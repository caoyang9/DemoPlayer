package com.android.retaildemo;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.android.retaildemo.startegy.CleanupManager;
import com.android.retaildemo.time.ConfigManager;
import com.android.retaildemo.time.TimeConfig;
import com.android.retaildemo.time.TimeService;
import com.android.retaildemo.utils.LockScreenManager;
import com.android.retaildemo.utils.PasswordDialog;
import com.android.retaildemo.utils.PasswordManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class DemoModeActivity extends AppCompatActivity {

    private static final String TAG = "DemoModeActivity";
    private static final String DEMO_MODE_ENABLED = "sysui_demo_allowed";
    private SwitchCompat switchDemoMode;
    private SwitchCompat switchTimeControl;
    private TextView tvTimeRange;
    private LinearLayout weekConfigContainer;
    private LinearLayout weekDaysContainer;

    private Toolbar toolbar;
    private boolean isUserAction = false; // 是否是用户操作
    private ConfigManager configManager;
    private TimeConfig currentConfig;
    private List<Integer> selectedWeekDays = new ArrayList<>();
    private final String[] weekNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private Handler handler = new Handler();
    private BroadcastReceiver stopReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_main);

        PasswordManager.getInstance(this);
        configManager = new ConfigManager(this);
        currentConfig = configManager.getConfig();
        selectedWeekDays = new ArrayList<>(currentConfig.getWeekDays());
        setupStopReceiver();
        startMonitorService();

        initViews();
        setupToolbar();
        loadCurrentDemoModeState();
        loadTimeConfig();
        setupListeners();
        setupWeekDayButtons();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchDemoMode = findViewById(R.id.switch_demo_mode);
        tvTimeRange = findViewById(R.id.tv_time_range);
        switchTimeControl = findViewById(R.id.switch_time_control);
        weekConfigContainer = findViewById(R.id.week_config_container);
        weekDaysContainer = findViewById(R.id.week_days_container);
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, UserActivityMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "启动UserActivityMonitorService");
            startForegroundService(intent);
        } else {
            startService(intent);
        }
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

    private void loadTimeConfig() {
        tvTimeRange.setText(currentConfig.getStartTime() + " - " + currentConfig.getEndTime());
        switchTimeControl.setChecked(currentConfig.isEnabled());
        weekConfigContainer.setVisibility(currentConfig.isEnabled() ? View.VISIBLE : View.GONE);
        updateWeekButtons();
    }

    private void setupWeekDayButtons() {
        weekDaysContainer.removeAllViews();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(4, 0, 4, 0);

        for (int i = 0; i < weekNames.length; i++) {
            final int day = i + 1;
            Button dayButton = new Button(this);
            dayButton.setText(weekNames[i]);
            dayButton.setTag(day);
            dayButton.setLayoutParams(params);
            dayButton.setAllCaps(false);
            dayButton.setPadding(8, 12, 8, 12);
            dayButton.setTextSize(12);

            updateButtonStyle(dayButton, selectedWeekDays.contains(day));

            dayButton.setOnClickListener(v -> {
                int clickedDay = (int) v.getTag();
                if (selectedWeekDays.contains(clickedDay)) {
                    selectedWeekDays.remove((Integer) clickedDay);
                } else {
                    selectedWeekDays.add(clickedDay);
                }
                updateButtonStyle((Button) v, selectedWeekDays.contains(clickedDay));
                currentConfig.setWeekDays(new ArrayList<>(selectedWeekDays));
                configManager.saveConfig(currentConfig);

                if (switchTimeControl.isChecked()) {
                    restartTimeService();
                }
            });

            weekDaysContainer.addView(dayButton);
        }
    }

    private void updateButtonStyle(Button button, boolean selected) {
        if (selected) {
            button.setBackgroundColor(getColor(android.R.color.holo_blue_light));
            button.setTextColor(getColor(android.R.color.white));
        } else {
            button.setBackgroundColor(getColor(android.R.color.darker_gray));
            button.setTextColor(getColor(android.R.color.black));
        }
    }

    private void updateWeekButtons() {
        for (int i = 0; i < weekDaysContainer.getChildCount(); i++) {
            Button button = (Button) weekDaysContainer.getChildAt(i);
            int day = i + 1;
            updateButtonStyle(button, selectedWeekDays.contains(day));
        }
    }


    private void setupListeners() {
        setupDemoModeListener();
        setupTimeConfigListeners();
    }

    private void setupDemoModeListener() {
        switchDemoMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isPressed()) {
                    isUserAction = true;
                    showPasswordVerificationDialog(isChecked);
                }
            }
        });
    }

    private void setupTimeConfigListeners() {
        // 时间范围点击监听器：配置时间段和星期
        tvTimeRange.setOnClickListener(v -> showTimeConfigDialog());
        // 时钟配置管理开关监听器：启用/停用时间范围配置
        switchTimeControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            weekConfigContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            currentConfig.setEnabled(isChecked);
            configManager.saveConfig(currentConfig);

            if (isChecked) {
                // 启动TimeService用于定时启停演示应用播放
                startTimeService();
                // 判断当前是否处于生效时间段内
                if (isInTimeRange()) {
                    Log.d(TAG, "时钟管理已开启，当前时间：演示播放期间");
                    showPlayPrompt();
                } else {
                    Log.d(TAG, "时钟管理已开启，当前时间：非演示播放期间");
                }
            } else {
                stopTimeService();
            }
        });
    }

    private void showTimeConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_time_config, null);

        // 初始化时间选择器
        TimePicker timePickerStart = view.findViewById(R.id.time_picker_start);
        TimePicker timePickerEnd = view.findViewById(R.id.time_picker_end);
        LinearLayout weekLayout = view.findViewById(R.id.week_days_container);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnApply = view.findViewById(R.id.btn_apply);

        // 设置24小时制
        timePickerStart.setIs24HourView(true);
        timePickerEnd.setIs24HourView(true);

        // 设置当前时间
        String[] startParts = currentConfig.getStartTime().split(":");
        String[] endParts = currentConfig.getEndTime().split(":");

        // 兼容不同API版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePickerStart.setHour(Integer.parseInt(startParts[0]));
            timePickerStart.setMinute(Integer.parseInt(startParts[1]));
            timePickerEnd.setHour(Integer.parseInt(endParts[0]));
            timePickerEnd.setMinute(Integer.parseInt(endParts[1]));
        } else {
            timePickerStart.setCurrentHour(Integer.parseInt(startParts[0]));
            timePickerStart.setCurrentMinute(Integer.parseInt(startParts[1]));
            timePickerEnd.setCurrentHour(Integer.parseInt(endParts[0]));
            timePickerEnd.setCurrentMinute(Integer.parseInt(endParts[1]));
        }

        // 设置星期复选框
        List<Integer> tempDays = new ArrayList<>(selectedWeekDays);
        List<CheckBox> weekCheckBoxes = setupWeekCheckBoxes(weekLayout, tempDays);

        // 创建对话框
        AlertDialog dialog = builder.setView(view)
                .setCancelable(false)
                .create();

        // 取消按钮
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // 应用按钮
        btnApply.setOnClickListener(v -> {
            // 获取开始时间
            int startHour, startMinute, endHour, endMinute;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startHour = timePickerStart.getHour();
                startMinute = timePickerStart.getMinute();
                endHour = timePickerEnd.getHour();
                endMinute = timePickerEnd.getMinute();
            } else {
                startHour = timePickerStart.getCurrentHour();
                startMinute = timePickerStart.getCurrentMinute();
                endHour = timePickerEnd.getCurrentHour();
                endMinute = timePickerEnd.getCurrentMinute();
            }

            String startTime = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute);
            String endTime = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute);

            // 更新配置
            currentConfig.setStartTime(startTime);
            currentConfig.setEndTime(endTime);
            currentConfig.setWeekDays(new ArrayList<>(tempDays));
            selectedWeekDays = new ArrayList<>(tempDays);

            // 更新UI
            tvTimeRange.setText(startTime + " - " + endTime);
            updateWeekButtons();
            configManager.saveConfig(currentConfig);

            // 重启服务
            if (switchTimeControl.isChecked()) {
                Log.d(TAG, "时钟配置修改并应用，重启TimeService");
                restartTimeService();
            }

            int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            if (demoModeEnabled == 1) {
                if (switchTimeControl.isChecked() && isInTimeRange()) {
                    startDemoPlayerActivity();
                }
            }

            dialog.dismiss();
        });
        dialog.show();
    }

    private List<CheckBox> setupWeekCheckBoxes(LinearLayout container, List<Integer> tempDays) {
        List<CheckBox> checkBoxes = new ArrayList<>();
        container.removeAllViews();

        String[] weekNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(4, 0, 4, 0);

        for (int i = 0; i < weekNames.length; i++) {
            final int day = i + 1;
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(weekNames[i]);
            checkBox.setTag(day);
            checkBox.setLayoutParams(params);
            checkBox.setButtonTintList(ColorStateList.valueOf(getColor(android.R.color.holo_blue_light)));
            checkBox.setTextColor(getColor(android.R.color.black));
            checkBox.setGravity(android.view.Gravity.CENTER);

            // 设置选中状态
            checkBox.setChecked(tempDays.contains(day));

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int selectedDay = (int) buttonView.getTag();
                if (isChecked) {
                    if (!tempDays.contains(selectedDay)) {
                        tempDays.add(selectedDay);
                    }
                } else {
                    tempDays.remove((Integer) selectedDay);
                }
            });

            container.addView(checkBox);
            checkBoxes.add(checkBox);
        }

        return checkBoxes;
    }

    private boolean isInTimeRange() {
        Calendar now = Calendar.getInstance();
        String[] start = currentConfig.getStartTime().split(":");
        String[] end = currentConfig.getEndTime().split(":");

        int currentWeekDay = getCurrentWeekDay();
        if (!selectedWeekDays.contains(currentWeekDay)) {
            // 日期星期不处于生效时间段内
            return false;
        }

        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMinutes = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);

        if (endMinutes < startMinutes) {
            // 时间段跨天：12:00(T) - 06:00(T+1)
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
        } else {
            // 时间段处于当天：
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
        }
    }

    private int getCurrentWeekDay() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 7 : day - 1;
    }

    private void showPlayPrompt() {
        Toast.makeText(this, "即将播放演示内容", Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> {
            if (switchTimeControl.isChecked() && isInTimeRange()) {
                startDemoPlayerActivity();
            }
        }, 5000);
    }

    private void startTimeService() {
        Intent intent = new Intent(this, TimeService.class);
        startForegroundService(intent);
        Log.d(TAG, "TimeService已启动");
    }

    private void stopTimeService() {
        Intent intent = new Intent(this, TimeService.class);
        intent.setAction("STOP");
        startForegroundService(intent);
        Log.d(TAG, "TimeService已停止");
    }

    private void restartTimeService() {
        stopTimeService();
        startTimeService();
        Log.d(TAG, "TimeService已重启");
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
//                    startDemoPlayerActivity();  // 立即开启
                    if (switchTimeControl.isChecked() && isInTimeRange()) {
                        startDemoPlayerActivity();
                    }
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
                    // 清理定时任务线程池资源
                    CleanupManager.shutdownInstance();
                    stopTimeService(); // 关闭演示模式时也停止时间服务
                }
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

    private void setupStopReceiver() {
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "收到停止广播，准备关闭");

                // 添加调试信息
                Log.d(TAG, "Intent action: " + intent.getAction());
                Log.d(TAG, "Intent flags: " + intent.getFlags());

                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        finish();
                    }
                });
            }
        };

        IntentFilter filter = new IntentFilter("STOP_DEMO_MODE_ACTIVITY");
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY); // 提高优先级

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_EXPORTED); // 临时改为 EXPORTED 测试
        } else {
            registerReceiver(stopReceiver, filter);
        }

        Log.d(TAG, "DemoPlayer停止广播接收器已注册");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}