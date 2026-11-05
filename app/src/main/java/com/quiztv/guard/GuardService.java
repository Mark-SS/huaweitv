package com.quiztv.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Foreground service that keeps a countdown running while the screen is on and pops the
 * quiz overlay every N minutes.
 */
public class GuardService extends Service implements QuizOverlay.Listener {
    private static final String TAG = "GuardService";
    private static final String CHANNEL_ID = "quiztv_guard";
    private static final int NOTIFICATION_ID = 1001;

    static final String ACTION_START = "com.quiztv.guard.START";
    static final String ACTION_STOP = "com.quiztv.guard.STOP";
    static final String ACTION_TRIGGER_NOW = "com.quiztv.guard.TRIGGER_NOW";
    static final String ACTION_RESCHEDULE = "com.quiztv.guard.RESCHEDULE";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable triggerRunnable = this::triggerQuiz;
    private static volatile boolean running;

    private QuestionBank bank;
    private QuizOverlay overlay;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean screenOn = true;
    private boolean pausedMedia;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOn = false;
                handler.removeCallbacks(triggerRunnable);
                Log.d(TAG, "screen off -> timer paused");
            } else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                screenOn = true;
                schedule();
            }
        }
    };

    static void start(Context context) {
        Intent intent = new Intent(context, GuardService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void send(Context context, String action) {
        Intent intent = new Intent(context, GuardService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        bank = new QuestionBank(this);
        overlay = new QuizOverlay(this, bank, this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOn = pm == null || pm.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);

        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification(null));
        if (Prefs.mutedByUs(this)) {
            Log.w(TAG, "previous run left the TV muted, unmuting");
            setMuted(false);
        }
        Log.d(TAG, "service created, static questions: " + bank.staticCount());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (action == null) {
            action = ACTION_START;
        }
        switch (action) {
            case ACTION_STOP:
                overlay.hide();
                resumeMedia();
                handler.removeCallbacks(triggerRunnable);
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            case ACTION_TRIGGER_NOW:
                handler.removeCallbacks(triggerRunnable);
                triggerQuiz();
                break;
            case ACTION_RESCHEDULE:
            case ACTION_START:
            default:
                if (!overlay.isShowing()) {
                    schedule();
                }
                break;
        }
        return START_STICKY;
    }

    private void schedule() {
        handler.removeCallbacks(triggerRunnable);
        if (!Prefs.enabled(this)) {
            Log.d(TAG, "disabled, not scheduling");
            updateNotification(null);
            return;
        }
        if (!screenOn) {
            return;
        }
        long delay = Prefs.intervalMinutes(this) * 60_000L;
        handler.postDelayed(triggerRunnable, delay);
        updateNotification(null);
        Log.d(TAG, "next quiz in " + (delay / 1000) + "s");
    }

    private void triggerQuiz() {
        if (overlay.isShowing()) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "no overlay permission");
            updateNotification(getString(R.string.notif_no_permission));
            schedule();
            return;
        }
        if (Prefs.pausePlayback(this)) {
            pauseMedia();
        }
        overlay.show();
    }

    @Override
    public void onQuizFinished(boolean parentSkipped) {
        resumeMedia();
        schedule();
    }

    // -------------------------------------------------------------- media

    private void pauseMedia() {
        if (audioManager == null) {
            return;
        }
        try {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setOnAudioFocusChangeListener(focusChange -> { })
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
            pausedMedia = true;
        } catch (Exception e) {
            Log.w(TAG, "pauseMedia failed", e);
        }
        setMuted(true);
    }

    private void resumeMedia() {
        setMuted(false);
        if (!pausedMedia || audioManager == null) {
            return;
        }
        pausedMedia = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                    focusRequest = null;
                }
            } else {
                audioManager.abandonAudioFocus(null);
            }
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        } catch (Exception e) {
            Log.w(TAG, "resumeMedia failed", e);
        }
    }

    private void dispatchMediaKey(int keyCode) {
        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    /**
     * Fallback for sources that ignore media keys / audio focus (HDMI boxes, live TV, sloppy
     * players): mute the system media stream while the quiz is up, restore afterwards.
     */
    private void setMuted(boolean mute) {
        if (audioManager == null) {
            return;
        }
        if (mute && !Prefs.pausePlayback(this)) {
            return;
        }
        if (!mute && !Prefs.mutedByUs(this)) {
            return;
        }
        try {
            if (mute && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                return; // user already muted the TV themselves; leave it alone afterwards too
            }
            int direction = mute ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mute != audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.w(TAG, "stream mute state did not change");
            }
            Prefs.setMutedByUs(this, mute);
        } catch (Exception e) {
            Log.w(TAG, "setMuted(" + mute + ") failed", e);
        }
    }

    // -------------------------------------------------------- notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                        getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String override) {
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, piFlags);
        String text = override != null ? override
                : Prefs.enabled(this)
                ? getString(R.string.notif_text, Prefs.intervalMinutes(this))
                : "已暂停（家长已关闭答题）";
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String override) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(override));
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        overlay.hide();
        resumeMedia();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
