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
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class UserActivityMonitorService extends Service implements SensorEventListener{

    private static final String TAG = "MonitorService";
    private static final long TIMEOUT_MS = 40 * 1000; // 20秒测试
    private static final String PREFS_NAME = "demo_prefs";
    private static final String KEY_LAST_TOUCH = "last_touch_time";
    private static final String CHANNEL_ID = "monitor_channel";
    private static final int NOTIFY_ID = 1001;
    private Handler handler;
    private SharedPreferences prefs;
    private long lastTouchTime;
    private BroadcastReceiver userActivityReceiver;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private PowerManager mPowerManager;
    private static final float MOVEMENT_THRESHOLD = 0.4f;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "UserActivityMonitorService服务创建");

        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 启动前台服务
        startForegroundService();

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (mAccelerometer != null) {
            mSensorManager.registerListener((SensorEventListener) this, mAccelerometer,
                    SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "加速度传感器监听已注册");
        }
        userActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action) ||
                        Intent.ACTION_USER_PRESENT.equals(action) ||
                        Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "监听到解锁、屏幕亮/灭事件");
                    // 屏幕亮起或用户解锁，更新触摸事件
                    updateTouchTime(context);
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
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // 计算加速度变化（静止时约9.8）
            float magnitude = (float) Math.sqrt(x*x + y*y + z*z);
            float deviation = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);

            // 只要有明显偏离重力，认为手机正在被使用
            if (deviation > MOVEMENT_THRESHOLD) {
                Log.d(TAG, "手机正在被使用，更新用户活动时间");
                updateTouchTime(getApplicationContext());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service被重建");
        startMonitoring();
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
        handler.post(new Runnable() {
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
        });
    }
    private void startHelloActivity() {
        try {
            Log.d(TAG, "尝试启动");
            Intent intent = new Intent(this, DemoPlayer.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 关键：创建PendingIntent来启动Activity
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
        wakeUpAndUnlockScreen();

        // 测试服务启动Activity
        startHelloActivity();

//        Intent intent = new Intent(this, DemoPlayer.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                this, 0, intent,
//                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
//
//        Notification notification;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            Log.d(TAG, "重启演示1");
//            NotificationChannel channel = new NotificationChannel(
//                    "restart", "重启演示", NotificationManager.IMPORTANCE_HIGH);
//            NotificationManager manager = getSystemService(NotificationManager.class);
//            manager.createNotificationChannel(channel);
//
//            notification = new Notification.Builder(this, "restart")
//                    .setContentTitle("演示模式")
//                    .setContentText("30分钟无操作，点击重启演示应用")
//                    .setSmallIcon(android.R.drawable.ic_dialog_info)
//                    .setContentIntent(pendingIntent)
//                    .setAutoCancel(true)
//                    .build();
//        } else {
//            Log.d(TAG, "重启演示2");
//            notification = new NotificationCompat.Builder(this, "restart")
//                    .setContentTitle("演示模式")
//                    .setContentText("10秒无操作，点击重启演示")
//                    .setSmallIcon(android.R.drawable.ic_dialog_info)
//                    .setContentIntent(pendingIntent)
//                    .setAutoCancel(true)
//                    .setPriority(NotificationCompat.PRIORITY_HIGH)
//                    .build();
//        }
//
//        NotificationManager manager = getSystemService(NotificationManager.class);
//        manager.notify(2002, notification);
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
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
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