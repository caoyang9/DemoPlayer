/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.retaildemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.android.retaildemo.startegy.CleanupManager;
import com.android.retaildemo.utils.LockScreenManager;
import com.android.retaildemo.work.CleanupWorker;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.TimeUnit;

/**
 * This is the activity for playing the retail demo video. This will also try to keep
 * the screen on.
 *
 * This will check for the demo video in {@link Environment#//getDataPreloadsDemoDirectory()} or
 * {@link Context#getObbDir()}. If the demo video is not present, it will run a task to download it
 * from the specified url.
 */
public class DemoPlayer extends Activity implements DownloadVideoTask.ResultListener {

    private static final String TAG = "DemoPlayer";
    private static final boolean DEBUG = false;
    // 达到阈值时间无交互，演示应用退出
    private static final long INACTIVITY_TIMEOUT = 30 * 60 * 1000;
//    private static final long INACTIVITY_TIMEOUT = 8 * 60 * 60 * 1000L;
    private Runnable mInactivityRunnable;

    /**
     * Maximum amount of time to wait for demo user to set up.
     * After it the user can tap the screen to exit
     */
    private static final long READY_TO_TAP_MAX_DELAY_MS = 60 * 1000; // 1min

    private PowerManager mPowerManager;

    private VideoView mVideoView;
    private String mDownloadPath;
    private boolean mUsingDownloadedVideo;
    private Handler mHandler;
    private boolean mReadyToTap;
    private SettingsObserver mSettingsObserver;
    private File mPreloadedVideoFile;
    private Handler mHideHandler = new Handler();
    private boolean mIsHidden = false;

    private static final String PREFS_NAME = "demo_prefs";
    private static final String KEY_LAST_TOUCH = "last_touch_time";
    private static final String CLEANUP_WORK_NAME = "periodic_cleanup_work";
    private static final int POWER_LOWER = 30;  // 下限30%
    private static final int POWER_UPPER = 70;  // 上限70%
    private BroadcastReceiver mBatteryReceiver;
    private boolean mBatteryReceiverRegistered = false;
    private boolean mBatteryProtectFlag = false;

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BroadcastReceiver-batteryReceiver-onReceive()");
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            Log.d(TAG, "level: " + level + "; scale: " + scale + "; status: " + status);
            float batteryPct = level * 100 / (float) scale;

            // 是否在充电
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);
            Log.d(TAG, "isCharging: " + isCharging);

            if (isCharging) {
                if (batteryPct >= POWER_UPPER) {
                    Log.d(TAG, "正在充电中，电量保护达到上限，准备停止充电");
                    // 达到上限，停止充电
                    stopCharging();
                }
            } else {
                if (batteryPct <= POWER_LOWER && isPowerConnected()) {
                    Log.d(TAG, "充电已连接，电量保护达到下限，准备开始充电");
                    // 低于下限且有电源，恢复充电
                    startCharging();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setScreenOffTimeoutIfNeeded();
        stopMonitorService();
        // 确保屏幕常量、设备锁屏也能显示该窗口、自动解锁屏幕
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 加载视频播放的布局文件
        setContentView(R.layout.retail_video);
        // 立即应用一次全屏设置，使窗口显示前就设置了UI可见性
        hideSystemUI();

        // 创建电池状态变化广播接收器实例
        if (mBatteryProtectFlag) {
            mBatteryReceiver = new BatteryReceiver();
        }

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mHandler = new Handler();

        // 初始化无交互后需执行任务
        mInactivityRunnable = () -> {
            Log.d(TAG, "8小时无用户交互，退出演示并关闭屏幕");
            // 1. 退出到桌面
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);

            // 取消每30分钟需执行的定时清理任务
            cancelPeriodicCleanup();
            // 清理线程池资源
            CleanupManager.shutdownInstance();
            // 关闭屏幕
            turnScreenOff();
            // 关闭当前Activity
            finish();
        };
        // 启动后台每30分钟的周期性清理任务
        schedulePeriodicCleanup();

        // 获取预加载的视频资源文件名称
        final String preloadedFileName = getString(R.string.retail_demo_video_file_name);
//        mPreloadedVideoFile = new File(Environment.getDataPreloadsDemoDirectory(),
//                preloadedFileName);
        mPreloadedVideoFile = new File(this.getExternalFilesDir(null), preloadedFileName);
        mDownloadPath = getObbDir().getPath() + File.separator + preloadedFileName;
        Log.d(TAG, "mDownloadPath: " + mDownloadPath);
        mVideoView = (VideoView) findViewById(R.id.video_content);

        // 视频准备就绪后开始播放
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);  // 循环播放
                mVideoView.start();  // 立即开始播放
            }
        });

        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if (mUsingDownloadedVideo && mPreloadedVideoFile.exists()) {
                    Log.d(TAG, "Error using the downloaded video, "
                            + "falling back to the preloaded video at " + mPreloadedVideoFile);
                    mUsingDownloadedVideo = false;
                    setVideoPath(mPreloadedVideoFile.getPath());
                    // And delete the downloaded video so that we don't try to use it
                    // again next time.
                    new File(mDownloadPath).delete();
                } else {
                    Log.d(TAG, "使用备用视图");
                    displayFallbackView();
                }
                return true;
            }
        });

        // 用户何时可以点击屏幕退出演示模式
        mReadyToTap = isUserSetupComplete();
        if (!mReadyToTap) {
            // Wait for setup to finish
            mSettingsObserver = new SettingsObserver();
            mSettingsObserver.register();
            // Allow user to exit the demo player if setup takes too long
            mHandler.postDelayed(() -> {
                mReadyToTap = true;
            }, READY_TO_TAP_MAX_DELAY_MS);
        }

        loadVideo();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
        );
    }

    /**
     * 电源是否连接到充电状态
     * @return boolean
     */
    private boolean isPowerConnected() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    private void stopCharging() {
        try {
            FileWriter fw = new FileWriter("/sys/class/power_supply/battery/charging_enabled");
            fw.write("0");
            fw.close();

            Log.d(TAG, "Charging stopped!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop charging", e);
        }
    }

    private void startCharging() {
        try {
            FileWriter fw = new FileWriter("/sys/class/power_supply/battery/charging_enabled");
            fw.write("1");
            fw.close();

            Log.d(TAG, "Charging resumed!");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start charging", e);
        }
    }

    private void schedulePeriodicCleanup() {
        LockScreenManager lockScreenManager = new LockScreenManager(this);
        boolean adminActive = lockScreenManager.isAdminActive();
        if (!adminActive) {
            Log.d(TAG, "应用需要获取设备所有者权限，以启动定时清理任务");
            return;
        }

        PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(CleanupWorker.class, 15, TimeUnit.MINUTES)
                .addTag("cleanup-task")
                .build();
        // 使用WorkManager调度任务
        WorkManager workManager = WorkManager.getInstance(this);
        // 如果存在具有相同唯一名称的待处理任务，则不执行任何操作。
        workManager.enqueueUniquePeriodicWork(
                CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
        );
    }

    private void cancelPeriodicCleanup() {
        WorkManager workManager = WorkManager.getInstance(this);

        // 通过唯一任务名称取消任务
        workManager.cancelUniqueWork(CLEANUP_WORK_NAME);
        Log.d(TAG, "已取消30分钟周期性清理任务");
    }

    private void stopMonitorService() {
        Log.d(TAG, "停止监控服务");
        Intent serviceIntent = new Intent(this, UserActivityMonitorService.class);
        stopService(serviceIntent);
    }

    /**
     * 重置无交互计时器
     */
    private void resetInactivityTimer() {
        // 移除之前的计时任务
        if (mHandler != null && mInactivityRunnable != null) {
            mHandler.removeCallbacks(mInactivityRunnable);
            // 启动8小时计时
            mHandler.postDelayed(mInactivityRunnable, INACTIVITY_TIMEOUT);
            Log.d(TAG, "重置计时器，与设备连续8小时无交互将关闭屏幕");
        }
    }

    /**
     * 关闭屏幕
     */
    private void turnScreenOff() {
        try {
            runOnUiThread(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                Log.d(TAG, "已移除常亮标志，屏幕将自动休眠");
            });
        } catch (Exception e) {
            Log.e(TAG, "关闭屏幕失败", e);
        }
    }

    private void displayFallbackView() {
        Log.d(TAG, "Showing the fallback view");
        findViewById(R.id.fallback_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.video_layout).setVisibility(View.GONE);
    }

    private void displayVideoView() {
        Log.d(TAG, "Showing the video view");
        findViewById(R.id.video_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.fallback_layout).setVisibility(View.GONE);
    }

    private void loadVideo() {
        // 若视频已下载，则使用已下载的版本并检查是否有更新。
        boolean isVideoSet = false;
        if (new File(mDownloadPath).exists()) {
            Log.d(TAG, "Using the already existing video at " + mDownloadPath);
            setVideoPath(mDownloadPath);
            isVideoSet = true;
        } else if (mPreloadedVideoFile.exists()) {
            Log.d(TAG, "Using the preloaded video at " + mPreloadedVideoFile);
            setVideoPath(mPreloadedVideoFile.getPath());
            isVideoSet = true;
        }

        final String downloadUrl = getString(R.string.retail_demo_video_download_url);
        // 如果视频下载链接为空，则无需启动下载任务，直接return
        if (TextUtils.isEmpty(downloadUrl)) {
            if (!isVideoSet) {
                displayFallbackView();
            }
            return;
        }
        if (!checkIfDownloadingAllowed()) {
            Log.d(TAG, "Downloading not allowed, neither starting download nor checking"
                    + " for an update.");
            if (!isVideoSet) {
                displayFallbackView();
            }
            return;
        }
        new DownloadVideoTask(this, mDownloadPath, mPreloadedVideoFile, this).run();
    }

    private boolean checkIfDownloadingAllowed() {
        final int lastBootCount = DataReaderWriter.readLastBootCount(this);
        final int bootCount = Settings.Global.getInt(getContentResolver(),
                Settings.Global.BOOT_COUNT, -1);
        // Something went wrong, don't do anything.
        if (bootCount == -1) {
            return false;
        }
        // Error reading the last boot count, just write the current boot count.
        if (lastBootCount == -1) {
            DataReaderWriter.writeLastBootCount(this, bootCount);
            return false;
        }
        // 上次下载视频时启动次数与当前启动次数相不同：设备刚重启过，允许下载
        if (lastBootCount != bootCount) {
            DataReaderWriter.writeLastBootCount(this, bootCount);
            return true;
        }
        return false;
    }

    @Override
    public void onFileDownloaded(final String filePath) {
        mUsingDownloadedVideo = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setVideoPath(filePath);
            }
        });
    }

    @Override
    public void onError() {
        displayFallbackView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "物理按键按下: " + keyCode);

        // 只有当 mReadyToTap 为 true 时才响应按键退出
        if (mReadyToTap) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                case KeyEvent.KEYCODE_POWER:
                case KeyEvent.KEYCODE_HOME:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                    Log.d(TAG, "演示时捕捉到物理按键事件");
                    exitToHomeScreen();
                    return true; // 已处理
                default:
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Log.d(TAG, "演示时捕捉到触摸屏幕事件");
        if (mReadyToTap) {
            exitToHomeScreen();
        }
        return true;
    }

    private void exitToHomeScreen() {
        Log.d(TAG, "启动监听服务，演示应用退出。");
        // 关闭电池健康保护
        if (mBatteryProtectFlag) {
            startCharging();
        }
        // 1. 保存当前触摸的时间戳
        saveTouchTime();

        // 2. 启动后台监控Service
        startMonitorService();

        cancelPeriodicCleanup();

        // 3. 退出到桌面
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);

        finish();
    }

    /**
     * 保存触摸时间到SharedPreferences
     */
    private void saveTouchTime() {
        // <long name="last_touch_time" value="1772499740395" />
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long currentTimeMillis = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_TOUCH, currentTimeMillis).apply();
        Log.d(TAG, "触摸时间已保存：" + currentTimeMillis);
    }

    /**
     * 启动监控服务
     */
    private void startMonitorService() {
        Intent intent = new Intent(this, UserActivityMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "启动UserActivityMonitorService");
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void disableSelf() {
        final String componentName = getString(R.string.demo_overlay_app_component);
        if (!TextUtils.isEmpty(componentName)) {
            ComponentName component = ComponentName.unflattenFromString(componentName);
            if (component != null) {
                Intent intent = new Intent();
                intent.setComponent(component);
                ResolveInfo resolveInfo = getPackageManager().resolveService(intent, 0);
                if (resolveInfo != null) {
                    startService(intent);
                } else {
                    resolveInfo = getPackageManager().resolveActivity(intent,
                            PackageManager.MATCH_DEFAULT_ONLY);
                    if (resolveInfo != null) {
                        startActivity(intent);
                    } else {
                        Log.w(TAG, "Component " + componentName + " cannot be resolved");
                    }
                }
            }
        }
        getPackageManager().setComponentEnabledSetting(getComponentName(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }

    @Override
    public void onPause() {
        if (mVideoView != null) {
            mVideoView.pause();
        }
        // If power key is pressed to turn screen off, turn screen back on
        if (!mPowerManager.isInteractive()) {
            forceTurnOnScreen();
        }

        // 应用暂停时，暂停计时器
        if (mHandler != null && mInactivityRunnable != null) {
            mHandler.removeCallbacks(mInactivityRunnable);
        }

        if (mBatteryReceiverRegistered) {
            unregisterReceiver(mBatteryReceiver);
            mBatteryReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 注册电池状态变化系统广播接收器
        if (mBatteryProtectFlag && !mBatteryReceiverRegistered) {
            registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            mBatteryReceiverRegistered = true;
        }

        // Resume video playing
        if (mVideoView != null && !mIsHidden) {
            mVideoView.start();
        }
        // 应用恢复时，重新启动计时器
        resetInactivityTimer();
    }

    @Override
    protected void onDestroy() {
        if (mSettingsObserver != null) {
            mSettingsObserver.unregister();
            mSettingsObserver = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        mHideHandler.removeCallbacksAndMessages(null);

        if (mBatteryReceiverRegistered) {
            unregisterReceiver(mBatteryReceiver);
            mBatteryReceiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            // Make view fullscreen.
            // And since flags SYSTEM_UI_FLAG_HIDE_NAVIGATION and SYSTEM_UI_FLAG_HIDE_NAVIGATION
            // might get cleared on user interaction, we do this here.
            Log.d(TAG, "窗口获得焦点，全屏显示");
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                            //| View.STATUS_BAR_DISABLE_BACK);
        }
    }

    private void setVideoPath(String videoPath) {
        // Load the video from resource
        try {
            mVideoView.setVideoPath(videoPath);
            displayVideoView();
        } catch (Exception e) {
            Log.e(TAG, "Exception setting video uri! " + e.getMessage());
            displayFallbackView();
        }
    }

    private void forceTurnOnScreen() {
        @SuppressLint("InvalidWakeLockTag")
        final PowerManager.WakeLock wakeLock = mPowerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        wakeLock.acquire();
        // Device woken up, release the wake-lock
        wakeLock.release();
    }

    private class SettingsObserver extends ContentObserver {
        private static final String DEMO_USER_SETUP_COMPLETE = "demo_user_setup_complete";
//        private final Uri mDemoModeSetupComplete = Settings.Secure.getUriFor(
//                Settings.Secure.DEMO_USER_SETUP_COMPLETE);
        private final Uri mDemoModeSetupComplete;
        SettingsObserver() {
            super(mHandler);
            mDemoModeSetupComplete = Settings.Secure.getUriFor(DEMO_USER_SETUP_COMPLETE);
        }

        void register() {
            ContentResolver cr = getContentResolver();
            cr.registerContentObserver(mDemoModeSetupComplete, false, this);
        }

        void unregister() {
            getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "SettingsObserver-onchange()");
            if (mDemoModeSetupComplete.equals(uri)) {
                mReadyToTap = true;
            }
        }
    }

    private boolean isUserSetupComplete() {
        String demoUserSetupComplete = Settings.Secure.getString(getContentResolver(), "demo_user_setup_complete");
        Log.d(TAG, "demoUserSetupComplete: " + demoUserSetupComplete);

        // 普通手机，该值为null，需要演示模式应该默认true（假设设置已完成）
        if (demoUserSetupComplete == null) {
            Log.d(TAG, "Setting not found, assuming setup is complete");
            return true;
        }

        return "1".equals(Settings.Secure.getString(getContentResolver(),
                "demo_user_setup_complete"));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent 被调用, action=" + intent.getAction());

        setIntent(intent);
    }

    private void setScreenOffTimeoutIfNeeded() {
        try {
            // 获取当前无操作息屏时间
            int currentTimeout = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT);
            Log.d(TAG, "Current screen timeout: " + currentTimeout + "ms");
            int targetTimeout = 15000;
            if (currentTimeout > targetTimeout) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        targetTimeout);
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Screen timeout setting not found", e);
        }
    }
}
