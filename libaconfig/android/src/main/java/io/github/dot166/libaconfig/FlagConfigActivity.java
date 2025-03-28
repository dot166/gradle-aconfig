package io.github.dot166.libaconfig;

import static io.github.dot166.libaconfig.Keys.keys;

import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import io.github.dot166.jlib.app.jConfigActivity;


public class FlagConfigActivity extends jConfigActivity {

    @Override
    public jLIBSettingsFragment preferenceFragment() {
        return new FlagConfigFragment();
    }

    public static class FlagConfigFragment extends jLIBSettingsFragment {
        @Override
        public boolean hideLIB() {
            return true; // hide jLib because this is a stripped down feature flag control panel not a settings app
        }
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            super.onCreatePreferences(savedInstanceState, rootKey);

            for (String key : keys) {
                PreferenceScreen screen = getPreferenceScreen();
                Preference pref = new SwitchPreference(requireContext());
                pref.setKey(key);
                screen.addPreference(pref);
            }
        }
    }
}