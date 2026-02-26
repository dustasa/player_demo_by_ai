package com.example.playerdemo.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.playerdemo.data.model.WslConfig;
import com.google.gson.Gson;

public class ConfigManager {
    private static final String PREFS_NAME = "player_demo_prefs";
    private static final String KEY_WSL_CONFIG = "wsl_config";
    
    private static ConfigManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    private WslConfig cachedConfig;

    private ConfigManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadConfig();
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }

    public void saveWslConfig(WslConfig config) {
        String json = gson.toJson(config);
        prefs.edit().putString(KEY_WSL_CONFIG, json).apply();
        cachedConfig = config;
    }

    public WslConfig getWslConfig() {
        return cachedConfig;
    }

    private void loadConfig() {
        String json = prefs.getString(KEY_WSL_CONFIG, null);
        if (json != null) {
            try {
                cachedConfig = gson.fromJson(json, WslConfig.class);
            } catch (Exception e) {
                cachedConfig = new WslConfig();
            }
        } else {
            cachedConfig = new WslConfig();
        }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
        cachedConfig = new WslConfig();
    }
}
