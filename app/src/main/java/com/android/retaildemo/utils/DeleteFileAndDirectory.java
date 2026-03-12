package com.android.retaildemo.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DeleteFileAndDirectory {
    private static final String TAG = "DeleteFileAndDirectory";
    private final Context mContext;

    public DeleteFileAndDirectory(Context context) {
        this.mContext = context.getApplicationContext();
    }

    /**
     * 删除指定路径的文件
     */
    public CleanupResult delete(String[] paths) {
        CleanupResult result = new CleanupResult();

        for (String path : paths) {
            try {
                int count = deletePath(path);
                result.addSuccess(path, count);
                Log.d(TAG, "清理路径成功: " + path + ", 删除文件数: " + count);
            } catch (Exception e) {
                Log.e(TAG, "清理路径失败: " + path, e);
                result.addFailure(path, e.getMessage());
            }
        }

        return result;
    }

    private int deletePath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return 0;
        }

        int deletedCount = deleteRecursive(file);

        // 通知媒体库更新
        notifyMediaScanner(path);

        return deletedCount;
    }

    private int deleteRecursive(File file) {
        int deletedCount = 0;

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deletedCount += deleteRecursive(child);
                }
            }

            // 是否删除空目录
            // if (file.delete()) deletedCount++;

        } else if (file.isFile()) {
            if (file.delete()) {
                deletedCount++;
            }
        }

        return deletedCount;
    }

    private void notifyMediaScanner(String path) {
        try {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(new File(path)));
            mContext.sendBroadcast(mediaScanIntent);
        } catch (Exception e) {
            Log.e(TAG, "通知媒体库失败", e);
        }
    }

    /**
     * 清理结果封装类
     */
    public static class CleanupResult {
        private final Map<String, Integer> successPaths = new HashMap<>();
        private final Map<String, String> failedPaths = new HashMap<>();

        public void addSuccess(String path, int count) {
            successPaths.put(path, count);
        }

        public void addFailure(String path, String error) {
            failedPaths.put(path, error);
        }

        public boolean isCompleteSuccess() {
            return failedPaths.isEmpty();
        }

        public int getTotalDeletedCount() {
            return successPaths.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
