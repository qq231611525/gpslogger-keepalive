package com.mendhak.gpslogger;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mendhak.gpslogger.common.IntentConstants;
import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

/**
 * 守护进程服务：运行在独立的 ":guard" 进程中，
 * 定时检查 GpsLoggingService 是否存活，死了就拉起来。
 * 与主服务互相守护，对抗国产 ROM 杀后台。
 */
public class GuardService extends Service {

    private static final Logger LOG = Logs.of(GuardService.class);
    private static final long CHECK_INTERVAL_MS = 60_000; // 每分钟检查一次

    private Handler handler;
    private Runnable checkTask;

    @Override
    public void onCreate() {
        super.onCreate();
        LOG.debug("GuardService created in guard process");
        handler = new Handler(Looper.getMainLooper());
        checkTask = new Runnable() {
            @Override
            public void run() {
                if (!isServiceRunning(GpsLoggingService.class)) {
                    int count = RestartCounter.incrementAndGet(GuardService.this);
                    LOG.warn("GpsLoggingService not running, restarting from guard process (restart #" + count + ")");
                    Intent serviceIntent = new Intent(GuardService.this, GpsLoggingService.class);
                    serviceIntent.putExtra(IntentConstants.IMMEDIATE_START, true);
                    try {
                        ContextCompat.startForegroundService(GuardService.this, serviceIntent);
                    } catch (Exception e) {
                        LOG.error("Failed to restart GpsLoggingService", e);
                    }
                }
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        handler.postDelayed(checkTask, CHECK_INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkTask != null) {
            handler.removeCallbacks(checkTask);
        }
        // 自己被杀时，尝试拉起主服务（主服务也会反过来拉守护进程）
        Intent serviceIntent = new Intent(this, GpsLoggingService.class);
        serviceIntent.putExtra(IntentConstants.IMMEDIATE_START, true);
        try {
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            LOG.error("GuardService onDestroy restart failed", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
