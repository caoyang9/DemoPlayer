package com.android.retaildemo;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HelloActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建一个TextView直接显示Hello
        TextView textView = new TextView(this);
        textView.setText("Hello");
        textView.setTextSize(50);
        textView.setGravity(android.view.Gravity.CENTER);

        // 设置为ContentView
        setContentView(textView);
    }
}
