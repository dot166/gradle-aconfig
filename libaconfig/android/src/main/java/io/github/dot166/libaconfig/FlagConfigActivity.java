package io.github.dot166.libaconfig;

import static io.github.dot166.libaconfig.Keys.keys;

import android.os.Bundle;

import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.Objects;

import io.github.dot166.jlib.app.jConfigActivity;

import io.github.dot166.libaconfig.R;


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
            requireActivity().setTitle(getString(R.string.feature_flags));
            PreferenceScreen screen = getPreferenceScreen();
            screen.removePreference(Objects.requireNonNull(findPreference("lib_category"))); // removes data setting, for preference screens never do this

            for (String key : keys) {
                SwitchPreference pref = new SwitchPreference(requireContext());
                pref.setKey(key);
                pref.setTitle(key);
                screen.addPreference(pref);
            }
        }
    }
}