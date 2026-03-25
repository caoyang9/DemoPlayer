package com.android.retaildemo;

import android.app.Application;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.WorkManager;

public class WorkManagerInitApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // 手动初始化 WorkManager
        try {
            Configuration configuration = new Configuration.Builder()
                    .setMinimumLoggingLevel(Log.DEBUG)
                    .build();

            WorkManager.initialize(this, configuration);
            Log.d(TAG, "WorkManager initialized successfully");
        } catch (IllegalStateException e) {
            // 已经初始化过，忽略
            Log.d(TAG, "WorkManager already initialized");
        }
    }
}
