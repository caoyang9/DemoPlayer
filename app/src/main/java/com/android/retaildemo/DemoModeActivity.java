package com.android.retaildemo;

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

import com.google.android.material.card.MaterialCardView;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14
public class DemoModeActivity extends AppCompatActivity {

    private static final String TAG = "DemoModeActivity";
    private static final String DEMO_MODE_ENABLED = "sysui_demo_allowed";

    private SwitchCompat switchDemoMode;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_main);

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
            // 读取当前演示模式状态
            int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            switchDemoMode.setChecked(demoModeEnabled == 1);

            Log.d(TAG, "当前演示模式状态: " + (demoModeEnabled == 1 ? "开启" : "关闭"));
        } catch (Exception e) {
            Log.e(TAG, "读取演示模式状态失败", e);
            Toast.makeText(this, "读取状态失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        switchDemoMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setDemoModeEnabled(isChecked);
            }
        });

        // 为占位功能项添加点击提示
        setupPlaceholderClickListeners();
    }

    private void setDemoModeEnabled(boolean enable) {
        try {
            // 需要 WRITE_SECURE_SETTINGS 权限
            int value = enable ? 1 : 0;
            boolean success = Settings.Global.putInt(getContentResolver(), DEMO_MODE_ENABLED, value);

            if (success) {
                String message = enable ? "演示模式已开启" : "演示模式已关闭";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.d(TAG, message);

                // 发送广播通知系统演示模式变化
                // sendDemoModeChangedBroadcast(enable);
            } else {
                Toast.makeText(this, "设置失败，请检查权限", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "缺少WRITE_SECURE_SETTINGS权限", e);
            Toast.makeText(this, "需要WRITE_SECURE_SETTINGS权限", Toast.LENGTH_LONG).show();
            // 恢复开关状态
            switchDemoMode.setOnCheckedChangeListener(null);
            switchDemoMode.setChecked(!enable);
            setupListeners();
        }
    }

    private void setupPlaceholderClickListeners() {
        // 为占位卡片添加点击提示
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

    /**
     * 可选：发送演示模式变化的广播
     * 系统UI可能监听此广播以立即更新演示模式状态
     */
    private void sendDemoModeChangedBroadcast(boolean enabled) {
        // 系统演示模式广播的Action
        String DEMO_MODE_BROADCAST = "com.android.systemui.demo";

        Bundle args = new Bundle();
        args.putString("command", enabled ? "enter" : "exit");

        // 需要android.permission.DUMP权限
        // 这里仅作为示例，实际使用时需要相应权限
        try {
            // 通过Service管理演示模式（需要系统权限）
            // 这只是一个示例，实际实现可能需要调用系统服务
            Log.d(TAG, "发送演示模式广播: " + (enabled ? "enter" : "exit"));
        } catch (SecurityException e) {
            Log.e(TAG, "发送广播缺少权限", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回页面时刷新状态（防止外部修改）
        loadCurrentDemoModeState();
    }
}
