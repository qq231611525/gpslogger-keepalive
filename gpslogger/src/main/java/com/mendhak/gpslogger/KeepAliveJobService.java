package com.mendhak.gpslogger;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.app.ActivityManager;

import androidx.core.content.ContextCompat;

import com.mendhak.gpslogger.common.IntentConstants;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

/**
 * JobScheduler 兜底保活：系统级定时任务，周期性检查 GPS 记录服务，
 * 即使双进程都被杀，JobScheduler 也会被系统重新调度。
 */
public class KeepAliveJobService extends JobService {

    private static final Logger LOG = Logs.of(KeepAliveJobService.class);
    public static final int JOB_ID = 0x1001;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        ComponentName component = new ComponentName(context, KeepAliveJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, component)
                .setPeriodic(15 * 60 * 1000) // 15分钟（系统最小间隔）
                .setPersisted(true)          // 重启后保留
                .build();

        int result = scheduler.schedule(jobInfo);
        LOG.debug("KeepAlive job scheduled, result=" + result);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LOG.debug("KeepAlive job fired, checking GpsLoggingService");
        if (!isServiceRunning(GpsLoggingService.class)) {
            int count = RestartCounter.incrementAndGet(this);
            LOG.warn("GpsLoggingService dead, restarting from JobScheduler (restart #" + count + ")");
            KeepAliveNotifier.notifyRestartAsync(this, count);
            // 注意：不要带 IMMEDIATE_START，否则会触发开始/停止记录的切换
            Intent serviceIntent = new Intent(this, GpsLoggingService.class);
            try {
                ContextCompat.startForegroundService(this, serviceIntent);
            } catch (Exception e) {
                LOG.error("KeepAlive restart failed", e);
            }
        }
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // 需要重调度
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
