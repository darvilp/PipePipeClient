package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class UpdateSettingsFragment extends BasePreferenceFragment {
    private final Preference.OnPreferenceChangeListener updatePreferenceChange
            = (preference, checkForUpdates) -> {
        defaultPreferences.edit()
                .putBoolean(getString(R.string.update_app_key), (boolean) checkForUpdates).apply();

        if ((boolean) checkForUpdates) {
            NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true);
        }
        return true;
    };

    private final Preference.OnPreferenceClickListener manualUpdateClick
            = preference -> {
        Toast.makeText(getContext(), R.string.checking_updates_toast, Toast.LENGTH_SHORT).show();
        NewVersionWorker.enqueueNewVersionCheckingWork(requireContext(), true);
        return true;
    };

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
        if (BuildConfig.UNOFFICIAL_BUILD) {
            findPreference(getString(R.string.update_app_key)).setVisible(false);
            findPreference(getString(R.string.show_prerelease_key)).setVisible(false);
            final Preference releases = findPreference(getString(R.string.manual_update_key));
            releases.setTitle(R.string.unofficial_releases_title);
            releases.setSummary(R.string.unofficial_releases_summary);
            releases.setOnPreferenceClickListener(preference -> {
                ShareUtils.openUrlInBrowser(requireContext(),
                        "https://github.com/darvilp/PipePipeClient/releases");
                return true;
            });
            return;
        }


        findPreference(getString(R.string.update_app_key))
                .setOnPreferenceChangeListener(updatePreferenceChange);
        findPreference(getString(R.string.manual_update_key))
                .setOnPreferenceClickListener(manualUpdateClick);
    }
}
