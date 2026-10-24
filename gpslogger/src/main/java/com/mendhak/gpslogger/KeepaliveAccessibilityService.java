package com.mendhak.gpslogger;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.content.ContextCompat;

import com.mendhak.gpslogger.common.slf4j.Logs;

import org.slf4j.Logger;

/**
 * 无障碍保活: 用户在系统设置里开启后, 系统把本服务长期绑定,
 * 进程优先级极高, 常规清理很难杀死; 每 5 分钟巡检一次, 主服务死了就拉回。
 * 本服务不读取、不处理任何屏幕内容(事件回调刻意留空, 配置也关了取窗)。
 * 拉起服务时一律用空 Intent, 不带任何开始/停止参数(144 的教训)。
 * 注意: 它的核心价值是“让系统不杀进程”, 巡检拉回是附加;
 * 安卓 12+ 后台启动前台服务的限制下, 拉回动作可能被系统挡下,
 * 此时由 JobScheduler/闹钟两条巡检通道补位。
 */
public class KeepaliveAccessibilityService extends AccessibilityService {

    private static final Logger LOG = Logs.of(KeepaliveAccessibilityService.class);
    private static final long PATROL_MS = 5 * 60 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable patrol = new Runnable() {
        @Override
        public void run() {
            if (!isServiceRunning(GpsLoggingService.class)) {
                LOG.warn("GpsLoggingService dead, restarting from accessibility patrol");
                try {
                    ContextCompat.startForegroundService(
                            KeepaliveAccessibilityService.this,
                            new Intent(KeepaliveAccessibilityService.this, GpsLoggingService.class));
                } catch (Exception e) {
                    LOG.error("KeepAlive accessibility restart failed", e);
                }
            }
            handler.postDelayed(this, PATROL_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler.post(patrol);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 刻意不处理任何事件: 保活不需要屏幕内容
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(patrol);
        super.onDestroy();
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
