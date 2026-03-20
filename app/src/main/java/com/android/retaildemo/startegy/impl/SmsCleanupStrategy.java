package com.android.retaildemo.startegy.impl;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 短信清理策略类
 * ContentResolver
 */
public class SmsCleanupStrategy implements CleanupStrategy {
    private static final String TAG = "SmsCleanupStrategy";

    // ==================== 核心URI定义 ====================

    // 短信相关URI
    private static final Uri SMS_URI = Uri.parse("content://sms");
    private static final Uri SMS_INBOX_URI = Uri.parse("content://sms/inbox");
    private static final Uri SMS_SENT_URI = Uri.parse("content://sms/sent");
    private static final Uri SMS_DRAFT_URI = Uri.parse("content://sms/draft");
    private static final Uri SMS_OUTBOX_URI = Uri.parse("content://sms/outbox");
    private static final Uri SMS_FAILED_URI = Uri.parse("content://sms/failed");
    private static final Uri SMS_QUEUED_URI = Uri.parse("content://sms/queued");

    // 彩信相关URI
    private static final Uri MMS_URI = Uri.parse("content://mms");
    private static final Uri MMS_PART_URI = Uri.parse("content://mms/part");
    private static final Uri MMS_ADDR_URI = Uri.parse("content://mms/addr");

    // 会话相关URI - 修正版
    private static final Uri CONVERSATIONS_URI = Uri.parse("content://sms/conversations");  // 正确获取threads的方式
    private static final Uri COMPLETE_CONVERSATIONS_URI = Uri.parse("content://mms-sms/complete-conversations");

    // 联系人地址URI
    private static final Uri CANONICAL_ADDRESSES_URI = Uri.parse("content://mms-sms/canonical-addresses");

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
        ContentResolver resolver = context.getContentResolver();

        Log.i(TAG, "========== 开始清理短信数据 ==========");

        try {
            // 步骤1: 记录清理前的状态
            SmsStats beforeStats = getCurrentStats(resolver);
            Log.i(TAG, "清理前状态: " + beforeStats);

            // 步骤2: 清理所有短信
            int smsDeleted = deleteAllSms(resolver);
            Log.d(TAG, "删除短信: " + smsDeleted + "条");

            // 步骤3: 清理所有彩信
            int mmsDeleted = deleteAllMms(resolver);
            Log.d(TAG, "删除彩信: " + mmsDeleted + "条");

            // 步骤4: 清理所有会话（threads）
            int threadsDeleted = deleteAllThreads(resolver);
            Log.d(TAG, "删除会话: " + threadsDeleted + "个");

            // 步骤5: 清理联系人地址（关键步骤）
            int addressesDeleted = deleteAllAddresses(resolver);
            Log.d(TAG, "删除联系人地址: " + addressesDeleted + "个");

            // 步骤6: 额外清理
            int extraDeleted = performExtraCleanup(resolver);

            // 步骤7: 记录清理后的状态
            SmsStats afterStats = getCurrentStats(resolver);
            Log.i(TAG, "清理后状态: " + afterStats);

            // 步骤8: 通知系统更新
            notifySmsChange(resolver);

            // 判断是否成功
            boolean success = afterStats.total == 0;
            Log.i(TAG, String.format("清理统计: 短信=%d, 彩信=%d, 会话=%d, 地址=%d, 其他=%d, 总计=%d",
                    smsDeleted, mmsDeleted, threadsDeleted, addressesDeleted, extraDeleted,
                    smsDeleted + mmsDeleted + threadsDeleted + addressesDeleted + extraDeleted));

            if (callback != null) {
                if (success) {
                    callback.onComplete(getStrategyName(), true);
                } else {
                    callback.onError(getStrategyName(), "清理不彻底");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "清理过程发生异常", e);
            if (callback != null) {
                callback.onError(getStrategyName(), e.getMessage());
            }
        }

        Log.i(TAG, "========== 短信清理结束 ==========");
    }

    /**
     * 删除所有短信 - 使用多层次策略
     */
    private int deleteAllSms(ContentResolver resolver) {
        int total = 0;

        // 方法1: 直接删除所有短信
        try {
            total += resolver.delete(SMS_URI, null, null);
        } catch (Exception e) {
            Log.w(TAG, "批量删除短信失败", e);
        }

        // 方法2: 如果方法1没删掉，分别删除各个文件夹
        if (total == 0) {
            Uri[] smsUris = {SMS_INBOX_URI, SMS_SENT_URI, SMS_DRAFT_URI,
                    SMS_OUTBOX_URI, SMS_FAILED_URI, SMS_QUEUED_URI};

            for (Uri uri : smsUris) {
                try {
                    total += resolver.delete(uri, null, null);
                } catch (Exception e) {
                    // 忽略单个失败
                }
            }
        }

        // 方法3: 强制删除
        if (total == 0) {
            try {
                total += resolver.delete(SMS_URI, "1=1", null);
            } catch (Exception e) {
                // 忽略
            }
        }

        return total;
    }

    /**
     * 删除所有彩信
     */
    private int deleteAllMms(ContentResolver resolver) {
        int total = 0;

        // 先删附件和地址
        try {
            total += resolver.delete(MMS_PART_URI, null, null);
            total += resolver.delete(MMS_ADDR_URI, null, null);
        } catch (Exception e) {
            Log.w(TAG, "删除彩信附件失败", e);
        }

        // 再删彩信本身
        try {
            total += resolver.delete(MMS_URI, null, null);
        } catch (Exception e) {
            Log.w(TAG, "删除彩信失败", e);
        }

        return total;
    }

    /**
     * 删除所有会话 - 修正版
     * 使用正确的CONVERSATIONS_URI
     */
    private int deleteAllThreads(ContentResolver resolver) {
        int total = 0;

        // 方法1: 先获取所有thread_id
        List<Long> threadIds = getAllThreadIds(resolver);

        if (!threadIds.isEmpty()) {
            Log.d(TAG, "找到 " + threadIds.size() + " 个会话需要删除");

            // 逐个删除会话
            for (Long threadId : threadIds) {
                try {
                    // 删除该会话的所有短信
                    int count = resolver.delete(SMS_URI,
                            "thread_id=?", new String[]{String.valueOf(threadId)});
                    total += count;
                } catch (Exception e) {
                    Log.w(TAG, "删除会话 " + threadId + " 的短信失败", e);
                }
            }
        }

        // 方法2: 尝试直接删除会话表（部分系统可能支持）
        try {
            total += resolver.delete(CONVERSATIONS_URI, null, null);
        } catch (Exception e) {
            // 忽略
        }

        return total;
    }

    /**
     * 获取所有thread_id
     */
    private List<Long> getAllThreadIds(ContentResolver resolver) {
        List<Long> threadIds = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = resolver.query(
                    CONVERSATIONS_URI,
                    new String[]{"thread_id"},
                    null, null, null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    threadIds.add(cursor.getLong(0));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "查询thread_id失败", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return threadIds;
    }

    /**
     * 删除所有联系人地址 - 关键方法
     * 采用多重策略确保删除成功
     */
    private int deleteAllAddresses(ContentResolver resolver) {
        int total = 0;
        int beforeCount = 0;

        // 查询删除前的数量
        try {
            beforeCount = queryCount(resolver, CANONICAL_ADDRESSES_URI);
            Log.d(TAG, "地址表删除前记录数: " + beforeCount);
        } catch (Exception e) {
            // 忽略
        }

        if (beforeCount == 0) {
            return 0;
        }

        // 方法1: 直接删除
        try {
            total += resolver.delete(CANONICAL_ADDRESSES_URI, null, null);
        } catch (Exception e) {
            Log.w(TAG, "直接删除地址失败", e);
        }

        // 方法2: 强制删除
        if (total == 0) {
            try {
                total += resolver.delete(CANONICAL_ADDRESSES_URI, "1=1", null);
            } catch (Exception e) {
                Log.w(TAG, "强制删除地址失败", e);
            }
        }

        // 方法3: 逐个删除（最彻底）
        if (total == 0 || (beforeCount - total) < beforeCount) {
            try {
                Cursor cursor = resolver.query(
                        CANONICAL_ADDRESSES_URI,
                        new String[]{"_id"},
                        null, null, null
                );

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        try {
                            int count = resolver.delete(
                                    CANONICAL_ADDRESSES_URI,
                                    "_id=?",
                                    new String[]{String.valueOf(id)}
                            );
                            total += count;
                        } catch (Exception e) {
                            // 忽略单个失败
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "逐个删除地址失败", e);
            }
        }

        // 最终验证
        int afterCount = queryCount(resolver, CANONICAL_ADDRESSES_URI);
        Log.d(TAG, String.format("地址表清理结果: 删除前=%d, 删除后=%d, 本次删除=%d",
                beforeCount, afterCount, total));

        return total;
    }

    /**
     * 额外清理 - 处理其他可能残留的数据
     */
    private int performExtraCleanup(ContentResolver resolver) {
        int total = 0;

        // 清理完整会话
        try {
            total += resolver.delete(COMPLETE_CONVERSATIONS_URI, null, null);
        } catch (Exception e) {
            // 忽略
        }

        // Android特定版本清理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Uri telephonyUri = Uri.parse("content://telephony/sms");
                total += resolver.delete(telephonyUri, null, null);
            } catch (Exception e) {
                // 忽略
            }
        }

        return total;
    }

    /**
     * 获取当前统计数据
     */
    private SmsStats getCurrentStats(ContentResolver resolver) {
        return new SmsStats(
                queryCount(resolver, SMS_URI),
                queryCount(resolver, MMS_URI),
                queryCount(resolver, CONVERSATIONS_URI),
                queryCount(resolver, CANONICAL_ADDRESSES_URI)
        );
    }

    /**
     * 查询记录数量
     */
    private int queryCount(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            return cursor != null ? cursor.getCount() : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 通知系统短信数据库已改变
     */
    private void notifySmsChange(ContentResolver resolver) {
        try {
            resolver.notifyChange(SMS_URI, null);
            resolver.notifyChange(MMS_URI, null);
            resolver.notifyChange(CONVERSATIONS_URI, null);
            resolver.notifyChange(CANONICAL_ADDRESSES_URI, null);
        } catch (Exception e) {
            Log.w(TAG, "发送通知失败", e);
        }
    }

    /**
     * 统计数据内部类
     */
    private static class SmsStats {
        final int smsCount;
        final int mmsCount;
        final int threadCount;
        final int addressCount;
        final int total;

        SmsStats(int sms, int mms, int threads, int addresses) {
            this.smsCount = sms;
            this.mmsCount = mms;
            this.threadCount = threads;
            this.addressCount = addresses;
            this.total = sms + mms + threads + addresses;
        }

        @Override
        public String toString() {
            return String.format("短信=%d, 彩信=%d, 会话=%d, 地址=%d, 总计=%d",
                    smsCount, mmsCount, threadCount, addressCount, total);
        }
    }
}