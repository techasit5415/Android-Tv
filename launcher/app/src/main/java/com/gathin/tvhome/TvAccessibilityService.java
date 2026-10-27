package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * System-wide hardware key interception service for Android TV.
 * Remaps dedicated hardware shortcut / TOT IPTV buttons to launch AIS PLAY.
 */
public class TvAccessibilityService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (isAppShortcutOrMediaKey(code)) {
                launchAisPlay();
                return true;
            }
        }
        return super.onKeyEvent(event);
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
}
