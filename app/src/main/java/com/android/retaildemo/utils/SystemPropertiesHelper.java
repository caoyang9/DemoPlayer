package com.android.retaildemo.utils;

import android.util.Log;

import java.lang.reflect.Method;

public class SystemPropertiesHelper {
    private static final String TAG = "SystemPropertiesHelper";
    private static Class<?> mSystemPropertiesClass;

    static {
        try {
            mSystemPropertiesClass = Class.forName("android.os.SystemProperties");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取系统属性值
     */
    public static String get(String key, String defaultValue) {
        if (mSystemPropertiesClass == null) return defaultValue;

        try {
            Method getter = mSystemPropertiesClass.getDeclaredMethod("get", String.class, String.class);
            return (String) getter.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get system property: " + key, e);
            return defaultValue;
        }
    }

    public static String get(String key) {
        if (mSystemPropertiesClass == null) return "";

        try {
            Method getter = mSystemPropertiesClass.getDeclaredMethod("get", String.class);
            return (String) getter.invoke(null, key);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get system property: " + key, e);
            return "";
        }
    }

    public static void set(String key, String value) {
        if (mSystemPropertiesClass == null) return;

        try {
            Method setter = mSystemPropertiesClass.getDeclaredMethod("set", String.class, String.class);
            setter.invoke(null, key, value);
            Log.d(TAG, "Set property: " + key + " = " + value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set system property: " + key, e);
        }
    }
    /**
     * 获取布尔型属性
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        if (mSystemPropertiesClass == null) return defaultValue;

        try {
            Method getter = mSystemPropertiesClass.getDeclaredMethod("getBoolean", String.class, boolean.class);
            return (boolean) getter.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get boolean property: " + key, e);
            return defaultValue;
        }
    }

    /**
     * 获取整型属性
     */
    public static int getInt(String key, int defaultValue) {
        if (mSystemPropertiesClass == null) return defaultValue;

        try {
            Method getter = mSystemPropertiesClass.getDeclaredMethod("getInt", String.class, int.class);
            return (int) getter.invoke(null, key, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get int property: " + key, e);
            return defaultValue;
        }
    }

}
