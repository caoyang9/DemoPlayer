package com.android.retaildemo.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 清除/重置已连接的账户、网页浏览历史、短信/彩信/RCS 历史、通话记录和照片
 */
public class CleanupWorker extends Worker {
    private static final String TAG = "CleanupWorker";
    private CleanupManager mCleanupManager;

    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mCleanupManager = CleanupManager.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "开始执行清理任务");

        final Result[] workResult = {Result.success()};
        final CountDownLatch latch = new CountDownLatch(1);

        mCleanupManager.cleanupAll(new CleanupCallback() {
            @Override
            public void onComplete(String strategyName, boolean success) {
                Log.d(TAG, "清理任务完成: " + strategyName);
                latch.countDown();
            }

            @Override
            public void onError(String strategyName, String error) {
                Log.e(TAG, "清理任务失败: " + strategyName + ", 错误: " + error);
                workResult[0] = Result.retry();
                latch.countDown();
            }
        });

        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                Log.e(TAG, "清理任务超时");
                return Result.failure();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "清理任务被中断", e);
            return Result.failure();
        }

        return workResult[0];
    }
}
