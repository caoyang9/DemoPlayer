package com.android.retaildemo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.android.retaildemo.startegy.CleanupManager;
import com.android.retaildemo.time.ConfigManager;
import com.android.retaildemo.time.TimeConfig;
import com.android.retaildemo.time.TimeService;
import com.android.retaildemo.utils.LockScreenManager;
import com.android.retaildemo.utils.MyDeviceAdminReceiver;
import com.android.retaildemo.utils.PasswordDialog;
import com.android.retaildemo.utils.PasswordManager;
import com.android.retaildemo.work.CleanupWorker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
    private SwitchCompat switchCleanInterval;
    private TextView tvCleanInterval;
    private static final String[] INTERVAL_OPTIONS = {
            "30分钟", "1小时", "2小时", "4小时", "8小时", "24小时"
    };
    private static final int[] INTERVAL_MINUTES = {30, 60, 120, 240, 480, 1440};
    private String currentInterval = "30分钟";
    private boolean isSwitchChecked = false;  // 周期清理开关
    private static final String SETTING_CLEAN_ENABLED = "clean_interval_enabled";
    private static final String SETTING_CLEAN_INTERVAL = "clean_interval_minutes";
    private static final String CLEANUP_WORK_NAME = "periodic_cleanup_work";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 100; // 请求码
    private static final int REQUEST_CODE_CALL_LOG = 101;
    private static final int REQUEST_CODE_SMS = 102;
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
            int demoModeEnabled = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            if (demoModeEnabled == 1 && isChecked) {
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
                    // 检查并申请设备管理员权限
                    checkAndRequestAdminPermission();
                    // 禁用密码锁定和恢复出厂设置
                    Log.d(TAG, "演示模式开启用户权限控制");

                    // TODO 禁用密码设置 禁止恢复出厂设置
                    // 设备管理员权限无法实现，需要设备所有者权限
//                    disableScreenLockAndFactoryReset();
                }
                if (demoModeEnabled == 0) {
                    if (switchCleanInterval != null) {
                        switchCleanInterval.setChecked(false);
                        updateIntervalEnabledState();
                        Settings.Global.putInt(getContentResolver(), SETTING_CLEAN_ENABLED, 0);
                        // 停止周期清理任务
                        stopPeriodicClean();
                        Log.d(TAG, "演示模式关闭，自动关闭周期清理");
                    }

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

                    // 停止周期任务
                    cancelPeriodicCleanup();
                    // 释放演示应用的设备所有者权限
                    releaseDeviceAdminPermission();
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

    public void releaseDeviceAdminPermission() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

        try {
            dpm.removeActiveAdmin(adminComponent);
            Log.i("DemoMode", "设备管理员权限已成功移除");
        } catch (SecurityException e) {
            Log.e("DemoMode", "移除失败: " + e.getMessage());
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

    private void initCleanInterval() {
        switchCleanInterval = findViewById(R.id.switch_clean_interval);
        tvCleanInterval = findViewById(R.id.tv_clean_interval);

        // 从Settings.Global读取保存的值
        loadSettings();

        // 设置时间选择器点击事件（始终可以点击选择）
        tvCleanInterval.setOnClickListener(v -> {
            showIntervalDialog();
        });

        // 设置开关监听
        switchCleanInterval.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 开关状态变化时，写入Settings.Global
            Settings.Global.putInt(getContentResolver(), SETTING_CLEAN_ENABLED, isChecked ? 1 : 0);
            int i = isChecked ? 1 : 0;
            Log.d(TAG, "开关状态变化，新值：" + i);

            // 更新UI效果
            updateIntervalEnabledState();

            // 开关状态变化时，触发周期清理任务的状态更新
            onCleanIntervalChanged();
        });

        // 初始化状态
        updateIntervalEnabledState();
    }

    private void loadSettings() {
        try {
            // 读取开关状态，默认关闭(0)
            int enabled = Settings.Global.getInt(getContentResolver(), SETTING_CLEAN_ENABLED, 0);
            switchCleanInterval.setChecked(enabled == 1);

            // 读取时间值，默认30分钟
            int minutes = Settings.Global.getInt(getContentResolver(), SETTING_CLEAN_INTERVAL, 30);

            // 根据分钟值找到对应的显示文本
            currentInterval = minutesToDisplayText(minutes);
            tvCleanInterval.setText(currentInterval);

        } catch (Exception e) {
            e.printStackTrace();
            // 发生异常时使用默认值
            switchCleanInterval.setChecked(false);
            currentInterval = "30分钟";
            tvCleanInterval.setText(currentInterval);
        }
    }

    private String minutesToDisplayText(int minutes) {
        for (int i = 0; i < INTERVAL_MINUTES.length; i++) {
            if (INTERVAL_MINUTES[i] == minutes) {
                return INTERVAL_OPTIONS[i];
            }
        }
        return "30分钟"; // 默认
    }

    private void updateIntervalEnabledState() {
        // 根据开关状态调整时间选择器的视觉效果
        float alpha = switchCleanInterval.isChecked() ? 1.0f : 0.5f;
        tvCleanInterval.setAlpha(alpha);
    }

    private void showIntervalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择清理周期");

        // 找到当前选中的项
        int checkedItem = -1;
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i].equals(currentInterval)) {
                checkedItem = i;
                break;
            }
        }

        builder.setSingleChoiceItems(INTERVAL_OPTIONS, checkedItem, (dialog, which) -> {
            currentInterval = INTERVAL_OPTIONS[which];
            tvCleanInterval.setText(currentInterval);

            // 将选中的时间值写入Settings.Global
            int minutes = INTERVAL_MINUTES[which];
            Settings.Global.putInt(getContentResolver(), SETTING_CLEAN_INTERVAL, minutes);

            // 如果开关是开启状态，立即生效
            if (switchCleanInterval.isChecked()) {
                onCleanIntervalChanged();
            }

            dialog.dismiss();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void onCleanIntervalChanged() {
        if (switchCleanInterval.isChecked()) {
            // 判断演示模式是否开启
            int currentValue = Settings.Global.getInt(getContentResolver(), DEMO_MODE_ENABLED, 0);
            if (currentValue == 0) {
                Log.d(TAG, "演示模式未开启");
                switchCleanInterval.setChecked(false);
                updateIntervalEnabledState();
                new AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("演示模式未开启，请先启用演示模式后再使用此功能。")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setCancelable(true)
                        .show();
                return;
            }
            // 开关开启，获取选中的时间值
            int minutes = getSelectedMinutes();
            // 执行周期性清理任务
            startPeriodicClean(minutes);
        } else {
            // 开关关闭，停止周期性清理
            stopPeriodicClean();
        }
    }

    private int getSelectedMinutes() {
        for (int i = 0; i < INTERVAL_OPTIONS.length; i++) {
            if (INTERVAL_OPTIONS[i].equals(currentInterval)) {
                return INTERVAL_MINUTES[i];
            }
        }
        return 30; // 默认30分钟
    }

    private void startPeriodicClean(int minutes) {
        Log.d(TAG, "取消旧的周期清理任务");
        cancelPeriodicCleanup();
        System.out.println("启动周期清理间隔：" + minutes + "分钟的任务");
        schedulePeriodicCleanup(minutes);
    }

    private void stopPeriodicClean() {
        System.out.println("停止周期清理任务");
        cancelPeriodicCleanup();
    }

    private void schedulePeriodicCleanup(int minutes) {
        LockScreenManager lockScreenManager = new LockScreenManager(this);
        boolean adminActive = lockScreenManager.isAdminActive();
        if (!adminActive) {
            Log.d(TAG, "应用需要获取设备管理员权限，以启动定时清理任务");
            return;
        }
        if (minutes < 15) {
            Log.w(TAG, "周期任务最小间隔为15分钟，输入的 " + minutes + " 分钟将被调整为15分钟");
            minutes = 15;
        }

        // 创建周期任务
        PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(
                CleanupWorker.class,
                minutes, TimeUnit.MINUTES)
                .addTag("cleanup-task")
                .build();
        // 使用WorkManager调度周期任务
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniquePeriodicWork(
                CLEANUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
        );
        Log.d(TAG, "周期清理任务已调度，间隔: " + minutes + " 分钟");
    }

    private void cancelPeriodicCleanup() {
        WorkManager workManager = WorkManager.getInstance(this);
        // 通过唯一任务名称取消任务
        workManager.cancelUniqueWork(CLEANUP_WORK_NAME);
        Log.d(TAG, "已取消周期性清理任务");
    }

    private void checkAndRequestAdminPermission() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);
        boolean isAdminActive = dpm.isAdminActive(adminComponent);
        Log.d(TAG, "设备管理员权限状态: " + (isAdminActive ? "已激活" : "未激活"));

        if (!isAdminActive) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "授予该权限使以清理设备中的数据。");
            // 启动系统授权界面
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
        } else {
            checkAndRequestCallLogPermission();
        }
    }

    private void checkAndRequestCallLogPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int readPermission = checkSelfPermission(Manifest.permission.READ_CALL_LOG);
            int writePermission = checkSelfPermission(Manifest.permission.WRITE_CALL_LOG);
            int readContacts = checkSelfPermission(Manifest.permission.READ_CONTACTS);
            int writeContacts = checkSelfPermission(Manifest.permission.WRITE_CONTACTS);
            if (readPermission != PackageManager.PERMISSION_GRANTED ||
                    writePermission != PackageManager.PERMISSION_GRANTED ||
                    readContacts != PackageManager.PERMISSION_GRANTED ||
                    writeContacts != PackageManager.PERMISSION_GRANTED
            ) {

                Log.d(TAG, "申请通话记录权限");
                if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                    // 用户之前拒绝过，显示说明
                    new AlertDialog.Builder(this)
                            .setTitle("需要通话记录权限")
                            .setMessage("为了清理通话记录，需要您授予通话记录相关权限")
                            .setPositiveButton("去授权", (dialog, which) -> {
                                requestPermissions(new String[]{
                                        Manifest.permission.READ_CALL_LOG,
                                        Manifest.permission.WRITE_CALL_LOG,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.WRITE_CONTACTS
                                }, REQUEST_CODE_CALL_LOG);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                } else {
                    // 直接申请
                    requestPermissions(new String[]{
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.WRITE_CALL_LOG,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                    }, REQUEST_CODE_CALL_LOG);
                }
            } else {
                Log.d(TAG, "已有通话权限");
                if (switchTimeControl.isChecked() && isInTimeRange()) {
                    startDemoPlayerActivity();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "用户已授予应用设备管理员权限");
                checkAndRequestCallLogPermission();
            } else {
                Log.w(TAG, "用户取消授予应用设备管理员权限");
                // 用户拒绝激活，可以给出提示，或重试
                Toast.makeText(this, "需要设备管理员权限才能自动清理数据", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isUserAction) {
            loadCurrentDemoModeState();
        }
        // 初始化周期清理任务时间选择组件
        initCleanInterval();
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