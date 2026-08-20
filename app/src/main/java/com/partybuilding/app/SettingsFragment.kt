package com.partybuilding.app

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<SeekBarPreference>("page_interval")?.apply {
            min = DataStore.MIN_INTERVAL
            max = DataStore.MAX_INTERVAL
            seekBarIncrement = 1
            showSeekBarValue = true
        }

        findPreference<Preference>("reset_data")?.setOnPreferenceClickListener {
            DataStore(requireContext()).resetAllText()
            Toast.makeText(requireContext(), R.string.msg_reset_done, Toast.LENGTH_SHORT).show()
            true
        }
    }
}
