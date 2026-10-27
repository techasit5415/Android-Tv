package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

public class MediaNotificationListener extends NotificationListenerService {

    private static final String TAG = "MediaListener";

    public interface Callback {
        void onMediaStateChanged(String track, String artist, boolean isPlaying, String packageName);
    }

    private static Callback sCallback;
    private static MediaNotificationListener sInstance;
    private static String sLastTrack;
    private static String sLastArtist;
    private static boolean sLastIsPlaying;
    private static String sLastPkg;

    private MediaSessionManager mediaSessionManager;
    private MediaController activeController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            Log.d(TAG, "onPlaybackStateChanged: " + (state != null ? state.getState() : "null"));
            mainHandler.post(() -> evaluateMediaState());
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            Log.d(TAG, "onMetadataChanged");
            mainHandler.post(() -> evaluateMediaState());
        }
    };

    private final BroadcastReceiver spotifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            Log.d(TAG, "Broadcast received: " + action);
            if ("com.spotify.music.playbackstatechanged".equals(action)
                    || "com.spotify.music.metadatachanged".equals(action)) {
                boolean playing = intent.getBooleanExtra("playing", false);
                String track = intent.getStringExtra("track");
                String artist = intent.getStringExtra("artist");
                Log.d(TAG, "Spotify broadcast: track=" + track + ", playing=" + playing);
                if (track != null) {
                    dispatchUpdate(track, artist, playing, "com.spotify.tv.android");
                } else {
                    evaluateMediaState();
                }
            }
        }
    };

    public static void setCallback(Callback callback) {
        Log.d(TAG, "setCallback: " + (callback != null));
        sCallback = callback;
        if (sCallback != null && sLastTrack != null) {
            sCallback.onMediaStateChanged(sLastTrack, sLastArtist, sLastIsPlaying, sLastPkg);
        }
        if (sInstance != null) {
            sInstance.evaluateMediaState();
        }
    }

    public static void requestUpdate() {
        Log.d(TAG, "requestUpdate called, instance=" + (sInstance != null));
        if (sInstance != null) {
            sInstance.checkSessionsDirect();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "MediaNotificationListener onCreate");
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.spotify.music.playbackstatechanged");
            filter.addAction("com.spotify.music.metadatachanged");
            registerReceiver(spotifyReceiver, filter);
        } catch (Exception e) {
            Log.w(TAG, "Failed to register spotify receiver", e);
        }
        initMediaSession();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaNotificationListener onDestroy");
        if (sInstance == this) sInstance = null;
        try {
            unregisterReceiver(spotifyReceiver);
        } catch (Exception ignored) {}
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {}
            activeController = null;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sInstance = this;
        Log.d(TAG, "MediaNotificationListener onListenerConnected");
        initMediaSession();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "MediaNotificationListener onListenerDisconnected");
        if (sInstance == this) sInstance = null;
    }

    public void checkSessionsDirect() {
        try {
            if (mediaSessionManager == null) {
                mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            }
            if (mediaSessionManager != null) {
                ComponentName cn = new ComponentName(this, MediaNotificationListener.class);
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                Log.d(TAG, "checkSessionsDirect found: " + (controllers != null ? controllers.size() : 0));
                onSessionsChanged(controllers);
            }
        } catch (Exception e) {
            Log.e(TAG, "checkSessionsDirect error", e);
        }
    }

    private void initMediaSession() {
        try {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                ComponentName cn = new ComponentName(this, MediaNotificationListener.class);
                mediaSessionManager.removeOnActiveSessionsChangedListener(this::onSessionsChanged);
                mediaSessionManager.addOnActiveSessionsChangedListener(this::onSessionsChanged, cn);
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                Log.d(TAG, "initMediaSession active sessions count: " + (controllers != null ? controllers.size() : 0));
                onSessionsChanged(controllers);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MediaSessionManager", e);
        }
    }

    private void onSessionsChanged(List<MediaController> controllers) {
        if (controllers == null) {
            Log.d(TAG, "onSessionsChanged: null list");
            evaluateMediaState();
            return;
        }
        Log.d(TAG, "onSessionsChanged size: " + controllers.size());
        mainHandler.post(() -> {
            MediaController chosen = null;
            // 1. Look for a playing Spotify controller
            for (MediaController mc : controllers) {
                if ("com.spotify.tv.android".equals(mc.getPackageName())) {
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                        chosen = mc;
                        break;
                    }
                }
            }
            // 2. Look for any playing controller
            if (chosen == null) {
                for (MediaController mc : controllers) {
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                        chosen = mc;
                        break;
                    }
                }
            }
            // 3. Fallback to Spotify even if paused
            if (chosen == null) {
                for (MediaController mc : controllers) {
                    if ("com.spotify.tv.android".equals(mc.getPackageName())) {
                        chosen = mc;
                        break;
                    }
                }
            }

            if (activeController != chosen) {
                if (activeController != null) {
                    try {
                        activeController.unregisterCallback(controllerCallback);
                    } catch (Exception ignored) {}
                }
                activeController = chosen;
                if (activeController != null) {
                    try {
                        activeController.registerCallback(controllerCallback, mainHandler);
                    } catch (Exception ignored) {}
                }
            }
            evaluateMediaState();
        });
    }

    public void evaluateMediaState() {
        if (activeController != null) {
            PlaybackState ps = activeController.getPlaybackState();
            MediaMetadata mm = activeController.getMetadata();
            boolean isPlaying = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
            String track = null;
            String artist = null;

            if (mm != null) {
                track = mm.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (track == null) track = mm.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                MediaDescription desc = mm.getDescription();
                if (track == null && desc != null && desc.getTitle() != null) {
                    track = desc.getTitle().toString();
                }

                artist = mm.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) artist = mm.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                if (artist == null) artist = mm.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                if (artist == null && desc != null && desc.getSubtitle() != null) {
                    artist = desc.getSubtitle().toString();
                }
            }

            Log.d(TAG, "evaluateMediaState: track=" + track + ", artist=" + artist + ", isPlaying=" + isPlaying + ", pkg=" + activeController.getPackageName());
            if (track != null) {
                dispatchUpdate(track, artist, isPlaying, activeController.getPackageName());
                return;
            }
        }

        // Check active notifications
        try {
            StatusBarNotification[] notifs = getActiveNotifications();
            if (notifs != null) {
                for (StatusBarNotification sbn : notifs) {
                    if ("com.spotify.tv.android".equals(sbn.getPackageName())) {
                        CharSequence title = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
                        CharSequence text = sbn.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
                        Log.d(TAG, "Found Spotify notification: " + title + " - " + text);
                        if (title != null) {
                            dispatchUpdate(title.toString(), text != null ? text.toString() : null, true, sbn.getPackageName());
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        Log.d(TAG, "evaluateMediaState: nothing active");
        dispatchUpdate(null, null, false, null);
    }

    private void dispatchUpdate(String track, String artist, boolean isPlaying, String pkg) {
        sLastTrack = track;
        sLastArtist = artist;
        sLastIsPlaying = isPlaying;
        sLastPkg = pkg;
        mainHandler.post(() -> {
            if (sCallback != null) {
                sCallback.onMediaStateChanged(track, artist, isPlaying, pkg);
            }
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && "com.spotify.tv.android".equals(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationPosted: Spotify");
            evaluateMediaState();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && "com.spotify.tv.android".equals(sbn.getPackageName())) {
            Log.d(TAG, "onNotificationRemoved: Spotify");
            evaluateMediaState();
        }
    }
}
