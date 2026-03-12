package com.android.retaildemo.startegy;

public interface CleanupCallback {
    void onComplete(String strategyName, boolean success);
    void onError(String strategyName, String error);
}
