package com.android.retaildemo.time;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.retaildemo.DemoPlayer;

/**
 * 定时器广播接收器
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ("ACTION_START_DEMO".equals(action)) {
            Log.d("AlarmManager", "闹钟触发-启动");
            // 闹钟触发：开始演示内容播放
            ConfigManager configManager = new ConfigManager(context);
            TimeConfig config = configManager.getConfig();

            if (config.isEnabled() && isInTimeRange(config, context)) {
                // 5秒后开始播放演示内容
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent demoIntent = new Intent(context, DemoPlayer.class);
                    demoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(demoIntent);
                }, 5000);
            }
            // 为未来7天设置闹钟
            Intent serviceIntent = new Intent(context, TimeService.class);
            context.startForegroundService(serviceIntent);

        } else if ("ACTION_STOP_DEMO".equals(action)) {
            Log.d("AlarmManager", "闹钟触发-停止");

            // 停止时钟管理Activity
            Intent stopDemoModeActivity = new Intent("STOP_DEMO_MODE_ACTIVITY");
            stopDemoModeActivity.setPackage(context.getPackageName());
            context.sendBroadcast(stopDemoModeActivity);

            // 停止演示内容播放Activity
            Intent stopIntent = new Intent("STOP_DEMO");
            stopIntent.setPackage(context.getPackageName());
            context.sendBroadcast(stopIntent);

            // 为未来7天设置闹钟
            Intent serviceIntent = new Intent(context, TimeService.class);
            context.startForegroundService(serviceIntent);
        }
    }

    private boolean isInTimeRange(TimeConfig config, Context context) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        String[] start = config.getStartTime().split(":");
        String[] end = config.getEndTime().split(":");

        int currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int startMinutes = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMinutes = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);

        if (endMinutes < startMinutes) {
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
        } else {
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
        }
    }
}
