package com.android.retaildemo.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class PasswordDialog {

    public interface PasswordCallback {
        void onSuccess();
        void onCancel();
    }

    public static void showInputDialog(Context context, String title, PasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("请输入6位数字密码");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String password = input.getText().toString();

            // 密码格式验证
            if (password.length() != 6 || !password.matches("\\d+")) {
                Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show();
                callback.onCancel(); // 格式错误，视为取消操作
                return;
            }

            // 密码验证
            if (PasswordManager.getInstance(context).verifyPassword(password)) {
                dialog.dismiss();
                callback.onSuccess();
            } else {
                Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show();
                callback.onCancel(); // 密码错误，视为取消操作
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            dialog.cancel();
            callback.onCancel();
        });

        // 设置对话框关闭监听
        builder.setOnCancelListener(dialog -> {
            callback.onCancel();
        });

        builder.show();
    }

    // 修改密码对话框
    public static void showChangePasswordDialog(Context context, PasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("修改密码");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        // 旧密码输入框
        final EditText oldPasswordInput = new EditText(context);
        oldPasswordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        oldPasswordInput.setHint("请输入旧密码");
        layout.addView(oldPasswordInput);

        // 新密码输入框
        final EditText newPasswordInput = new EditText(context);
        newPasswordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        newPasswordInput.setHint("请输入新密码（6位数字）");
        layout.addView(newPasswordInput);

        // 确认新密码输入框
        final EditText confirmPasswordInput = new EditText(context);
        confirmPasswordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        confirmPasswordInput.setHint("请再次输入新密码");
        layout.addView(confirmPasswordInput);

        builder.setView(layout);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String oldPassword = oldPasswordInput.getText().toString();
            String newPassword = newPasswordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();

            // 验证旧密码
            if (!PasswordManager.getInstance(context).verifyPassword(oldPassword)) {
                Toast.makeText(context, "旧密码错误", Toast.LENGTH_SHORT).show();
                return;
            }

            // 验证新密码格式
            if (newPassword.length() != 6 || !newPassword.matches("\\d+")) {
                Toast.makeText(context, "新密码必须是6位数字", Toast.LENGTH_SHORT).show();
                return;
            }

            // 验证两次输入是否一致
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(context, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
                return;
            }

            // 设置新密码
            if (PasswordManager.getInstance(context).setPassword(newPassword)) {
                Toast.makeText(context, "密码修改成功", Toast.LENGTH_SHORT).show();
                callback.onSuccess();
            } else {
                Toast.makeText(context, "密码修改失败", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onCancel();
            dialog.cancel();
        });

        builder.show();
    }
}