package com.android.retaildemo.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordManager {
    private static final String PREF_NAME = "demo_mode_prefs";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String DEFAULT_PASSWORD = "123456"; // 默认密码
    private static PasswordManager instance;
    private SharedPreferences sharedPreferences;

    private PasswordManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 如果没有设置过密码，初始化默认密码
        if (!isPasswordSet()) {
            setPassword(DEFAULT_PASSWORD);
        }
    }

    public static synchronized PasswordManager getInstance(Context context) {
        if (instance == null) {
            instance = new PasswordManager(context.getApplicationContext());
        }
        return instance;
    }

    // 检查密码是否已设置
    public boolean isPasswordSet() {
        return sharedPreferences.contains(KEY_PASSWORD_HASH);
    }

    // 设置新密码
    public boolean setPassword(String password) {
        if (password == null || password.length() != 6 || !TextUtils.isDigitsOnly(password)) {
            return false;
        }
        String hashedPassword = hashPassword(password);
        return sharedPreferences.edit().putString(KEY_PASSWORD_HASH, hashedPassword).commit();
    }

    // 验证密码
    public boolean verifyPassword(String password) {
        if (password == null || password.length() != 6 || !TextUtils.isDigitsOnly(password)) {
            return false;
        }
        String storedHash = sharedPreferences.getString(KEY_PASSWORD_HASH, null);
        if (storedHash == null) {
            return false;
        }
        String inputHash = hashPassword(password);
        return storedHash.equals(inputHash);
    }

    // 密码哈希化（MD5加密）
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return password; // 如果加密失败，返回原密码
        }
    }

    // 重置为默认密码
    public void resetToDefaultPassword() {
        setPassword(DEFAULT_PASSWORD);
    }
}
