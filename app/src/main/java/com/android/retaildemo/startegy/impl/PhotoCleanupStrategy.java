package com.android.retaildemo.startegy.impl;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.android.retaildemo.startegy.CleanupCallback;
import com.android.retaildemo.startegy.CleanupStrategy;
import com.android.retaildemo.utils.DeleteFileAndDirectory;

public class PhotoCleanupStrategy implements CleanupStrategy {
    private static final String TAG = "PhotoCleanupStrategy";

    static final String CAMERA_PATH = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Camera/";
    static final String SCREENSHOTS_PATH = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES).getAbsolutePath();

    @Override
    public void cleanup(Context context, CleanupCallback callback) {
        try {
            DeleteFileAndDirectory deleter = new DeleteFileAndDirectory(context);
            deleter.delete(new String[] {CAMERA_PATH, SCREENSHOTS_PATH});

            if (callback != null) {
                callback.onComplete(getStrategyName(), true);
            }
        } catch (Exception e) {
            Log.e(TAG, "照片清理失败", e);
            if (callback != null) {
                callback.onError(getStrategyName(), e.getMessage());
            }
        }
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getStrategyName() {
        return null;
    }
}
