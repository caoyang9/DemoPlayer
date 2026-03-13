package com.android.retaildemo;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.android.retaildemo.utils.LockScreenManager;
import com.android.retaildemo.utils.PasswordDialog;
import com.android.retaildemo.utils.PasswordManager;
import com.google.android.material.card.MaterialCardView;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class DemoModeActivity extends AppCompatActivity {

    private static final String TAG = "DemoModeActivity";
    private static final String DEMO_MODE_ENABLED = "sysui_demo_allowed";

    private SwitchCompat switchDemoMode;
    private Toolbar toolbar;
    private boolean isUserAction = false; // 是否是用户操作

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_main);

        PasswordManager.getInstance(this);

        initViews();
        setupToolbar();
        loadCurrentDemoModeState();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchDemoMode = findViewById(R.id.switch_demo_mode);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void loadCurrentDemoModeState() {
        try {
            int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            // 临时移除监听器，避免触发事件
            switchDemoMode.setOnCheckedChangeListener(null);
            switchDemoMode.setChecked(demoModeEnabled == 1);
            // 重新设置监听器
            setupListeners();

            Log.d(TAG, "当前演示模式状态: " + (demoModeEnabled == 1 ? "开启" : "关闭"));
        } catch (Exception e) {
            Log.e(TAG, "读取演示模式状态失败", e);
        }
    }

    private void setupListeners() {
        switchDemoMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isPressed()) {
                    isUserAction = true;
                    showPasswordVerificationDialog(isChecked);
                }
            }
        });

        setupPlaceholderClickListeners();
    }

    private void showPasswordVerificationDialog(boolean enable) {
        String title = enable ? "开启演示模式" : "关闭演示模式";
        PasswordDialog.showInputDialog(this, title, new PasswordDialog.PasswordCallback() {
            @Override
            public void onSuccess() {
                // 密码验证成功
                isUserAction = false;
                setDemoModeEnabled(enable);
            }

            @Override
            public void onCancel() {
                // 用户取消或密码错误
                isUserAction = false;
                restoreSwitchState();
            }
        });
    }

    private void setDemoModeEnabled(boolean enable) {
        try {
            int value = enable ? 1 : 0;
            boolean success = Settings.Global.putInt(getContentResolver(), DEMO_MODE_ENABLED, value);

            if (success) {
                int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
                if (demoModeEnabled == 1) {
                    startDemoPlayerActivity();  // 立即开启
                    // 禁用密码锁定和恢复出厂设置
                    Log.d(TAG, "演示模式开启用户权限控制");
                    disableScreenLockAndFactoryReset();
                }
                if (demoModeEnabled == 0) {
                    // 退出前台MonitorService服务
                    Log.d(TAG, "退出MonitorService服务");
                    Intent intent = new Intent(this, UserActivityMonitorService.class);
                    stopService(intent);
                    // 解除演示模式权限控制
                    Log.d(TAG, "解除演示模式权限控制");
                    restoreScreenLockAndFactoryReset();
                }
                String message = enable ? "正在播放演示内容" : "演示模式已关闭";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.d(TAG, message);
            } else {
                Toast.makeText(this, "设置失败，请检查权限", Toast.LENGTH_SHORT).show();
                restoreSwitchState();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "缺少权限", e);
            Toast.makeText(this, "需要WRITE_SECURE_SETTINGS权限", Toast.LENGTH_LONG).show();
            restoreSwitchState();
        } catch (Exception e) {
            Log.e(TAG, "设置失败", e);
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show();
            restoreSwitchState();
        }
    }

    private void disableScreenLockAndFactoryReset() {
        try {
            LockScreenManager lockScreenManager = new LockScreenManager(this);
            boolean deviceOwner = lockScreenManager.isDeviceOwner();
            Log.d(TAG, "是否是设备所有者:" + deviceOwner);
            // 禁止用户设定任何种类的密码
            lockScreenManager.ensureNoLockScreen();
            // 禁止用户恢复出厂设置
            lockScreenManager.disableFactoryReset();
        } catch (Exception e) {
            Log.e(TAG, "disableScreenLockAndFactoryReset() failed", e);
        }
    }

    private void restoreSwitchState() {
        try {
            int currentValue = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            // 临时移除监听器，避免触发事件
            switchDemoMode.setOnCheckedChangeListener(null);
            switchDemoMode.setChecked(currentValue == 1);
            setupListeners();
        } catch (Exception e) {
            Log.e(TAG, "恢复开关状态失败", e);
        }
    }

    private void setupPlaceholderClickListeners() {
        int[] placeholderIds = {R.id.placeholder_1, R.id.placeholder_2, R.id.placeholder_3};

        for (int id : placeholderIds) {
            MaterialCardView card = findViewById(id);
            if (card != null) {
                card.setOnClickListener(v ->
                        Toast.makeText(DemoModeActivity.this, "功能待开发", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    private void startDemoPlayerActivity() {
        try {
            Intent intent = new Intent(this, DemoPlayer.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            Log.e("IdleMonitor", "启动失败", e);
        }
    }

    private void restoreScreenLockAndFactoryReset() {
        try {
            LockScreenManager lockScreenManager = new LockScreenManager(this);
            // 接触演示模式用户权限控制
            lockScreenManager.restoreAllSettings();
            Log.d("DemoMode", "已退出演示模式，功能已恢复");
        } catch (Exception e) {
            Log.e("DemoMode", "退出演示模式失败", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isUserAction) {
            loadCurrentDemoModeState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isUserAction = false;
    }
}