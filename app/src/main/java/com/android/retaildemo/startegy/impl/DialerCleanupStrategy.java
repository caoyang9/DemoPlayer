package com.android.retaildemo.startegy.impl;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;

public class DialerCleanupStrategy implements CleanupStrategy {
    private static final String TAG = "DialerCleanupStrategy";

    @Override
    public String getStrategyName() {
        return "拨号器清理";
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public void cleanup(Context context, CleanupCallback callback) {
        ContentResolver resolver = context.getContentResolver();
        int totalDeleted = 0;
        // 1. 删通话记录
        try {
            int count = resolver.delete(CallLog.Calls.CONTENT_URI, null, null);
            totalDeleted += count;
            Log.d(TAG, "通话记录删除: " + count);
        } catch (Exception e) {
            Log.w(TAG, "通话记录删除失败: " + e.getMessage());
        }

        // 2. 删所有联系人
        try {
            int count = resolver.delete(ContactsContract.RawContacts.CONTENT_URI, null, null);
            totalDeleted += count;
            Log.d(TAG, "联系人删除: " + count);
        } catch (Exception e) {
            Log.w(TAG, "联系人删除失败: " + e.getMessage());
        }

        // 3. 删联系人详情
        try {
            int count = resolver.delete(ContactsContract.Data.CONTENT_URI, null, null);
            totalDeleted += count;
        } catch (Exception e) {
            Log.w(TAG, "详情删除失败: " + e.getMessage());
        }

        // 4. 删收藏夹
        try {
            int count = resolver.delete(ContactsContract.Contacts.CONTENT_URI,
                    ContactsContract.Contacts.STARRED + "=?", new String[]{"1"});
            totalDeleted += count;
        } catch (Exception e) {
            Log.w(TAG, "收藏夹删除失败: " + e.getMessage());
        }
        if (callback != null) {
            // 只要有任意一条删除成功就算成功
            callback.onComplete(getStrategyName(), totalDeleted > 0);
        }
    }
}