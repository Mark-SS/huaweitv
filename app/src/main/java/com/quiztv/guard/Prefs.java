package com.quiztv.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Prefs {
    private static final String NAME = "quiztv";
    private static final String KEY_INTERVAL = "interval_minutes";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PIN = "parent_pin";
    private static final String KEY_PAUSE = "pause_playback";
    private static final String KEY_STAT_DAY = "stat_day";
    private static final String KEY_STAT_TOTAL = "stat_total";
    private static final String KEY_STAT_CORRECT = "stat_correct";

    static final int MIN_INTERVAL = 1;
    static final int MAX_INTERVAL = 120;
    static final int DEFAULT_INTERVAL = 10;
    static final String DEFAULT_PIN = "1234";

    private Prefs() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static int intervalMinutes(Context context) {
        return sp(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL);
    }

    static void setIntervalMinutes(Context context, int minutes) {
        int clamped = Math.max(MIN_INTERVAL, Math.min(MAX_INTERVAL, minutes));
        sp(context).edit().putInt(KEY_INTERVAL, clamped).apply();
    }

    static boolean enabled(Context context) {
        return sp(context).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        sp(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    static String pin(Context context) {
        return sp(context).getString(KEY_PIN, DEFAULT_PIN);
    }

    static void setPin(Context context, String pin) {
        sp(context).edit().putString(KEY_PIN, pin).apply();
    }

    static boolean pausePlayback(Context context) {
        return sp(context).getBoolean(KEY_PAUSE, true);
    }

    static void setPausePlayback(Context context, boolean pause) {
        sp(context).edit().putBoolean(KEY_PAUSE, pause).apply();
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static void recordAnswer(Context context, boolean correct) {
        SharedPreferences prefs = sp(context);
        String day = today();
        int total = 0;
        int right = 0;
        if (day.equals(prefs.getString(KEY_STAT_DAY, ""))) {
            total = prefs.getInt(KEY_STAT_TOTAL, 0);
            right = prefs.getInt(KEY_STAT_CORRECT, 0);
        }
        prefs.edit()
                .putString(KEY_STAT_DAY, day)
                .putInt(KEY_STAT_TOTAL, total + 1)
                .putInt(KEY_STAT_CORRECT, right + (correct ? 1 : 0))
                .apply();
    }

    /** Returns {total, correct} for today. */
    static int[] todayStats(Context context) {
        SharedPreferences prefs = sp(context);
        if (!today().equals(prefs.getString(KEY_STAT_DAY, ""))) {
            return new int[]{0, 0};
        }
        return new int[]{prefs.getInt(KEY_STAT_TOTAL, 0), prefs.getInt(KEY_STAT_CORRECT, 0)};
    }
}
