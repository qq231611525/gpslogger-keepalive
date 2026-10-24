package com.mendhak.gpslogger;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

/**
 * 闹钟巡检复活, 与 KeepAliveJobService(15 分钟)双保险、错开时间。
 * 注意与 senders.AlarmReceiver(定时上传用)同名不同包, 不要混淆。
 * 拉起服务时一律用空 Intent, 不带任何开始/停止参数(144 的教训:
 * 守护拉起不得碰记录开关, 只负责把死了的服务拉起来)。
 */
public class KeepAliveAlarmReceiver extends BroadcastReceiver {

    private static final Logger LOG = Logs.of(KeepAliveAlarmReceiver.class);
    private static final int REQUEST_CODE = 0x1002;
    private static final long INTERVAL_MS = 10 * 60 * 1000L;
    private static final long FIRST_DELAY_MS = 5 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isServiceRunning(context, GpsLoggingService.class)) {
            LOG.warn("GpsLoggingService dead, restarting from alarm patrol");
            try {
                ContextCompat.startForegroundService(
                        context, new Intent(context, GpsLoggingService.class));
            } catch (Exception e) {
                LOG.error("KeepAlive alarm restart failed", e);
            }
        }
        // 自链下一发(与 JobScheduler 的持久周期不同, 闹钟需自己续链)
        scheduleNext(context);
    }

    /** 首次布防: 5 分钟后首响, 之后每 10 分钟自链 */
    public static void schedule(Context context) {
        scheduleDelayed(context, FIRST_DELAY_MS);
    }

    /** 续链: 10 分钟后下一发 */
    public static void scheduleNext(Context context) {
        scheduleDelayed(context, INTERVAL_MS);
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pendingIntent(context));
        }
    }

    private static void scheduleDelayed(Context context, long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long trigger = System.currentTimeMillis() + delayMs;
        PendingIntent pi = pendingIntent(context);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                // 未获精确闹钟授权时退为非精确闹钟(仍可唤醒, 时间有抖动)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            }
        } catch (Exception e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } catch (Exception ignored) {
            }
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, KeepAliveAlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    private static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
