package com.quiztv.guard;

import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Second optional revival hook: notification listeners are also re-bound by the system
 * after a kill. It never reads notification content; it only restarts {@link GuardService}.
 */
public class RevivalListener extends NotificationListenerService {
    private static final String TAG = "RevivalListener";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "connected");
        if (Prefs.enabled(this) && !GuardService.isRunning()) {
            GuardService.start(this);
        }
    }
}
