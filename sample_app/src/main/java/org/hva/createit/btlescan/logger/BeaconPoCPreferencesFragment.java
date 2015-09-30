package org.hva.createit.btlescan.logger;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import org.hva.createit.btlescan.R;

public class BeaconPoCPreferencesFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	addPreferencesFromResource(R.xml.preferences);
    }

}
