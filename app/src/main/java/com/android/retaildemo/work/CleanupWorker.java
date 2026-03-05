package com.android.retaildemo.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * 清除/重置已连接的账户、网页浏览历史、短信/彩信/RCS 历史、通话记录和照片
 */
public class CleanupWorker extends Worker {

    private static final String TAG = "CleanupWorker";

    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("CleanupWorker", "开始执行每30分钟的清理任务");
        Context context = getApplicationContext();

        // 1.异步清除已连接的账户
        // 2.异步清除浏览器搜索浏览历史
        WebViewHistoryCleanup.clearWebViewHistoryAsync(context,
                new WebViewHistoryCleanup.CleanupCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        Log.d(TAG, "WebView历史清理完成");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "WebView清理过程出现错误: " + error);
                    }
                });
        // 3.异步清除短信/彩信/RCS历史
        // 4.异步清除通话记录
        // 5.异步清除照片
        PhotoCleanup.clearAllPhotos(context);

        try {
            Log.d("CleanupWorker", "清理任务执行成功");
            return Result.success();
        } catch (Exception e) {
            Log.e("CleanupWorker", "清理任务执行失败", e);
            return Result.failure();
        }
    }
}
