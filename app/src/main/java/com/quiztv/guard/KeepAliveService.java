package com.quiztv.guard;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional "revival hook". Accessibility services are re-bound by the system after the
 * process is killed, so whenever this service connects or receives an event we make sure
 * {@link GuardService} is running. Enabled via adb (see scripts/install.sh, KEEPALIVE=1);
 * it reads nothing from the screen.
 */
public class KeepAliveService extends AccessibilityService {
    private static final String TAG = "KeepAliveService";
    private long lastCheck;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "connected");
        ensureGuard();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (now - lastCheck > 30_000) {
            lastCheck = now;
            ensureGuard();
        }
    }

    @Override
    public void onInterrupt() {
    }

    private void ensureGuard() {
        if (Prefs.enabled(this) && !GuardService.isRunning()) {
            Log.d(TAG, "guard not running, restarting");
            GuardService.start(this);
        }
    }
}
