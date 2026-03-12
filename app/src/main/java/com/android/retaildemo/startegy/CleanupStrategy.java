package com.android.retaildemo.startegy;

import android.content.Context;

public interface CleanupStrategy {
    /**
     * 执行清理任务
     * @param callback 清理回调
     */
    void cleanup(Context context, CleanupCallback callback);

    /**
     * 获取策略优先级（用于排序和并行清理任务）
     */
    int getPriority();

    /**
     * 获取策略名称
     */
    String getStrategyName();
}
