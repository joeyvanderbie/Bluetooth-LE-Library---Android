package org.hva.createit.btlescan.logger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.utils.UrlBeaconUrlCompressor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.hva.createit.btlescan.R;
import org.hva.createit.btlescan.activities.MainActivity;

/**
 * Adapted from original code written by D Young of Radius Networks.
 * @author dyoung, jodwyer
 *
 */
public class ScanActivity extends AppCompatActivity implements BeaconConsumer,
														GooglePlayServicesClient.ConnectionCallbacks,
														GooglePlayServicesClient.OnConnectionFailedListener {
    
	// Constant Declaration
	private static final String PREFERENCE_SCANINTERVAL = "scanInterval";
	private static final String PREFERENCE_TIMESTAMP = "timestamp";
	private static final String PREFERENCE_POWER = "power";
	private static final String PREFERENCE_PROXIMITY = "proximity";
	private static final String PREFERENCE_RSSI = "rssi";
	private static final String PREFERENCE_MAJORMINOR = "majorMinor";
	private static final String PREFERENCE_UUID = "uuid";
	private static final String PREFERENCE_INDEX = "index";
    private static final String PREFERENCE_LOCATION = "location";
    private static final String PREFERENCE_REALTIME = "realTimeLog";
    private static final String MODE_SCANNING = "Stop Scanning";
    private static final String MODE_STOPPED = "Start Scanning";
    protected static final String TAG = "ScanActivity";

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    private FileHelper fileHelper; 
    private BeaconManager beaconManager;
    private Region region;
    private int eventNum = 1;
    
    // This StringBuffer will hold the scan data for any given scan.  
    private StringBuffer logString;
   
    // Preferences - will actually have a boolean value when loaded.
    private Boolean index;
    private Boolean location;
    private Boolean uuid;
	private Boolean majorMinor;
	private Boolean rssi;
	private Boolean proximity;
	private Boolean power;
	private Boolean timestamp;
	private String scanInterval;
    // Added following a feature request from D.Schmid.
    private Boolean realTimeLog;
    
	// LocationClient for Google Play Location Services
	LocationClient locationClient;

    private ScrollView scroller;
    private EditText editText;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);
		verifyBluetooth();
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		BeaconScannerApp app = (BeaconScannerApp)this.getApplication();
		beaconManager = app.getBeaconManager();
		//beaconManager.setForegroundScanPeriod(10);
        region = app.getRegion();
        beaconManager.bind(this);
        locationClient = new LocationClient(this, this, this);
        fileHelper = app.getFileHelper();
        scroller = (ScrollView)ScanActivity.this.findViewById(R.id.scanScrollView);
        editText = (EditText)ScanActivity.this.findViewById(R.id.scanText);
		// Initialise scan button.
		getScanButton().setText(MODE_STOPPED);
    }

    @Override
    public void onResume() {
        super.onResume();
        beaconManager.bind(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Uncommenting the following leak prevents a ServiceConnection leak when using the back
        // arrow in the Action Bar to come out of the file list screen. Unfortunately it also kills
        // background scanning, and as I have no workaround right now I'm settling for the lesser of
        // two evils.
        // beaconManager.unbind(this);
    }

    public String getCurrentLocation() {
        /** Default "error" value is set for location, will be overwritten with the correct lat and
         *  long values if we're ble to connect to location services and get a reading.
         */
        String location = "Unavailable";
        if (locationClient.isConnected()) {
            Location currentLocation = locationClient.getLastLocation();
            if (currentLocation != null) {
                location = Double.toString(currentLocation.getLatitude()) + "," +
                        Double.toString(currentLocation.getLongitude());
            }
        }
        return location;
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBeaconServiceConnect() {}
    
    /**
     * 
     * @param view
     */
	public void onScanButtonClicked(View view) {
		toggleScanState();
	}
	
 	// Handle the user selecting "Settings" from the action bar.
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.menu_scanner:
                // Launch list files activity
                Intent scanner = new Intent(this, MainActivity.class);
                startActivity(scanner);
                return true;
            case R.id.Settings:
	            // Show settings
	    		Intent api = new Intent(this, AppPreferenceActivity.class);
	            startActivityForResult(api, 0);
	            return true;
	    	case R.id.action_listfiles:
	    		// Launch list files activity
	    		Intent fhi = new Intent(this, FileHandlerActivity.class);
	            startActivity(fhi);
	            return true;
            case R.id.menu_about:
                displayAboutDialog();
                break;
	        default:
	            return super.onOptionsItemSelected(item);
	     }
        return true;
	 }
	
	/**
	 * Start and stop scanning, and toggle button label appropriately.
	 */
	private void toggleScanState() {
		Button scanButton = getScanButton();
		String currentState = scanButton.getText().toString();
		if (currentState.equals(MODE_SCANNING)) {
			stopScanning(scanButton);
		} else {
			startScanning(scanButton);
		}
	}

	/**
	 * start looking for beacons.
	 */
	private void startScanning(Button scanButton) {
		
		// Set UI elements to the correct state.
		scanButton.setText(MODE_SCANNING);
		((EditText)findViewById(R.id.scanText)).setText("");
		
		// Reset event counter
		eventNum = 1;
		// Get current values for logging preferences
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	    HashMap<String, Object> prefs = new HashMap<String, Object>();
	    prefs.putAll(sharedPrefs.getAll());
	    
	    index = (Boolean)prefs.get(PREFERENCE_INDEX);
        location = (Boolean)prefs.get(PREFERENCE_LOCATION);
	    uuid = (Boolean)prefs.get(PREFERENCE_UUID);
		majorMinor = (Boolean)prefs.get(PREFERENCE_MAJORMINOR);
		rssi = (Boolean)prefs.get(PREFERENCE_RSSI);
		proximity = (Boolean)prefs.get(PREFERENCE_PROXIMITY);
		power = (Boolean)prefs.get(PREFERENCE_POWER);
		timestamp = (Boolean)prefs.get(PREFERENCE_TIMESTAMP);
		scanInterval = (String)prefs.get(PREFERENCE_SCANINTERVAL);
        realTimeLog = (Boolean)prefs.get(PREFERENCE_REALTIME);
		
		// Get current background scan interval (if specified)
		if (prefs.get(PREFERENCE_SCANINTERVAL) != null) {
            // set the duration of the scan to be 1.1 seconds
            beaconManager.setBackgroundScanPeriod(2000l);
            // set the time between each scan to be 1 hour (3600 seconds)
			beaconManager.setBackgroundBetweenScanPeriod(Long.parseLong(scanInterval));
		}
		
		logToDisplay("Scanning...");
		
		// Initialise scan log
		logString = new StringBuffer();
		
		//Start scanning again.
        beaconManager.setRangeNotifier(new RangeNotifier() {
        	@Override
        	public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Iterator<Beacon> beaconIterator = beacons.iterator();
                    while (beaconIterator.hasNext()) {
                        Beacon beacon = beaconIterator.next();
                        logBeaconData(beacon);
                    }
                }
        	}
        });

        try {
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
        	// TODO - OK, what now then?
        }	

	}

	/**
	 * Stop looking for beacons.
	 */
	private void stopScanning(Button scanButton) {
		try {
			beaconManager.stopRangingBeaconsInRegion(region);
		} catch (RemoteException e) {
				// TODO - OK, what now then?
		}
		String scanData = logString.toString();
		if (scanData.length() > 0) {
			// Write file
			fileHelper.createFile(scanData);
			// Display file created message.
			Toast.makeText(getBaseContext(),
                    "File saved to:" + getFilesDir().getAbsolutePath(),
                    Toast.LENGTH_SHORT).show();
			scanButton.setText(MODE_STOPPED);
		} else {
			// We didn't get any data, so there's no point writing an empty file.
			Toast.makeText(getBaseContext(),
                    "No data captured during scan, output file will not be created.",
                    Toast.LENGTH_SHORT).show();
			scanButton.setText(MODE_STOPPED);
		}
	}

	/**
	 * 
	 * @return reference to the start/stop scanning button
	 */
	private Button getScanButton() {
		return (Button)findViewById(R.id.scanButton);
	}
	
    /**
     * 
     * @param beacon The detected beacon
     */
	private void logBeaconData(Beacon beacon) {

		StringBuilder scanString = new StringBuilder();

        if (index) {
            scanString.append(eventNum++);
        }

        if (beacon.getServiceUuid() == 0xfeaa) {

            if (beacon.getBeaconTypeCode() == 0x00) {

                scanString.append(" Eddystone-UID -> ");
                scanString.append(" Namespace : ").append(beacon.getId1());
                scanString.append(" Identifier : ").append(beacon.getId2());

                logEddystoneTelemetry(scanString, beacon);

            } else if (beacon.getBeaconTypeCode() == 0x10) {

                String url = UrlBeaconUrlCompressor.uncompress(beacon.getId1().toByteArray());
                scanString.append(" Eddystone-URL -> " + url);

            } else if (beacon.getBeaconTypeCode() == 0x20) {

                scanString.append(" Eddystone-TLM -> ");
                logEddystoneTelemetry(scanString, beacon);

            }

        } else {

            // Just an old fashioned iBeacon or AltBeacon...
            logGenericBeacon(scanString, beacon);

        }
	    
		logToDisplay(scanString.toString());
		scanString.append("\n");

        // Code added following a feature request by D.Schmid - writes a single entry to a file
        // every time a beacon is detected, the file will only ever have one entry as it will be
        // recreated on each call to this method.
        // Get current background scan interval (if specified)
        if (realTimeLog) {
            // We're in realtime logging mode, create a new log file containing only this entry.
            fileHelper.createFile(scanString.toString(), "realtimelog.txt");
        }

		logString.append(scanString.toString());
		
	}

    /**
     * Logs iBeacon & AltBeacon data.
     */
    private void logGenericBeacon(StringBuilder scanString, Beacon beacon) {
        if (location) {
            scanString.append(" Location: ").append(getCurrentLocation()).append(" ");
        }

        if (uuid) {
            scanString.append(" UUID: ").append(beacon.getId1());
        }

        if (majorMinor) {
            scanString.append(" Maj. Mnr.: ");
            if (beacon.getId2() != null) {
                scanString.append(beacon.getId2());
            }
            scanString.append("-");
            if (beacon.getId3() != null) {
                scanString.append(beacon.getId3());
            }
        }

        if (rssi) {
            scanString.append(" RSSI: ").append(beacon.getRssi());
        }

        if (proximity) {
            scanString.append(" Proximity: ").append(BeaconHelper.getProximityString(beacon.getDistance()));
        }

        if (power) {
            scanString.append(" Power: ").append(beacon.getTxPower());
        }

        if (timestamp) {
            scanString.append(" Timestamp: ").append(BeaconHelper.getCurrentTimeStamp());
        }
    }

    private void logEddystoneTelemetry(StringBuilder scanString, Beacon beacon) {
        // Do we have telemetry data?
        if (beacon.getExtraDataFields().size() > 0) {
            long telemetryVersion = beacon.getExtraDataFields().get(0);
            long batteryMilliVolts = beacon.getExtraDataFields().get(1);
            long pduCount = beacon.getExtraDataFields().get(3);
            long uptime = beacon.getExtraDataFields().get(4);

            scanString.append(" Telemetry version : " + telemetryVersion);
            scanString.append(" Uptime (sec) : " + uptime);
            scanString.append(" Battery level (mv) " + batteryMilliVolts);
            scanString.append(" Tx count: " + pduCount);
        }
    }

	/**
	 * 
	 * @param line
	 */
    private void logToDisplay(final String line) {
    	runOnUiThread(new Runnable() {
    	    public void run() {

    	    	editText.append(line + "\n");

                // Temp code - don't really want to do this for every line logged, will look for a
                // workaround.
                Linkify.addLinks(editText, Linkify.WEB_URLS);

                scroller.fullScroll(View.FOCUS_DOWN);

    	    }
    	});
    }
    
 	private void verifyBluetooth() {

		try {
			if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Bluetooth not enabled");			
				builder.setMessage("Please enable bluetooth in settings and restart this application.");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialog) {
						finish();
			            System.exit(0);
					}					
				});
				builder.show();
			}			
		}
		catch (RuntimeException e) {
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Bluetooth LE not available");			
			builder.setMessage("Sorry, this device does not support Bluetooth LE.");
			builder.setPositiveButton(android.R.string.ok, null);
			builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

				@Override
				public void onDismiss(DialogInterface dialog) {
					finish();
		            System.exit(0);
				}
				
			});
			builder.show();
			
		}
		
	}

    /* Location services code follows */

    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        locationClient.connect();
    }

//    @Override
//    protected void onStop() {
//        // Disconnect the client.
//        locationClient.disconnect();
//        super.onStop();
//    }


    @Override
    protected void onDestroy() {
        // Disconnect the client.
        locationClient.disconnect();
        super.onDestroy();
    }


    @Override
    public void onConnected(Bundle dataBundle) {
        // Uncomment the following line to display the connection status.
        // Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected() {
        // Display the connection status
        Toast.makeText(this, "Disconnected. Please re-connect.",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

         /* Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Toast.makeText(getBaseContext(),
                    "Location services not available, cannot track device location.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    /*
     * Handle results returned to the FragmentActivity
     * by Google Play services
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        // Decide what to do based on the original request code
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
             switch (resultCode) {
                case Activity.RESULT_OK :
                 /*
                  * TODO - Try the request again
                  */
                  break;
             }
        }
    }

    private void displayAboutDialog() {
        // REALLY REALLY LAZY LINKIFIED DIALOG
        final int paddingSizeDp = 5;
        final float scale = getResources().getDisplayMetrics().density;
        final int dpAsPixels = (int) (paddingSizeDp * scale + 0.5f);

        final TextView textView = new TextView(this);
        final SpannableString text = new SpannableString(getString(R.string.about_dialog_text));

        textView.setText(text);
        textView.setAutoLinkMask(RESULT_OK);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(dpAsPixels, dpAsPixels, dpAsPixels, dpAsPixels);

        Linkify.addLinks(text, Linkify.ALL);
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_about)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                    }
                })
                .setView(textView)
                .show();
    }

}

