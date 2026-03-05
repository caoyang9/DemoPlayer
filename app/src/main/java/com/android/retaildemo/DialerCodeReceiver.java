package com.android.retaildemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * 拨号盘暗码广播接收器
 * 在拨号盘中输入 *#*#9876#*#* 启动RetailDemo
 */
public class DialerCodeReceiver extends BroadcastReceiver {
    private static final String TAG = "DialerCodeReceiver";
    private static final String DIALER_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    private static final String DialerCode = "9876";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive()");
        if (intent != null && DIALER_CODE_ACTION.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null && uri.getHost() != null && uri.getHost().equals(DialerCode)){
                Log.d(TAG, "收到暗码广播：" + uri.getHost() + "，启动RetailDemo");
                // 启动 DemoPlayer
                Intent i = new Intent(context, DemoPlayer.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        }
    }
}
