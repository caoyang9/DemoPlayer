package com.android.retaildemo.utils;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "DeviceAdminReceiver";
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "设备管理员已激活");
        LockScreenManager lockScreenManager = new LockScreenManager(context);
        boolean deviceOwner = lockScreenManager.isDeviceOwner();
        Log.d(TAG, String.valueOf(deviceOwner));
        // 激活后立即尝试禁用任何方式的密码锁定
        lockScreenManager.ensureNoLockScreen();
        lockScreenManager.disableFactoryReset();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "设备管理员已禁用");
    }
}
