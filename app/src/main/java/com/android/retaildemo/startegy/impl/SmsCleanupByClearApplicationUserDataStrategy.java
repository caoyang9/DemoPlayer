package com.android.retaildemo.startegy.impl;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 短信清理策略类 - 清理Google Messages数据
 */
public class SmsCleanupByClearApplicationUserDataStrategy implements CleanupStrategy {
    private static final String TAG = "SmsCleanupStrategy";
//    private static final String SMS_PACKAGE = "com.google.android.apps.messaging"; // tcl
    private static final String SMS_PACKAGE = "com.android.messaging";

    @Override
    public String getStrategyName() {
        return "短信清理";
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public void cleanup(Context context, CleanupCallback callback) {
        new Thread(() -> {
            try {
                Log.i(TAG, "开始清理短信数据: " + SMS_PACKAGE);

                // 强制停止目标应用
                forceStopPackage(context, SMS_PACKAGE);
                Thread.sleep(500); // 等待进程停止

                // 清除应用数据
                boolean success = clearApplicationUserData(context, SMS_PACKAGE);

                if (success) {
                    Log.i(TAG, "短信清理成功");
                    if (callback != null) {
                        callback.onComplete(getStrategyName(), true);
                    }
                } else {
                    Log.w(TAG, "短信清理失败");
                    if (callback != null) {
                        callback.onError(getStrategyName(), "清理失败");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "清理异常", e);
                if (callback != null) {
                    callback.onError(getStrategyName(), e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 强制停止应用
     */
    private void forceStopPackage(Context context, String packageName) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            Method method = ActivityManager.class.getMethod("forceStopPackage", String.class);
            method.invoke(am, packageName);
            Log.d(TAG, "已停止: " + packageName);
        } catch (Exception e) {
            Log.e(TAG, "停止应用失败", e);
        }
    }

    /**
     * 清除应用数据
     */
    private boolean clearApplicationUserData(Context context, String packageName) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            // 获取方法
            Method method = ActivityManager.class.getMethod(
                    "clearApplicationUserData",
                    String.class,
                    Class.forName("android.content.pm.IPackageDataObserver")
            );

            // 等待清除完成
            CountDownLatch latch = new CountDownLatch(1);
            final boolean[] success = {false};

            // 创建回调
            Class<?> observerClass = Class.forName("android.content.pm.IPackageDataObserver");
            Object observer = java.lang.reflect.Proxy.newProxyInstance(
                    observerClass.getClassLoader(),
                    new Class[]{observerClass},
                    (proxy, m, args) -> {
                        if (m.getName().equals("onRemoveCompleted")) {
                            success[0] = (boolean) args[1];
                            latch.countDown();
                        }
                        return null;
                    }
            );

            // 调用清除
            method.invoke(am, packageName, observer);

            // 等待完成
            latch.await(5, TimeUnit.SECONDS);

            Log.d(TAG, "清除结果: " + success[0]);
            return success[0];

        } catch (Exception e) {
            Log.e(TAG, "清除数据失败", e);
            return false;
        }
    }
}