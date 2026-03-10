package com.android.retaildemo.utils;

import android.app.admin.DevicePolicyManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

public class LockScreenManager {
    private static final String TAG = "LockScreenManager";

    private Context mContext;
    private DevicePolicyManager mDpm;
    private ComponentName mAdminName;
    private KeyguardManager mKeyguardManager;

    public LockScreenManager(Context context) {
        this.mContext = context;
        this.mDpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        this.mAdminName = new ComponentName(context, MyDeviceAdminReceiver.class);
    }

    /**
     * 检查设备管理员是否已激活
     */
    public boolean isAdminActive() {
        return mDpm.isAdminActive(mAdminName);
    }

    /**
     * 检查是否是设备所有者
     */
    public boolean isDeviceOwner() {
        return mDpm.isDeviceOwnerApp(mContext.getPackageName());
    }

    /**
     * 检查当前是否有密码被设置
     * Returns whether the device is secured with a PIN, pattern or password.
     * See also isKeyguardSecure which treats SIM locked states as secure.
     * Returns:
     * true if a PIN, pattern or password was set.
     */
    public boolean hasPassword() {
        return mKeyguardManager.isDeviceSecure();
    }

    /**
     * 核心方法：确保设备无密码且无法设置密码
     */
    public void ensureNoLockScreen() {
        if (!isAdminActive()) {
            Log.d(TAG, "设备管理员未激活");
            return;
        }
        setExtremePasswordPolicy(mDpm, mAdminName);
    }

    private void setExtremePasswordPolicy(DevicePolicyManager dpm, ComponentName adminName) {
        try {
            // 设置一个几乎不可能手动输入的密码质量要求
            dpm.setPasswordQuality(adminName, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
            // 设置一个很长的最小密码长度
            dpm.setPasswordMinimumLength(adminName, 24);
            // 不可能满足的条件：最少字母、数字和字符
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.setPasswordMinimumLetters(adminName, 10);
                dpm.setPasswordMinimumNumeric(adminName, 10);
                dpm.setPasswordMinimumSymbols(adminName, 10);
            }
            Log.d(TAG, "极端密码策略已应用");
        } catch (Exception e) {
            Log.e(TAG, "设置密码策略失败", e);
        }
    }
}