package com.android.retaildemo.time;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.android.retaildemo.DemoPlayer;

import java.util.Calendar;
import java.util.List;

public class TimeService extends Service {
    private static final String TAG = "TimeService";
    private static final String CHANNEL_ID = "time_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private AlarmManager alarmManager;
    private ConfigManager configManager;

    @Override
    public void onCreate() {
        super.onCreate();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        configManager = new ConfigManager(this);

        startForegroundService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TimeService-onStartCommand()");
        if (intent != null && "STOP".equals(intent.getAction())) {
            Log.d(TAG, "停止TimeService");
            stopSelf();
        } else {
            Log.d(TAG, "为未来7天设置闹钟");
            scheduleTasks();
        }
        return START_STICKY;
    }

    private void scheduleTasks() {
        TimeConfig config = configManager.getConfig();
        // 时钟配置管理未开启
        if (!config.isEnabled()) {
            stopForeground(true);
            stopSelf();
            return;
        }

        // 清理历史任务
        cancelAllAlarms();
        // 获取当前时间
        Calendar now = Calendar.getInstance();
        int currentWeekDay = getConfigWeekDay(now);

        // 1. 处理今天的情况
        if (config.getWeekDays().contains(currentWeekDay)) {
            Calendar todayStart = getCalendarForTime(config.getStartTime());
            Calendar todayEnd = getCalendarForTime(config.getEndTime());

            // 设置到今天
            setToDate(todayStart, now);
            setToDate(todayEnd, now);

            // 如果结束时间小于开始时间（跨天），将结束时间加一天
            if (todayEnd.before(todayStart)) {
                todayEnd.add(Calendar.DAY_OF_MONTH, 1);
            }

            long nowMillis = System.currentTimeMillis();

            // 情况1：当前时间在开始时间之前
            if (nowMillis < todayStart.getTimeInMillis()) {
                // 设置今天的开始闹钟
                setAlarm(todayStart, config.getStartTime(), "ACTION_START_DEMO");
                Log.d(TAG, "今天还未开始，设置开始闹钟: " + todayStart.getTime());
            }

            // 情况2：当前时间在开始时间和结束时间之间
            if (nowMillis >= todayStart.getTimeInMillis() && nowMillis <= todayEnd.getTimeInMillis()) {
                // 立即启动演示（如果还没启动）
                if (!isDemoPlaying()) {
                    Log.d(TAG, "当前在播放时间段内，立即启动演示");
                    startDemoWithDelay();
                }
                // 设置今天的结束闹钟
                setAlarm(todayEnd, config.getEndTime(), "ACTION_STOP_DEMO");
                Log.d(TAG, "设置今天结束闹钟: " + todayEnd.getTime());
            }

            // 情况3：当前时间在结束时间之后（今天仍然有效，用于跨天情况）
            if (nowMillis > todayEnd.getTimeInMillis() && todayEnd.after(todayStart)) {
                // 今天的播放已经结束，不需要设置今天的闹钟
                Log.d(TAG, "今天的播放时段已结束");
            }
        } else {
            Log.d(TAG, "今天不在生效星期内");
        }

        // 2. 为未来6天设置闹钟（从明天开始）
        for (int day = 1; day < 7; day++) {
            Calendar futureDate = Calendar.getInstance();
            futureDate.add(Calendar.DAY_OF_YEAR, day);
            int weekDay = getConfigWeekDay(futureDate);

            if (config.getWeekDays().contains(weekDay)) {
                Calendar startTime = getCalendarForTime(config.getStartTime());
                Calendar endTime = getCalendarForTime(config.getEndTime());

                // 设置到未来日期
                setToDate(startTime, futureDate);
                setToDate(endTime, futureDate);

                // 如果结束时间小于开始时间，将结束时间加一天
                if (endTime.before(startTime)) {
                    endTime.add(Calendar.DAY_OF_MONTH, 1);
                }

                setAlarm(startTime, config.getStartTime(), "ACTION_START_DEMO");
                setAlarm(endTime, config.getEndTime(), "ACTION_STOP_DEMO");

                Log.d(TAG, "设置未来第" + day + "天闹钟: " +
                        startTime.getTime() + " - " + endTime.getTime());
            }
        }
    }

    private boolean isDemoPlaying() {
        // 可以通过SharedPreferences或全局变量记录状态
        // 简单实现：检查是否有DemoActivity在运行
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (topActivity.getClassName().equals(DemoPlayer.class.getName())) {
                return true;
            }
        }
        return false;
    }

    // 5秒后启动演示
    private void startDemoWithDelay() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("ACTION_START_DEMO_NOW");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "启动演示失败", e);
        }
    }

    // 获取指定时间的Calendar对象
    private Calendar getCalendarForTime(String time) {
        String[] parts = time.split(":");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    // 将时间设置到指定日期
    private void setToDate(Calendar target, Calendar date) {
        target.set(Calendar.YEAR, date.get(Calendar.YEAR));
        target.set(Calendar.MONTH, date.get(Calendar.MONTH));
        target.set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH));
    }

    private int getConfigWeekDay(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    private void setAlarm(Calendar calendar, String time, String action) {
        String[] parts = time.split(":");
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        calendar.set(Calendar.SECOND, 0);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this,
                action.hashCode() + calendar.get(Calendar.DAY_OF_YEAR),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 检查设置的闹钟时间是否在未来
        if (calendar.getTimeInMillis() > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 使用 setExactAndAllowWhileIdle
                if (checkSelfPermission(android.Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(),
                            pendingIntent);
                } else {
                    // 如果没有权限，使用非精确闹钟
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            calendar.getTimeInMillis(), pendingIntent);
                    Log.w(TAG, "No exact alarm permission, using inexact alarm");
                }
            } else {
                // Android 11 及以下
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }

    private void cancelAllAlarms() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        for (int i = 0; i < 100; i++) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, i, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundService() {
        createNotificationChannel();

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "前台服务已启动");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "演示定时服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于定时播放演示视频");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        // 创建一个点击后打开主Activity的Intent
        Intent intent = new Intent(this, com.android.retaildemo.DemoModeActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("演示定时服务")
                    .setContentText("正在运行中...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(pendingIntent)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("演示定时服务")
                    .setContentText("正在运行中...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(pendingIntent)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        }
    }
}