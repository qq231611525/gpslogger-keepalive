package com.mendhak.gpslogger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 保活重启计数器：记录被守护进程/Job 拉起的次数，
 * 显示在通知栏上，方便用户直观看到保活效果。
 */
public class RestartCounter {

    private static final String PREFS_NAME = "keepalive_stats";
    private static final String KEY_RESTART_COUNT = "restart_count";

    public static int getCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_RESTART_COUNT, 0);
    }

    public static int incrementAndGet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(KEY_RESTART_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_RESTART_COUNT, count).apply();
        return count;
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_RESTART_COUNT, 0).apply();
    }
}
