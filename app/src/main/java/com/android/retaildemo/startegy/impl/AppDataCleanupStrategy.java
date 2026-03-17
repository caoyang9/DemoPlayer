package com.android.retaildemo.startegy.impl;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;
import com.android.retaildemo.utils.MyDeviceAdminReceiver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppDataCleanupStrategy implements CleanupStrategy {
    private static final String TAG = "AppDataCleanupStrategy";
    @Override
    public void cleanup(Context context, CleanupCallback callback) {
        // 要清除数据的应用包名列表
        String[] targetPackages = {
                "com.android.dialer",        // 通话记录
                "com.android.providers.contacts", // 联系人提供者
//                "com.android.mms",           // 短信
//                "com.android.chrome"         // 浏览器
        };

        DevicePolicyManager dpm = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminName = new ComponentName(context,
                MyDeviceAdminReceiver.class);

        // 检查设备所有者权限
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.e(TAG, "不是设备所有者，无法清除应用数据");
            callback.onError(getStrategyName(), "缺少设备所有者权限");
            return;
        }

        CountDownLatch latch = new CountDownLatch(targetPackages.length);
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (String packageName : targetPackages) {
            dpm.clearApplicationUserData(
                    adminName,
                    packageName,
                    ContextCompat.getMainExecutor(context),
                    new DevicePolicyManager.OnClearApplicationUserDataListener() {
                        @Override
                        public void onApplicationUserDataCleared(String pkg, boolean succeeded) {
                            if (succeeded) {
                                Log.d(TAG, "成功清除 " + pkg + " 的数据");
                            } else {
                                Log.e(TAG, "清除 " + pkg + " 失败");
                                hasError.set(true);
                            }
                            latch.countDown();
                        }
                    }
            );
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
            callback.onComplete(getStrategyName(), !hasError.get());
        } catch (InterruptedException e) {
            callback.onError(getStrategyName(), "操作被中断");
        }
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public String getStrategyName() {
        return "AppDataCleanup";
    }
}
