package com.app.estudiaapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class OpenAiKeyStore {
    private static final String TAG = "OpenAiKeyStore";
    private static final String PREFS = "estudia_openai_prefs";
    private static final String KEY_OPENAI = "openai_api_key";

    private static SharedPreferences getPrefs(Context ctx) {
        if (ctx == null) return null;
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // If encrypted prefs are not available for any reason, fall back to normal SharedPreferences
            try {
                return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            } catch (Exception ex) {
                Log.w(TAG, "Unable to get SharedPreferences", ex);
                return null;
            }
        }
    }

    public static void saveKey(Context ctx, @Nullable String key) {
        if (ctx == null) return;
        try {
            SharedPreferences sp = getPrefs(ctx);
            if (sp == null) return;
            sp.edit().putString(KEY_OPENAI, key == null ? "" : key).apply();
        } catch (Exception e) {
            Log.w(TAG, "saveKey failed", e);
        }
    }

    public static String getKey(Context ctx) {
        if (ctx == null) return "";
        try {
            SharedPreferences sp = getPrefs(ctx);
            if (sp == null) return "";
            String k = sp.getString(KEY_OPENAI, "");
            return k == null ? "" : k;
        } catch (Exception e) {
            Log.w(TAG, "getKey failed", e);
            return "";
        }
    }

    public static void clearKey(Context ctx) {
        if (ctx == null) return;
        try {
            SharedPreferences sp = getPrefs(ctx);
            if (sp == null) return;
            sp.edit().remove(KEY_OPENAI).apply();
        } catch (Exception e) {
            Log.w(TAG, "clearKey failed", e);
        }
    }
}
