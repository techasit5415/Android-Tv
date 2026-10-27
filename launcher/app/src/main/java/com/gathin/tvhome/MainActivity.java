package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
        private TextView btnInputs;
    private TextView btnNowPlaying;
    private String currentPlayingTrack = null;
    private TextView tvRam;
    private final ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    private ActivityManager activityManager;
    private ImageView wallpaper;
    private int currentWallpaperRes = 0;
    private boolean isDockPeeking = false;
    private ValueAnimator dockPeekAnimator;
    private float peekOffset;
    private String peekDepthPref = "ultra";
    private boolean isTopBarShowText = true;
    private View settingsOverlay;
    private TextView btnTopStyleText, btnTopStyleIcon;
    private TextView btnPeekUltra, btnPeekBalanced, btnPeekNone;
    private TextView btnWallAuto, btnWallDay, btnWallNight;
    private boolean isAudioOnly = false;
    private View audioOnlyOverlay;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
                    updateRamUsage();
            updateWallpaper();
        updateRamUsage();
            updateRamUsage();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        peekDepthPref = prefs.getString("dock_peek_depth", "ultra");
        updatePeekOffset();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
                btnInputs = findViewById(R.id.btnInputs);
        if (btnInputs != null) {
            btnInputs.setOnClickListener(v -> showInputs());
        }
        wallpaper = findViewById(R.id.wallpaper);
        updateWallpaper();
        updateRamUsage();

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: 2-row dock sits at the bottom, giving maximum space for the wallpaper above.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int want = Math.max(0, h - (int) ((225 + 24) * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: smooth, stable, non-bouncy springs on highlight position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(360f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        }

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettings());
        }
        View dateView = findViewById(R.id.date);
        if (dateView != null) {
            dateView.setOnClickListener(v -> showCalendar());
        }
        tvRam = findViewById(R.id.tvRam);

        updateWeatherAsync();
        updateUpcomingEventAsync();

        android.content.IntentFilter pkgFilter = new android.content.IntentFilter();
        pkgFilter.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
        pkgFilter.addDataScheme("package");
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, android.content.Intent intent) {
                if (adapter != null) {
                    adapter.reload();
                }
            }
        }, pkgFilter);

        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;

            // Direct 1-click Navigation between top bar pills
            if (v == btnSettings) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnInputs != null) btnInputs.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            } else if (v == btnInputs) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnSettings != null) btnSettings.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (btnNowPlaying != null && btnNowPlaying.getVisibility() == View.VISIBLE) {
                        btnNowPlaying.requestFocus();
                    } else if (dateView != null) {
                        dateView.requestFocus();
                    }
                    return true;
                }
            } else if (v == btnNowPlaying) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnInputs != null) btnInputs.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (dateView != null) dateView.requestFocus();
                    return true;
                }
            } else if (v == dateView) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (btnNowPlaying != null && btnNowPlaying.getVisibility() == View.VISIBLE) {
                        btnNowPlaying.requestFocus();
                    } else if (btnInputs != null) {
                        btnInputs.requestFocus();
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setDockPeek(false, true);
                int target = (lastFocused >= 0 && lastFocused < COLUMNS) ? lastFocused : 0;
                focusTile(target);
                return true;
            }
            return false;
        };
        if (btnNowPlaying != null) btnNowPlaying.setOnKeyListener(pillKeys);
        if (btnInputs != null) btnInputs.setOnKeyListener(pillKeys);
        if (btnSettings != null) btnSettings.setOnKeyListener(pillKeys);
        if (dateView != null) dateView.setOnKeyListener(pillKeys);

        // Hide the glass/hero and peek the dock when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {
                hideGlass();
                hideHero();
                setDockPeek(true, true);
            } else {
                setDockPeek(false, true);
            }
        });
    }

    @Override
    public void onInputsTileClicked() {
        showInputs();
    }

    private boolean isInitialLaunch = true;

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        MediaNotificationListener.setCallback(this::onMediaUpdated);
        MediaNotificationListener.requestUpdate();
        ambient.setAlpha(0f);

        if (adapter != null) {
            adapter.reload();
        }

        updateGreeting();
                updateRamUsage();
        updateWallpaper();
        updateRamUsage();

        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Only move focus to top bar and set peek mode on initial cold launch
        if (isInitialLaunch) {
            isInitialLaunch = false;
            grid.post(() -> {
                View btn = findViewById(R.id.btnInputs);
                if (btn != null) btn.requestFocus();
                setDockPeek(true, false);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isAudioOnly) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int code = event.getKeyCode();
                if (code != KeyEvent.KEYCODE_VOLUME_UP &&
                    code != KeyEvent.KEYCODE_VOLUME_DOWN &&
                    code != KeyEvent.KEYCODE_VOLUME_MUTE) {
                    exitAudioOnlyMode();
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (isAppShortcutOrMediaKey(code)) {
                launchAisPlay();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isAppShortcutOrMediaKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_DVR:
            case KeyEvent.KEYCODE_WINDOW:
            case KeyEvent.KEYCODE_BUTTON_1:
            case KeyEvent.KEYCODE_BUTTON_2:
            case KeyEvent.KEYCODE_BUTTON_3:
            case KeyEvent.KEYCODE_BUTTON_4:
            case KeyEvent.KEYCODE_BUTTON_5:
            case KeyEvent.KEYCODE_BUTTON_6:
            case KeyEvent.KEYCODE_BUTTON_7:
            case KeyEvent.KEYCODE_BUTTON_8:
            case KeyEvent.KEYCODE_BUTTON_9:
            case KeyEvent.KEYCODE_BUTTON_10:
            case KeyEvent.KEYCODE_BUTTON_11:
            case KeyEvent.KEYCODE_BUTTON_12:
            case KeyEvent.KEYCODE_BUTTON_13:
            case KeyEvent.KEYCODE_BUTTON_14:
            case KeyEvent.KEYCODE_BUTTON_15:
            case KeyEvent.KEYCODE_BUTTON_16:
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_PROG_BLUE:
            case KeyEvent.KEYCODE_LAST_CHANNEL:
            case KeyEvent.KEYCODE_TV_DATA_SERVICE:
            case KeyEvent.KEYCODE_TV_RADIO_SERVICE:
            case KeyEvent.KEYCODE_TV_TELETEXT:
            case KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU:
            case 284:
            case 285:
            case 286:
            case 287:
            case 393:
            case 601:
            case 644:
            case 645:
            case 647:
            case 756:
            case 757:
            case 758:
            case 759:
            case 761:
                return true;
            default:
                return false;
        }
    }

    private void launchAisPlay() {
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.ais.mimo.aisplay.tv");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.ais.mimo.aisplay.tv");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (settingsOverlay != null && settingsOverlay.getVisibility() == View.VISIBLE) {
            hideSettings();
            return;
        }
        if (calendarOverlay != null && calendarOverlay.getVisibility() == View.VISIBLE) {
            hideCalendar();
            return;
        }
        View focused = getCurrentFocus();
        if (focused != null && focused.getId() == R.id.card) {
            if (lastFocused >= COLUMNS * 2) {
                // Return from apps grid to the dock
                focusTile(0);
            } else {
                // Return from dock to top bar (peek mode, full photo view)
                View btn = findViewById(R.id.btnInputs);
                if (btn != null) btn.requestFocus();
            }
        }
    }

    // ---- Calendar Overlay ------------------------------------------------------------------------

    private View calendarOverlay;
    private TextView tvCalMonthYear, tvCalSubtitle;
    private TableLayout calGridTable;
    private int calYear = 2026, calMonth = Calendar.OCTOBER;

    private static final class CalEvent {
        final String badge;
        final String title;
        final int color;
        CalEvent(String badge, String title, int color) {
            this.badge = badge;
            this.title = title;
            this.color = color;
        }
    }
    private static final Map<Integer, CalEvent> OCT_EVENTS = new HashMap<>();

    private void showCalendar() {
        if (calendarOverlay == null) {
            calendarOverlay = findViewById(R.id.calendarOverlay);
            tvCalMonthYear = findViewById(R.id.tvCalMonthYear);
            tvCalSubtitle = findViewById(R.id.tvCalSubtitle);
            calGridTable = findViewById(R.id.calGridTable);
            findViewById(R.id.btnCalClose).setOnClickListener(v -> hideCalendar());
            findViewById(R.id.btnCalPrev).setOnClickListener(v -> {
                calMonth--;
                if (calMonth < 0) { calMonth = 11; calYear--; }
                renderCalendar();
                v.requestFocus();
            });
            findViewById(R.id.btnCalNext).setOnClickListener(v -> {
                calMonth++;
                if (calMonth > 11) { calMonth = 0; calYear++; }
                renderCalendar();
                v.requestFocus();
            });
            findViewById(R.id.btnCalToday).setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                calYear = c.get(Calendar.YEAR);
                calMonth = c.get(Calendar.MONTH);
                renderCalendar();
                v.requestFocus();
            });
        }
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        Calendar c = Calendar.getInstance();
        calYear = c.get(Calendar.YEAR);
        calMonth = c.get(Calendar.MONTH);
        renderCalendar();
        calendarOverlay.setAlpha(0f);
        calendarOverlay.setVisibility(View.VISIBLE);
        calendarOverlay.animate().alpha(1f).setDuration(220).start();
    }

    private void hideCalendar() {
        if (calendarOverlay == null || calendarOverlay.getVisibility() != View.VISIBLE) return;
        calendarOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            calendarOverlay.setVisibility(View.GONE);
            grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            View btn = findViewById(R.id.btnInputs);
            if (btn != null) btn.requestFocus();
        }).start();
    }

    private void renderCalendar() {
        Calendar cal = Calendar.getInstance();
        int todayYear = cal.get(Calendar.YEAR);
        int todayMonth = cal.get(Calendar.MONTH);
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);

        cal.set(Calendar.YEAR, calYear);
        cal.set(Calendar.MONTH, calMonth);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvCalMonthYear.setText(sdf.format(cal.getTime()));
        tvCalSubtitle.setText(todayDay + " " + new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime()).toUpperCase(Locale.getDefault()) + " — No scheduled events");

        calGridTable.removeAllViews();

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sunday
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar prevCal = (Calendar) cal.clone();
        prevCal.add(Calendar.MONTH, -1);
        int daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int dayCounter = 1;
        int nextMonthDayCounter = 1;
        View viewToFocus = null;

        for (int row = 0; row < 5; row++) {
            TableRow tr = new TableRow(this);
            TableLayout.LayoutParams trLp = new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            tr.setLayoutParams(trLp);

            for (int col = 0; col < 7; col++) {
                View cell = getLayoutInflater().inflate(R.layout.item_cal_day, tr, false);
                TextView tvDayNum = cell.findViewById(R.id.tvDayNum);
                View dotToday = cell.findViewById(R.id.dotToday);
                View eventBadge = cell.findViewById(R.id.eventBadge);
                View eventDot = cell.findViewById(R.id.eventDot);
                TextView eventText = cell.findViewById(R.id.eventText);

                TableRow.LayoutParams cellLp = new TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f);
                cell.setLayoutParams(cellLp);

                int cellIndex = row * 7 + col;
                if (cellIndex < firstDayOfWeek) {
                    int pDay = daysInPrevMonth - (firstDayOfWeek - 1 - cellIndex);
                    tvDayNum.setText(String.valueOf(pDay));
                    tvDayNum.setTextColor(0x38FFFFFF);
                    cell.setAlpha(0.35f);
                    cell.setFocusable(false);
                    cell.setClickable(false);
                } else if (dayCounter <= daysInMonth) {
                    final int d = dayCounter;
                    tvDayNum.setText(String.valueOf(d));
                    cell.setFocusable(true);
                    cell.setClickable(true);
                    if (row == 0) {
                        cell.setNextFocusUpId(R.id.btnCalToday);
                    }
                    boolean isToday = (calYear == todayYear && calMonth == todayMonth && d == todayDay);
                    if (isToday) {
                        dotToday.setVisibility(View.VISIBLE);
                        dotToday.setBackgroundColor(0xFF38BDF8);
                        tvDayNum.setTextColor(0xFF38BDF8);
                        viewToFocus = cell;
                    }

                    if (calYear == 2026 && calMonth == Calendar.OCTOBER && OCT_EVENTS.containsKey(d)) {
                        CalEvent ev = OCT_EVENTS.get(d);
                        eventBadge.setVisibility(View.VISIBLE);
                        eventDot.setBackgroundColor(ev.color);
                        eventText.setText(ev.badge);
                        eventText.setTextColor(ev.color);
                    }

                    cell.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) {
                            if (calYear == 2026 && calMonth == Calendar.OCTOBER && OCT_EVENTS.containsKey(d)) {
                                tvCalSubtitle.setText(OCT_EVENTS.get(d).title);
                                tvCalSubtitle.setTextColor(OCT_EVENTS.get(d).color);
                            } else {
                                tvCalSubtitle.setText(d + " " + sdf.format(cal.getTime()).toUpperCase(Locale.getDefault()) + " — No scheduled events");
                                tvCalSubtitle.setTextColor(0xFF38BDF8);
                            }
                        }
                    });

                    dayCounter++;
                } else {
                    tvDayNum.setText(String.valueOf(nextMonthDayCounter++));
                    tvDayNum.setTextColor(0x38FFFFFF);
                    cell.setAlpha(0.35f);
                    cell.setFocusable(false);
                    cell.setClickable(false);
                }

                tr.addView(cell);
            }
            calGridTable.addView(tr);
        }

        if (viewToFocus != null) {
            final View f = viewToFocus;
            f.post(f::requestFocus);
        }
    }



    private void setDockPeek(boolean peek, boolean animate) {
        if (isDockPeeking == peek && dockPeekAnimator == null) return;
        isDockPeeking = peek;
        final float targetY = peek ? peekOffset : 0f;
        if (peek) {
            grid.scrollToPosition(0);
        }

        if (dockPeekAnimator != null) {
            dockPeekAnimator.cancel();
            dockPeekAnimator = null;
        }

        if (!animate) {
            grid.setTranslationY(targetY);
            return;
        }

        final float startY = grid.getTranslationY();
        if (Math.abs(startY - targetY) < 1f) return;

        dockPeekAnimator = ValueAnimator.ofFloat(startY, targetY);
        dockPeekAnimator.setDuration(peek ? 240 : 260);
        dockPeekAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        dockPeekAnimator.addUpdateListener(anim -> {
            grid.setTranslationY((float) anim.getAnimatedValue());
        });
        dockPeekAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dockPeekAnimator = null;
                if (!isDockPeeking) {
                    RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(lastFocused);
                    if (vh != null) {
                        View card = vh.itemView.findViewById(R.id.card);
                        if (card != null && card.isFocused()) {
                            onTileFocused(lastFocused, card);
                        }
                    }
                }
            }
        });
        dockPeekAnimator.start();
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        if ((settingsOverlay != null && settingsOverlay.getVisibility() == View.VISIBLE) ||
            (calendarOverlay != null && calendarOverlay.getVisibility() == View.VISIBLE)) {
            hideGlass();
            hideHero();
            return;
        }
        lastFocused = position;
        // Tile rect in root coordinates, normalized by subtracting current grid translation.
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f - grid.getTranslationY();

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        int row = position / COLUMNS;
        if (row == 0) {
            // Row 0 (Favorites Bar): smoothly park grid at top padding
            RecyclerView.ViewHolder vh0 = grid.findViewHolderForAdapterPosition(0);
            int dockTop = (vh0 != null) ? vh0.itemView.getTop() : item.getTop();
            int dy = dockTop - (int) (6 * density) - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
        } else {
            // Row 1+ (Stremio shelf and All Apps): smooth, natural scroll without violent jumps
            Rect want = new Rect(0, -(int) (12 * density), item.getWidth(), item.getHeight() + (int) (26 * density));
            grid.requestChildRectangleOnScreen(item, want, false);
        }
        showHero(position, 0f);
    }

    // ---- top shelf & ambient ---------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        int color = adapter.ambientColorAt(position);
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (color != ambientColor) {
            ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                setAmbient(color);
                ambient.animate().alpha(1f).setDuration(500).start();
            }).start();
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        if (!heroShown) return;
        heroShown = false;
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        if ((settingsOverlay != null && settingsOverlay.getVisibility() == View.VISIBLE) ||
            (calendarOverlay != null && calendarOverlay.getVisibility() == View.VISIBLE)) {
            return true;
        }
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateWallpaper() {
        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        String themePref = prefs.getString("wallpaper_theme", "auto");
        boolean isDay;
        if ("day".equals(themePref)) {
            isDay = true;
        } else if ("night".equals(themePref)) {
            isDay = false;
        } else {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);
            int timeInMinutes = hour * 60 + minute;
            // Day cycle: 07:00 (420 min) to 19:30 (1170 min)
            isDay = (timeInMinutes >= 420 && timeInMinutes < 1170);
        }
        int targetRes = isDay ? R.drawable.wallpaper_day : R.drawable.wallpaper_night;

        if (wallpaper == null) return;

        if (currentWallpaperRes == 0) {
            currentWallpaperRes = targetRes;
            wallpaper.setImageResource(targetRes);
            wallpaper.setAlpha(1f);
        } else if (currentWallpaperRes != targetRes) {
            currentWallpaperRes = targetRes;
            wallpaper.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                wallpaper.setImageResource(targetRes);
                wallpaper.animate().alpha(1f).setDuration(600).start();
            }).start();
        }
    }

    private void updateWifiLabel() {}

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (260 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                final String id = info.getId();
                String label;
                if (id.contains("HW1413744128")) {
                    label = "HDMI 1 (ARC)";
                } else if (id.contains("HW1413744384")) {
                    label = "HDMI 2";
                } else if (id.contains("HW1413744640")) {
                    label = "HDMI 3";
                } else if (id.contains("HW1413743104")) {
                    label = "AV";
                } else {
                    CharSequence l = info.loadLabel(this);
                    label = l == null ? id : l.toString();
                    if (info.getType() == TvInputInfo.TYPE_HDMI && !label.toUpperCase().contains("HDMI")) {
                        label = "HDMI " + label;
                    }
                }

                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")
                                    .setComponent(new ComponentName("com.tcl.tv", "com.tcl.tv.TVActivity"));
                            open(intent);
                        });
            }
        }
        
        // Add option to open full TV Source Menu
        if (rows.getChildCount() > 0) addSeparator(rows);
        addInputRow(rows, popup, R.drawable.ic_settings, "All Sources", -1, () -> {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.tcl.tv", "com.tcl.sourcemenu.sourcemanager.MainActivity"));
            open(intent);
        });

        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        if (btnInputs != null) {
            popup.showAsDropDown(btnInputs, (int) (-40 * density), (int) (8 * density));
        } else {
            popup.showAtLocation(root, Gravity.CENTER, 0, 0);
        }
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- Settings & Top Bar Preferences ----------------------------------------------------------

    private void updatePeekOffset() {
        if ("none".equals(peekDepthPref)) {
            peekOffset = 0f;
        } else if ("balanced".equals(peekDepthPref)) {
            peekOffset = 140f * density;
        } else {
            peekOffset = 220f * density; // default: ultra low peek (wallpaper takes ~95% of screen)
        }
    }

    private void applyTopBarStyle(boolean showText) {
        isTopBarShowText = showText;
        if (tvRam == null) tvRam = findViewById(R.id.tvRam);
        if (btnNowPlaying == null) btnNowPlaying = findViewById(R.id.btnNowPlaying);
        if (btnInputs == null) btnInputs = findViewById(R.id.btnInputs);
        updateRamUsage();
    }

    private void showSettings() {
        if (settingsOverlay == null) {
            settingsOverlay = findViewById(R.id.settingsOverlay);
            findViewById(R.id.btnSettingsClose).setOnClickListener(v -> hideSettings());

            btnPeekUltra = findViewById(R.id.btnPeekUltra);
            btnPeekBalanced = findViewById(R.id.btnPeekBalanced);
            btnPeekNone = findViewById(R.id.btnPeekNone);
            btnWallAuto = findViewById(R.id.btnWallAuto);
            btnWallDay = findViewById(R.id.btnWallDay);
            btnWallNight = findViewById(R.id.btnWallNight);

            if (btnPeekUltra != null) btnPeekUltra.setOnClickListener(v -> setPeekDepth("ultra"));
            if (btnPeekBalanced != null) btnPeekBalanced.setOnClickListener(v -> setPeekDepth("balanced"));
            if (btnPeekNone != null) btnPeekNone.setOnClickListener(v -> setPeekDepth("none"));

            if (btnWallAuto != null) btnWallAuto.setOnClickListener(v -> setWallpaperTheme("auto"));
            if (btnWallDay != null) btnWallDay.setOnClickListener(v -> setWallpaperTheme("day"));
            if (btnWallNight != null) btnWallNight.setOnClickListener(v -> setWallpaperTheme("night"));

            View btnAudioOnly = findViewById(R.id.btnAudioOnly);
            if (btnAudioOnly != null) {
                btnAudioOnly.setOnClickListener(v -> {
                    hideSettings();
                    enableAudioOnlyMode();
                });
            }

            View sysSet = findViewById(R.id.btnSysSettings);
            if (sysSet != null) sysSet.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
            View sysWifi = findViewById(R.id.btnSysWifi);
            if (sysWifi != null) sysWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            View sysApps = findViewById(R.id.btnSysApps);
            if (sysApps != null) sysApps.setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)));
            settingsOverlay.setOnClickListener(v -> hideSettings());
        }

        updateSettingsUI();
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        hideGlass();
        hideHero();
        settingsOverlay.setVisibility(View.VISIBLE);
        settingsOverlay.setAlpha(0f);
        settingsOverlay.animate().alpha(1f).setDuration(200).start();
        settingsOverlay.post(() -> {
            View firstBtn = findViewById(R.id.btnWallAuto);
            if (firstBtn != null) {
                firstBtn.setFocusable(true);
                firstBtn.setFocusableInTouchMode(true);
                firstBtn.requestFocus();
            } else {
                View closeBtn = findViewById(R.id.btnSettingsClose);
                if (closeBtn != null) closeBtn.requestFocus();
            }
        });
    }

    private void hideSettings() {
        if (settingsOverlay == null || settingsOverlay.getVisibility() != View.VISIBLE) return;
        settingsOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            settingsOverlay.setVisibility(View.GONE);
            grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            View btn = findViewById(R.id.btnSettings);
            if (btn != null) btn.requestFocus();
        }).start();
    }

    private void updateSettingsUI() {
        if (btnPeekUltra != null) btnPeekUltra.setSelected("ultra".equals(peekDepthPref));
        if (btnPeekBalanced != null) btnPeekBalanced.setSelected("balanced".equals(peekDepthPref));
        if (btnPeekNone != null) btnPeekNone.setSelected("none".equals(peekDepthPref));

        String wTheme = getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getString("wallpaper_theme", "auto");
        btnWallAuto.setSelected("auto".equals(wTheme));
        btnWallDay.setSelected("day".equals(wTheme));
        btnWallNight.setSelected("night".equals(wTheme));
    }

    private void setTopBarStyle(boolean showText) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putBoolean("top_bar_show_text", showText).apply();
        applyTopBarStyle(showText);
        updateSettingsUI();
    }

    private void setPeekDepth(String depth) {
        peekDepthPref = depth;
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("dock_peek_depth", depth).apply();
        updatePeekOffset();
        if (isDockPeeking) {
            setDockPeek(true, true);
        }
        updateSettingsUI();
    }

    private void setWallpaperTheme(String theme) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("wallpaper_theme", theme).apply();
        updateWallpaper();
        updateRamUsage();
        updateSettingsUI();
    }

    private void onMediaUpdated(String track, String artist, boolean isPlaying, String packageName) {
        if (isFinishing() || isDestroyed()) return;
        runOnUiThread(() -> {
            if (btnNowPlaying == null) btnNowPlaying = findViewById(R.id.btnNowPlaying);
            if (btnNowPlaying == null) return;

            if (isPlaying && track != null && !track.trim().isEmpty()) {
                String label = track;
                if (artist != null && !artist.trim().isEmpty()) {
                    label = track + " • " + artist;
                }
                currentPlayingTrack = label;
                btnNowPlaying.setText(isTopBarShowText ? label : "");
                btnNowPlaying.setVisibility(View.VISIBLE);
                btnNowPlaying.setSelected(true);
            } else {
                btnNowPlaying.setVisibility(View.GONE);
                currentPlayingTrack = null;
            }
        });
    }

    private void launchPlayingApp() {
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.spotify.tv.android");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.spotify.tv.android");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MediaNotificationListener.setCallback(null);
    }


    private void cleanRam() {
        if (activityManager == null) {
            activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        }
        long beforeAvail = 0;
        if (activityManager != null) {
            activityManager.getMemoryInfo(memInfo);
            beforeAvail = memInfo.availMem;
        }
        System.gc();
        Runtime.getRuntime().gc();
        updateRamUsage();
        long afterAvail = 0;
        if (activityManager != null) {
            activityManager.getMemoryInfo(memInfo);
            afterAvail = memInfo.availMem;
        }
        long freedMb = Math.max(0, (afterAvail - beforeAvail) / (1024 * 1024));
        if (freedMb > 0) {
            Toast.makeText(this, "เคลียร์ RAM สำเร็จ (คืนพื้นที่ " + freedMb + " MB)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "RAM ปัจจุบัน: " + (tvRam != null ? tvRam.getText() : ""), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRamUsage() {
        if (activityManager == null) {
            activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        }
        if (activityManager != null) {
            activityManager.getMemoryInfo(memInfo);
            long totalMem = memInfo.totalMem;
            long availMem = memInfo.availMem;
            long usedMem = totalMem - availMem;
            int percent = (int) Math.round((usedMem * 100.0) / totalMem);

            double usedGb = usedMem / (1024.0 * 1024.0 * 1024.0);
            double totalGb = totalMem / (1024.0 * 1024.0 * 1024.0);

            String text;
            if (isTopBarShowText) {
                text = String.format(Locale.US, "%.1f / %.1f GB (%d%%)", usedGb, totalGb, percent);
            } else {
                text = String.format(Locale.US, "%d%%", percent);
            }

            if (tvRam == null) tvRam = findViewById(R.id.tvRam);
            if (tvRam != null) {
                tvRam.setText(text);
            }
        }
    }

    private void enableAudioOnlyMode() {
        isAudioOnly = true;
        if (audioOnlyOverlay == null) {
            audioOnlyOverlay = new View(this);
            audioOnlyOverlay.setBackgroundColor(0xFF000000);
            audioOnlyOverlay.setFocusable(true);
            audioOnlyOverlay.setClickable(true);
            root.addView(audioOnlyOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        audioOnlyOverlay.setVisibility(View.VISIBLE);
        audioOnlyOverlay.bringToFront();
        audioOnlyOverlay.requestFocus();
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.0f;
        getWindow().setAttributes(lp);
        Toast.makeText(this, "โหมดปิดจอภาพ (กดปุ่มใดๆ เพื่อเปิดจอ)", Toast.LENGTH_SHORT).show();
    }

    private void exitAudioOnlyMode() {
        if (!isAudioOnly) return;
        isAudioOnly = false;
        if (audioOnlyOverlay != null) {
            audioOnlyOverlay.setVisibility(View.GONE);
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = -1.0f;
        getWindow().setAttributes(lp);
    }



    private void updateWeatherAsync() {
        new Thread(() -> {
            try {
                String city = "กรุงเทพฯ";
                double lat = 13.7563;
                double lon = 100.5018;
                try {
                    java.net.URL ipUrl = new java.net.URL("http://ip-api.com/json");
                    java.net.HttpURLConnection ipConn = (java.net.HttpURLConnection) ipUrl.openConnection();
                    ipConn.setConnectTimeout(4000);
                    ipConn.setReadTimeout(4000);
                    if (ipConn.getResponseCode() == 200) {
                        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(ipConn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = r.readLine()) != null) sb.append(l);
                        r.close();
                        org.json.JSONObject ipJson = new org.json.JSONObject(sb.toString());
                        lat = ipJson.optDouble("lat", lat);
                        lon = ipJson.optDouble("lon", lon);
                        String rawCity = ipJson.optString("city", "Bangkok");
                        if ("Bangkok".equalsIgnoreCase(rawCity)) {
                            city = "กรุงเทพฯ";
                        } else {
                            city = rawCity;
                        }
                    }
                } catch (Exception ignored) {}

                String urlStr = String.format(java.util.Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m",
                        lat, lon);
                java.net.URL url = new java.net.URL(urlStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject obj = new org.json.JSONObject(sb.toString());
                    org.json.JSONObject current = obj.getJSONObject("current");
                    double temp = current.getDouble("temperature_2m");
                    final String weatherStr = Math.round(temp) + "°C " + city;
                    runOnUiThread(() -> {
                        TextView tvWeather = findViewById(R.id.tvWeather);
                        if (tvWeather != null) tvWeather.setText(weatherStr);
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateUpcomingEventAsync() {
        new Thread(() -> {
            try {
                Calendar todayCal = Calendar.getInstance();
                todayCal.set(Calendar.HOUR_OF_DAY, 0);
                todayCal.set(Calendar.MINUTE, 0);
                todayCal.set(Calendar.SECOND, 0);
                long startOfDay = todayCal.getTimeInMillis();
                long endOfDay = startOfDay + 86400000L;

                android.net.Uri uri = android.provider.CalendarContract.Events.CONTENT_URI;
                android.database.Cursor cursor = getContentResolver().query(
                        uri,
                        new String[]{"title", "dtstart", "allDay"},
                        "dtstart >= ? AND deleted = 0",
                        new String[]{String.valueOf(startOfDay)},
                        "dtstart ASC");

                String eventDisplay = "ไม่มีนัดหมายวันนี้";
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        String title = cursor.getString(0);
                        long start = cursor.getLong(1);
                        boolean allDay = cursor.getInt(2) == 1;

                        if (start < endOfDay) {
                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(start);
                            String timeStr = allDay ? "ตลอดวัน" : String.format(java.util.Locale.getDefault(), "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
                            eventDisplay = "วันนี้: " + title + " (" + timeStr + ")";
                        } else {
                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(start);
                            String[] months = {"ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."};
                            int m = c.get(Calendar.MONTH);
                            String mName = (m >= 0 && m < months.length) ? months[m] : "";
                            eventDisplay = title + " (" + c.get(Calendar.DAY_OF_MONTH) + " " + mName + ")";
                        }
                    }
                    cursor.close();
                }

                final String finalEventStr = eventDisplay;
                runOnUiThread(() -> {
                    TextView tvEvent = findViewById(R.id.tvUpcomingEvent);
                    if (tvEvent != null) tvEvent.setText(finalEventStr);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView tvEvent = findViewById(R.id.tvUpcomingEvent);
                    if (tvEvent != null) tvEvent.setText("ไม่มีนัดหมายวันนี้");
                });
            }
        }).start();
    }
}