package com.android.retaildemo.time;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

public class ConfigManager {
    private static final String PREF_NAME = "demo_pref";
    private static final String KEY_TIME_CONFIG = "time_config";
    private SharedPreferences prefs;
    private Gson gson = new Gson();

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveConfig(TimeConfig config) {
        prefs.edit().putString(KEY_TIME_CONFIG, gson.toJson(config)).apply();
    }

    public TimeConfig getConfig() {
        String json = prefs.getString(KEY_TIME_CONFIG, "");
        if (json != null && json.isEmpty()) {
            return new TimeConfig();
        }
        return gson.fromJson(json, TimeConfig.class);
    }
}