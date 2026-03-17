package com.android.retaildemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.android.retaildemo.time.ConfigManager;
import com.android.retaildemo.time.TimeConfig;

import java.util.ArrayList;
import java.util.Calendar;

public class UserActivityMonitorService extends Service {

    private static final String TAG = "MonitorService";
    private static final long TIMEOUT_MS = 20 * 1000; // 20秒测试
    private static final String PREFS_NAME = "demo_prefs";
    private static final String KEY_LAST_TOUCH = "last_touch_time";
    private static final String CHANNEL_ID = "monitor_channel";
    private static final int NOTIFY_ID = 1001;
    private Handler handler;
    private SharedPreferences prefs;
    private long lastTouchTime;
    private BroadcastReceiver userActivityReceiver;
    private static final String DEMO_MODE_ENABLED = "sysui_demo_allowed";
    private ConfigManager configManager;
    private TimeConfig timeConfig;

    Runnable task = new Runnable() {
        @Override
        public void run() {
            lastTouchTime = prefs.getLong(KEY_LAST_TOUCH, System.currentTimeMillis());
            long idleTime = System.currentTimeMillis() - lastTouchTime;
            Log.d(TAG, "设备空闲时间: " + idleTime / 1000 + "秒");
            if (idleTime >= TIMEOUT_MS) {
                Log.d(TAG, "超时无任何触摸事件，发送启动演示应用的通知");
                sendRestartNotification();
            } else {
                // 继续监控
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UserActivityMonitorService服务创建");

        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 启动前台服务
        startForegroundService();

        configManager = new ConfigManager(this);

        userActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "监听屏幕熄灭事件，演示应用启动开始定时");
                    // 定时启动演示应用
                    updateTouchTime(context);
                    startMonitoring();
                }
                if (Intent.ACTION_SCREEN_ON.equals(action) ||
                        Intent.ACTION_USER_PRESENT.equals(action)) {
                    Log.d(TAG, "监听到解锁、屏幕亮起事件，取消定时启动演示任务");
                    // 屏幕亮起或用户解锁，更新触摸事件
                    handler.removeCallbacks(task);
                }
            }
        };
        // 注册屏幕亮起广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(userActivityReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service被重建");
        return START_STICKY; // 如果Service被杀死，尝试重建
    }

    /**
     * 启动前台服务
     */
    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "监控服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("DemoPlayer")
                    .setContentText("自启动监控运行中")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();

            startForeground(NOTIFY_ID, notification);
        }
    }

    /**
     * 开始监控
     */
    private void startMonitoring() {
        handler.post(task);
    }
    private void startDemoPlayerActivity() {
        try {
            Log.d(TAG, "尝试启动");
            Intent intent = new Intent(this, DemoPlayer.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 创建PendingIntent来启动Activity
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 发送PendingIntent
            pendingIntent.send();

            Log.d("IdleMonitor", "通过PendingIntent启动Activity");
        } catch (PendingIntent.CanceledException e) {
            Log.e("IdleMonitor", "启动失败", e);
        }
    }
    /**
     * 发送重启通知
     */
    private void sendRestartNotification() {
        Log.d(TAG, "sendRestartNotification()发送重启通知");

        // 直接启动DemoPlayer Activity
//        startDemoPlayerActivity();

        // 当前为演示模式
        int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
        if (demoModeEnabled == 1) {
            // 未启用时间管理 || 在时间范围内时
            Log.d(TAG, "演示模式状态：开启");
            if (!configManager.getConfig().isEnabled() || isInTimeRange()) {
                Log.d(TAG, "configManager.getConfig().isEnabled(): " + configManager.getConfig().isEnabled());
                // 点亮屏幕
                wakeUpAndUnlockScreen();
                Log.d(TAG, "时钟配置未开启或开启且处于时间范围内");
                startDemoPlayerActivity();
            } else {
                Log.d(TAG, "时钟配置开启，当前未处于有效时间范围内");
                stopSelf();
            }
        }
    }

    private boolean isInTimeRange() {
        Log.d(TAG, "======校验时间范围======");
        Calendar now = Calendar.getInstance();
        timeConfig= configManager.getConfig();
        String[] start = timeConfig.getStartTime().split(":");
        String[] end = timeConfig.getEndTime().split(":");

        int currentWeekDay = getCurrentWeekDay();
        ArrayList<Integer> selectedWeekDays = new ArrayList<>(timeConfig.getWeekDays());
        Log.d(TAG, "选择星期：" + selectedWeekDays);
        Log.d(TAG, "当前星期：" + currentWeekDay);
        if (!selectedWeekDays.contains(currentWeekDay)) {
            // 日期星期不处于生效时间段内
            return false;
        }

        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMinutes = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);

        if (endMinutes < startMinutes) {
            // 时间段跨天：12:00(T) - 06:00(T+1)
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
        } else {
            // 时间段处于当天：
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
        }
    }

    private int getCurrentWeekDay() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    /**
     * 外部调用：更新触摸时间
     */
    public static void updateTouchTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_TOUCH, System.currentTimeMillis()).apply();
        Log.d(TAG, "触摸时间已被更新");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (userActivityReceiver != null) {
            try {
                unregisterReceiver(userActivityReceiver);
                Log.d(TAG, "屏幕广播接收器已注销");
            } catch (Exception e) {
                Log.e(TAG, "注销广播接收器失败", e);
            }
        }
        handler.removeCallbacksAndMessages(null);
    }

    private void wakeUpAndUnlockScreen() {
        Log.d(TAG, "唤醒屏幕并解锁");

        // 获取PowerManager
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // 唤醒屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean isScreenOn = powerManager.isInteractive();
            if (!isScreenOn) {
                // 获取唤醒锁
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                PowerManager.FULL_WAKE_LOCK, "Demo:WakeLock");
                // 唤醒屏幕
                wakeLock.acquire(5000);
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}