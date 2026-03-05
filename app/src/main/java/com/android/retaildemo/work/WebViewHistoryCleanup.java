package com.android.retaildemo.work;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import java.io.DataOutputStream;
import java.io.IOException;

public class WebViewHistoryCleanup {
    private static final String TAG = "WebViewCleanup";

    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 异步清除 WebView 所有历史记录
     * 确保在主线程执行 WebView 操作
     */
    public static void clearWebViewHistoryAsync(Context context, CleanupCallback callback) {
        // 切换到主线程执行 WebView 操作
        mainHandler.post(() -> {
            try {
                Log.d(TAG, "在主线程开始清理 WebView 历史记录");

                // 清理Chrome浏览器历史记录


                // 创建一个临时的 WebView 实例
                WebView webView = new WebView(context);
                // 清除浏览历史
                webView.clearHistory();
                // 清除缓存
                webView.clearCache(true);
                // 清除表单数据
                webView.clearFormData();
                // 清除 SSL 偏好设置
                webView.clearSslPreferences();
                // 获取 WebView 数据库并清除
                WebViewDatabase webViewDb = WebViewDatabase.getInstance(context);
                if (webViewDb != null) {
                    webViewDb.clearUsernamePassword();
                    webViewDb.clearHttpAuthUsernamePassword();
                    webViewDb.clearFormData();
                }
                // 清除 Web Storage
                WebStorage.getInstance().deleteAllData();
                // 销毁 WebView 释放资源
                webView.destroy();

                Log.d(TAG, "WebView 历史记录已清除");

                // 通过回调通知结果
                if (callback != null) {
                    callback.onComplete(true);
                }

            } catch (Exception e) {
                Log.e(TAG, "清除 WebView 历史失败", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * 清理回调接口
     */
    public interface CleanupCallback {
        void onComplete(boolean success);
        void onError(String error);
    }
}
