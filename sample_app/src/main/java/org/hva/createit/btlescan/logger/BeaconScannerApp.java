package org.hva.createit.btlescan.logger;

import android.app.Application;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;

public class BeaconScannerApp extends Application {

	/**
	 * Global reference to FileHelper instance.
	 */
	private FileHelper fileHelper;
	private BackgroundPowerSaver backgroundPowerSaver;
	private BeaconManager beaconManager;
	private Region region;

	@Override
	public void onCreate() {
		super.onCreate();
		fileHelper = new FileHelper(getExternalFilesDir(null));
		// Allow scanning to continue in the background.
		backgroundPowerSaver = new BackgroundPowerSaver(this);
		beaconManager = BeaconManager.getInstanceForApplication(this);

//		// set the duration of the scan to be 1.1 seconds
//		beaconManager.setBackgroundScanPeriod(2000l);
//		// set the time between each scan to be 1 hour (3600 seconds)
//		beaconManager.setBackgroundBetweenScanPeriod(5l);

		BeaconScannerApp app = this;//(BeaconScannerApp)this.getApplication();
		//beaconManager = app.getBeaconManager();
		//beaconManager.setForegroundScanPeriod(10);

		// logToDisplay("BeaconParsers size is:" + beaconManager.getBeaconParsers().size());

        // Add parser for iBeacons;
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        // Detect the Eddystone main identifier (UID) frame:
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19"));
        // Detect the Eddystone telemetry (TLM) frame:
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("x,s:0-1=feaa,m:2-2=20,d:3-3,d:4-5,d:6-7,d:8-11,d:12-15"));
        // Detect the Eddystone URL frame:
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-20"));

        // Get the details for all the beacons we encounter.
		region = new Region("justGiveMeEverything", null, null, null);
	}

	public FileHelper getFileHelper() { return fileHelper; }
    public BeaconManager getBeaconManager() {
        return beaconManager;
    }
    public Region getRegion() {return region; }

}
