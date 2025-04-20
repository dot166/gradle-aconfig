package io.github.dot166.libaconfig;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import io.github.dot166.jlib.app.jLIBCoreApp;

public class writableFlag {
    private String mKey;
    private boolean mDefVal;
    private Context mContext;
    public writableFlag(String key, boolean defVal) {
        mKey = key;
        mDefVal = defVal;
        mContext = jLIBCoreApp.getInstance();
    }

    private SharedPreferences getFlagPrefsOptions() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public boolean getFlagValue() {
        if (mContext != null) {
            return getFlagPrefsOptions().getBoolean(mKey, mDefVal);
        } else {
            return mDefVal;
        }
    }
}