package com.android.retaildemo.work;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class PhotoCleanup {
    private static final String TAG = "PhotoCleanup";

    /**
     * 在后台线程中清空相册
     */
    public static void clearAllPhotos(Context context) {
        if (!checkPermission(context)) {
            Log.e(TAG, "没有存储权限，无法清空相册");
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "开始清空相册");
                ContentResolver contentResolver = context.getContentResolver();
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {MediaStore.Images.Media._ID};

                Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                if (cursor == null) {
                    Log.e(TAG, "无法访问相册");
                    return;
                }

                int deletedCount = 0;
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri imageUri = ContentUris.withAppendedId(uri, id);

                    try {
                        int rowsDeleted = contentResolver.delete(imageUri, null, null);
                        if (rowsDeleted > 0) {
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "删除图片失败", e);
                    }
                }
                cursor.close();
                Log.d(TAG, "相册清空完成，删除了 " + deletedCount + " 张图片");

            } catch (SecurityException e) {
                Log.e(TAG, "权限不足: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "清空相册失败: " + e.getMessage());
            }
        }).start();
    }

    private static boolean checkPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
}
