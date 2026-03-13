package com.android.retaildemo.utils;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

public class LockScreenManager {
    private static final String TAG = "LockScreenManager";

    private Context mContext;
    private DevicePolicyManager mDpm;
    private ComponentName mAdminName;

    public LockScreenManager(Context context) {
        this.mContext = context;
        this.mDpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
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
     * 核心方法：确保设备无密码且无法设置密码
     */
    public void ensureNoLockScreen() {
        if (!isAdminActive()) {
            Log.d(TAG, "设备管理员未激活，无法禁用锁屏密码设置");
            return;
        }
        setExtremePasswordPolicy();
    }

    private void setExtremePasswordPolicy() {
        try {
            // 设置一个几乎不可能手动输入的密码质量要求
            mDpm.setPasswordQuality(mAdminName, DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
            // 设置一个很长的最小密码长度
//            mDpm.setPasswordMinimumLength(mAdminName, 24);
            // 不可能满足的条件：最少字母、数字和字符
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mDpm.setPasswordMinimumLetters(mAdminName, 10);
                mDpm.setPasswordMinimumNumeric(mAdminName, 10);
                mDpm.setPasswordMinimumSymbols(mAdminName, 10);
            }
            Log.d(TAG, "极端密码策略已应用");
        } catch (Exception e) {
            Log.e(TAG, "设置密码策略失败", e);
        }
    }

    public void disableFactoryReset() {
        if (!isAdminActive()) {
            Log.d(TAG, "设备管理员未激活，无法禁用恢复出厂设置");
            return;
        }
        try {
            mDpm.addUserRestriction(mAdminName, UserManager.DISALLOW_FACTORY_RESET);
        } catch (Exception e) {
            Log.e(TAG, "禁用恢复出厂设置失败", e);
        }
    }

    public void restoreAllSettings() {
        restorePasswordSettings();
        restoreFactoryReset();
    }

    private void restorePasswordSettings() {
        if (!isAdminActive()) {
            Log.d(TAG, "设备管理员未激活，无法恢复密码设置");
            return;
        }
        try {
            mDpm.setPasswordQuality(mAdminName, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
            // 恢复字符要求
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mDpm.setPasswordMinimumLetters(mAdminName, 0);
                mDpm.setPasswordMinimumNumeric(mAdminName, 0);
                mDpm.setPasswordMinimumSymbols(mAdminName, 0);
            }
            Log.d(TAG, "密码设置功能已恢复");
        } catch (Exception e) {
            Log.e(TAG, "恢复密码设置失败", e);
        }
    }

    private void restoreFactoryReset() {
        if (!isAdminActive()) {
            Log.d(TAG, "设备管理员未激活，无法恢复恢复出厂设置");
            return;
        }
        try {
            mDpm.clearUserRestriction(mAdminName, UserManager.DISALLOW_FACTORY_RESET);
            Log.d(TAG, "已恢复恢复出厂设置功能");
        } catch (Exception e) {
            Log.e(TAG, "恢复恢复出厂设置失败", e);
        }
    }
}