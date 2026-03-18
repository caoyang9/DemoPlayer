package com.android.retaildemo.startegy;

import android.content.Context;
import android.util.Log;

import com.android.retaildemo.startegy.impl.AppDataCleanupStrategy;
import com.android.retaildemo.startegy.impl.PhotoCleanupStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CleanupManager {
    private static final String TAG = "CleanupManager";
    private final Context mContext;
    private static volatile CleanupManager instance;
    private final List<CleanupStrategy> mStrategies;
    private final ExecutorService mExecutorService;

    // 私有构造函数
    private CleanupManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.mStrategies = new ArrayList<>();

        // 创建线程池
        int corePoolSize = Runtime.getRuntime().availableProcessors() / 3;
        Log.d(TAG, "创建核心线程数： " + corePoolSize);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,  // 核心线程数
                corePoolSize,  // 最大线程数
                60L,       // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        executor.allowCoreThreadTimeOut(true);
        this.mExecutorService = executor;
        registerStrategies();
        Log.d(TAG, "CleanupManager 单例创建，线程池初始化");
    }

    // 获取单例
    public static CleanupManager getInstance(Context context) {
        if (instance == null) {
            Log.d(TAG, "CleanupManager == null");
            synchronized (CleanupManager.class) {
                if (instance == null) {
                    Log.d(TAG, "创建新的CleanupManager实例");
                    instance = new CleanupManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void registerStrategies() {
        // 按优先级注册（数值越小优先级越高）
//        mStrategies.add(new WebViewHistoryStrategy());      // 优先级 1
        mStrategies.add(new PhotoCleanupStrategy());        // 优先级 2
        mStrategies.add(new AppDataCleanupStrategy());
//        mStrategies.add(new CallLogCleanupStrategy());      // 优先级 3
//        mStrategies.add(new SmsCleanupStrategy());          // 优先级 4
//        mStrategies.add(new AccountCleanupStrategy());      // 优先级 5
        // 可以继续添加其他策略

        // 按优先级排序
        Collections.sort(mStrategies,
                (s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()));
    }

    /**
     * 执行所有清理任务（并行执行）
     */
    public void cleanupAll(CleanupCallback callback) {
        Log.d(TAG, "开始执行所有清理任务，策略数量: " + mStrategies.size());

        CountDownLatch latch = new CountDownLatch(mStrategies.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (CleanupStrategy strategy : mStrategies) {
            mExecutorService.submit(() -> {
                try {
                    strategy.cleanup(mContext, new CleanupCallback() {
                        @Override
                        public void onComplete(String strategyName, boolean success) {
                            Log.d(TAG, strategyName + " 清理完成: " + success);
                            latch.countDown();
                        }

                        @Override
                        public void onError(String strategyName, String error) {
                            Log.e(TAG, strategyName + " 清理失败: " + error);
                            hasError.set(true);
                            latch.countDown();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, strategy.getStrategyName() + " 执行异常", e);
                    hasError.set(true);
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成（可以设置超时）
        try {
            boolean completed = latch.await(5, TimeUnit.MINUTES);
            if (completed) {
                if (callback != null) {
                    callback.onComplete("CleanupManager", !hasError.get());
                }
            } else {
                if (callback != null) {
                    callback.onError("CleanupManager", "清理任务超时");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (callback != null) {
                callback.onError("CleanupManager", "清理任务被中断");
            }
        }
    }

    /**
     * 执行特定优先级的清理任务
     */
    public void cleanupByPriority(int priority, CleanupCallback callback) {
        for (CleanupStrategy strategy : mStrategies) {
            if (strategy.getPriority() == priority) {
                mExecutorService.submit(() ->
                        strategy.cleanup(mContext, callback));
                break;
            }
        }
    }

    /**
     * 关闭线程池
     */
    private void shutdown() {
        if (mExecutorService != null && !mExecutorService.isShutdown()) {
            mExecutorService.shutdown();
            try {
                if (!mExecutorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    mExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                mExecutorService.shutdownNow();
            }
        }
    }

    public static void shutdownInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
}
