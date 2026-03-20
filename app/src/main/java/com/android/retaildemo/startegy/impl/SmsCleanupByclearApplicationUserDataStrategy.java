package com.android.retaildemo.startegy.impl;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 短信清理策略类
 * ContentResolver
 */
public class SmsCleanupByclearApplicationUserDataStrategy implements CleanupStrategy {
    private static final String TAG = "SmsCleanupStrategy";
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
        try {
            Log.d(TAG, "尝试删除短信数据");
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            // 需要系统签名
            Method method = ActivityManager.class.getMethod(
                    "clearApplicationUserData",
                    String.class,
                    Class.forName("android.content.pm.IPackageDataObserver")
            );

            Object observer = Proxy.newProxyInstance(
                    ClassLoader.getSystemClassLoader(),
                    new Class[]{Class.forName("android.content.pm.IPackageDataObserver")},
                    (proxy, m, args) -> {
                        if (m.getName().equals("onRemoveCompleted")) {
                            Log.i(TAG, args[0] + " 清除结果: " + args[1]);
                        }
                        return null;
                    }
            );
            method.invoke(am, "com.android.providers.telephony", observer);

        } catch (Exception e) {
            Log.e(TAG, "失败", e);
        }
    }
}