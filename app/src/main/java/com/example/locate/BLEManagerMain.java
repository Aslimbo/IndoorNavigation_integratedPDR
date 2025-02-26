package com.example.locate;

import static com.example.locate.Constants.C;
import static com.example.locate.Constants.R;
import static com.example.locate.Constants.F;
import static com.example.locate.Constants.G;
import static com.example.locate.Constants.Q;
import static com.example.locate.Constants.obstaclefilename;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.locate.data.ObstacleRegionLoader;
import com.example.locate.data.Position;
import com.example.locate.data.obstacleRegion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 2024/4/15 15:55
 * Author: ZST
 */

public class BLEManagerMain extends Context {

    private BluetoothLeScanner bleScanner;
    private BluetoothAdapter bluetoothAdapter;
    //     The main office
    public List<String> mainOfficeBLEBeaconList = Arrays.asList(
            "A4:9E:69:95:29:73",
            "A4:9E:69:96:19:FF",
            "A4:9E:69:96:1A:0F",
            "BC:02:6E:9C:68:54",
            "A4:9E:69:95:29:61"
    );
    //  Pantry
    public List<String> pantryBLEBeaconList = Arrays.asList(
            "A4:9E:69:95:1E:67",
            "A4:9E:69:95:23:56",
            "A4:9E:69:95:29:6B",
            "A4:9E:69:95:23:6D",
            "BC:02:6E:9C:57:B4"
    );

    //  combined meeting, corrider, printing, and entry
    public List<String> mcpeBLEBeaconList = Arrays.asList(
            "A4:9E:69:95:29:86",
            "A4:9E:69:95:26:2A",
            "A4:9E:69:95:1E:7B",
            "A4:9E:69:95:3A:AB",
            "A4:9E:69:95:29:46"
    );

    // Meeting room
    public List<String> meetingRoomBLEBeaconList = Arrays.asList(
            "A4:9E:69:95:3A:AA",
            "A4:9E:69:95:29:38",
            "A4:9E:69:95:1B:D1",
            "A4:9E:69:95:29:2B",
            "A4:9E:69:95:29:46"
    );

    public List<String> backupBLEBeaconList = Arrays.asList(
            "A4:9E:69:95:21:17",
            "A4:9E:69:95:26:63",
            "A4:9E:69:95:1E:5B",
            "BC:02:6E:9C:60:64",
            "A4:9E:69:19:4A:49"
    );

    // Main office
    public List<String> mainOfficeBLEPositionList = Arrays.asList(
            "16.8, 13.5",
            "0.85, 13.5",
            "0.85, 4.03",
            "17.2, 4.1",
            "8.70, 8.28"
    );
    // COMMON
    public List<String> pantryBLEPositionList = Arrays.asList(
            "22.7,  7.6",
            "24.5, 12.0",
            "18.3, 11.0",
            "18.3, 3.5",
            "21.13, 8.65"
    );
    // MEETING ROOM
    public List<String> meetingRoomBLEPositionList = Arrays.asList(
            "13.95, 14.35",
            "18.01, 14.47",
            "17.94, 19.96",
            "13.99, 20.06",
            "15.76, 17.51"
    );

    //  combined meeting, corrider, printing, and entry
    public List<String> mcpeAreaBLEPositionList = Arrays.asList(
            "19.57, 15.18",
            "19.69, 25.15",
            "12.06, 25.08",
            "11.38, 14.37",
            "15.76, 17.51"
    );

    public List<String> backupBLEPositionList = Arrays.asList(
            "0.58, 3.65",
            "17.58, 3.76",
            "17.10, 13.79",
            "0.66, 13.50",
            "9.12, 8.30"
    );

//    private final String obstaclefilename = "obstacleRegions1" + ".txt";

    private List<String> currentBeaconList;
    private List<String> currentPositionList;

    private Context context;
    private Handler bleScanHandler = new Handler();
    private boolean isScanning = false;
    private TextView tvScanResult;
    private TextView tvPSOResult;
    private double[][] psoBoundary_obs;
    private double[][] psoBoundary_map;
    private double[][] psoBoundary;

    // Map to store a KalmanFilter for each BLE beacon address
    private Map<String, KalmanFilter> beaconFilters = new HashMap<>();
    private Map<String, Position> beaconposition = new HashMap<>();
    private Map<String, Double> beaconpFilteredRSSI = new HashMap<>();
    private Map<String, Double> beaconpRawRSSI = new HashMap<>();


    private final PDRKalman PdrKalman;

    private final Runnable bleScanRunnable = new Runnable() {
        @Override
        public void run() {
            startBleScan();
            double[] FilteredValuesArray = getbeaconpFilteredRSSIValuesArray(beaconpFilteredRSSI);
            double[][] BeaconPositions = getBeaconPositionValuesArray();
            if (FilteredValuesArray.length == 5) {
                BLEProcessor.processBLEblock(BeaconPositions, FilteredValuesArray, psoBoundary, tvPSOResult, PdrKalman);
                Log.d("starttimestamp", "");
            }

            // Uncomment if you want to repeat the scan
            bleScanHandler.postDelayed(this, 500); // Repeat every 1 second
        }
    };

    public BLEManagerMain(Context context, TextView tvScanResult, TextView tvPSOResult, PDRKalman PdrKalman) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        this.tvScanResult = tvScanResult;
        this.tvPSOResult = tvPSOResult;
        this.PdrKalman = PdrKalman;

        setBeaconList(mainOfficeBLEBeaconList, mainOfficeBLEPositionList);
        List<obstacleRegion> obstaclesRegions = getObstacles();
        psoBoundary_obs = getPsoBoundaryObstacles(obstaclesRegions);
        psoBoundary_map = getPSOBoundary();
        psoBoundary = concatenateArray(psoBoundary_map, psoBoundary_obs);
//        for (int i = 0; i < psoBoundary.length; i++) {
//            Log.d("Psoboundary", Arrays.toString(psoBoundary[i]));
//        }

        // Initialize Kalman Filters for each beacon
        for (String beaconAddress : currentBeaconList) {
            KalmanFilter kalmanFilter = new KalmanFilter(Q, R, F, G, C);
            kalmanFilter.setInitialState(5.0);
            beaconFilters.put(beaconAddress, kalmanFilter);
        }
        initializeBeaconPositions();
    }

    public void reinitializeKalmanFiltersAndPositions() {
        // Clear the existing filters
        beaconFilters.clear();
        beaconposition.clear();
        beaconpFilteredRSSI.clear();
        beaconpRawRSSI.clear();

        // Reinitialize Kalman Filters for the new beacon list
        for (String beaconAddress : currentBeaconList) {
            KalmanFilter kalmanFilter = new KalmanFilter(Q, R, F, G, C);
            kalmanFilter.setInitialState(-50);
            beaconFilters.put(beaconAddress, kalmanFilter);
        }

        // Reinitialize beacon positions for the new beacon list
        psoBoundary = getPSOBoundary();
        initializeBeaconPositions();
    }

    private void initializeBeaconPositions() {

        beaconposition.put(currentBeaconList.get(0), new Position(parsePosition(currentPositionList.get(0))[0], parsePosition(currentPositionList.get(0))[1]));
        beaconposition.put(currentBeaconList.get(1), new Position(parsePosition(currentPositionList.get(1))[0], parsePosition(currentPositionList.get(1))[1]));
        beaconposition.put(currentBeaconList.get(2), new Position(parsePosition(currentPositionList.get(2))[0], parsePosition(currentPositionList.get(2))[1]));
        beaconposition.put(currentBeaconList.get(3), new Position(parsePosition(currentPositionList.get(3))[0], parsePosition(currentPositionList.get(3))[1]));
        beaconposition.put(currentBeaconList.get(4), new Position(parsePosition(currentPositionList.get(4))[0], parsePosition(currentPositionList.get(4))[1]));
        ;
        beaconpFilteredRSSI.put(currentBeaconList.get(0), (double) -50);
        beaconpFilteredRSSI.put(currentBeaconList.get(1), (double) -50);
        beaconpFilteredRSSI.put(currentBeaconList.get(2), (double) -50);
        beaconpFilteredRSSI.put(currentBeaconList.get(3), (double) -50);
        beaconpFilteredRSSI.put(currentBeaconList.get(4), (double) -50);

        beaconpRawRSSI.put(currentBeaconList.get(0), (double) -50);
        beaconpRawRSSI.put(currentBeaconList.get(1), (double) -50);
        beaconpRawRSSI.put(currentBeaconList.get(2), (double) -50);
        beaconpRawRSSI.put(currentBeaconList.get(3), (double) -50);
        beaconpRawRSSI.put(currentBeaconList.get(4), (double) -50);

    }

    void startBleScan() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bleScanner != null) {
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bleScanner.startScan(null, settings, bleScanCallback);
            isScanning = true;
            Log.d("BLEManagerMain", "BLE scan started");
        }
    }

    private void stopBleScan() {
        if (isScanning && bleScanner != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bleScanner.stopScan(bleScanCallback);
            isScanning = false;
            Log.d("BLEManagerMain", "BLE scan stopped");
        }
    }

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceAddress = result.getDevice().getAddress();

            // Check if this device has a corresponding Kalman Filter
            if (beaconFilters.containsKey(deviceAddress)) {
                KalmanFilter filter = beaconFilters.get(deviceAddress);

                // Log raw RSSI value
                Log.d("raw_rssi_" + deviceAddress, String.valueOf(result.getRssi()));

                // only filter rssi that is rational in range of [-10, -100]

                double rssi_current = result.getRssi();
                if (true) { // rssi_current < -10 && rssi_current > -100
                    // Apply the Kalman filter to the RSSI value
                    double filteredValue = filter.update(rssi_current);
                    // Log the filtered RSSI value
                    Log.d("filtered_rssi_" + deviceAddress, String.valueOf(filteredValue));

                    // Further processing, e.g., pass to PSO
                    beaconpRawRSSI.put(deviceAddress, (double) result.getRssi());
                    beaconpFilteredRSSI.put(deviceAddress, filteredValue);
                    double[] RawValuesArray = getbeaconpFilteredRSSIValuesArray(beaconpRawRSSI);

                    double[] FilteredValuesArray = getbeaconpFilteredRSSIValuesArray(beaconpFilteredRSSI);
                    double[][] BeaconPositions = getBeaconPositionValuesArray();
                    Log.d("RawValuesArray", Arrays.toString(RawValuesArray));
                    // Update the UI using the context's main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String resultText = "RAW BLE RSSIs: " + Arrays.toString(RawValuesArray);
                        tvScanResult.setText(resultText);
                    });

                    Log.d("FilteredValuesArray", Arrays.toString(FilteredValuesArray));
                    Log.d("BeaconPosition", String.valueOf(BeaconPositions.length));
//                if (FilteredValuesArray.length == 4){
//                    BLEProcessor.processBLEblock(BeaconPositions,FilteredValuesArray);
//                    Log.d("starttimestamp", "");
//                }
//                BLEProcessor.processBLEblock(BeaconPositions,FilteredValuesArray);
                }
            }
            Log.d("bleScanResults", deviceAddress);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // Handle batch scan results if needed
            // For each result, process as in onScanResult
            for (ScanResult result : results) {
                onScanResult(0, result); // Use default callbackType (0)
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLEManagerMain", "BLE scan failed with error code: " + errorCode);
        }
    };

    private void processBleResult(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        long timestamp = new Date().getTime();
        String bleResultString = timestamp + "\t" + "TYPE_BLE" + "\t" + device.getName() + "\t" + device.getAddress()
                + "\t" + result.getRssi() + "\n";

        ArrayList<String> bleResultsList = new ArrayList<>();
        bleResultsList.add(bleResultString);

        if (bleResultsList.size() > 20) {
            DataProcessor.processBlock(bleResultsList);
        }

        // Update BLE display if needed
        // updateBleDisplay(bleResultsList);
    }

    private void updateBleDisplay(List<String> bleResults) {
        StringBuilder sb = new StringBuilder("BLE:\n");
        for (String result : bleResults) {
            sb.append(result).append("\n");
        }
        // Uncomment if you have a TextView to display BLE info
        // tvBleInfo.setText(sb.toString());
    }

    public void startRepeatingTask() {
        bleScanRunnable.run();
    }

    public void stopRepeatingTask() {
        bleScanHandler.removeCallbacks(bleScanRunnable);
        stopBleScan();
    }

    public void cleanup() {
        stopRepeatingTask();
        stopBleScan();
    }

    public double[] getbeaconpFilteredRSSIValuesArray(Map<String, Double> map) {
        // Retrieve the values from the map
        Collection<Double> values = map.values();

        // Convert the collection to an array
        double[] filteredValuesArray = new double[values.size()];
        int index = 0;
        for (Double value : values) {
            filteredValuesArray[index++] = value;
        }

        return filteredValuesArray;
    }

    public double[][] getBeaconPositionValuesArray() {
        // Retrieve the values (Position objects) from the map
        Collection<Position> positions = beaconposition.values();

        // Create an array to store the positions (each as a [x, y] array)
        double[][] positionArray = new double[positions.size()][2];

        int index = 0;
        for (Position position : positions) {
            // Store both x and y coordinates in the array
            positionArray[index][0] = position.getX();
            positionArray[index][1] = position.getY();
            index++;
        }

        return positionArray;
    }

    private double[] parsePosition(String positionStr) {
        String[] coordinates = positionStr.split(",");

        double x = Double.parseDouble((coordinates[0]));
        double y = Double.parseDouble((coordinates[1]));

        return new double[]{x, y};
    }

    private double[][] getPSOBoundary() {
        assert currentPositionList != null;
        double[][] coordinates = new double[currentPositionList.size()][2]; // 4 row and 2 col

        for (int i = 0; i < currentPositionList.size(); i++) {
            coordinates[i] = parsePosition(currentPositionList.get(i));
        }
        ;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (int i = 0; i < coordinates.length; i++) {
            double x = coordinates[i][0];
            double y = coordinates[i][1];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);

            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

        }

        double[][] coordinates_min_max = new double[1][4];
        coordinates_min_max[0][0] = maxX;
        coordinates_min_max[0][1] = minX;
        coordinates_min_max[0][2] = maxY;
        coordinates_min_max[0][3] = minY;

        return coordinates_min_max;
    }

    ;

    private double[][] getInitPSOBoundary() {
        double[][] coordinates = new double[mainOfficeBLEPositionList.size()][2]; // 4 row and 2 col

        for (int i = 0; i < mainOfficeBLEPositionList.size(); i++) {
            coordinates[i] = parsePosition(mainOfficeBLEPositionList.get(i));
        }
        ;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (int i = 0; i < coordinates.length; i++) {
            double x = coordinates[i][0];
            double y = coordinates[i][1];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);

            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

        }

        double[][] coordinates_min_max = new double[2][2];
        coordinates_min_max[0][0] = maxX;
        coordinates_min_max[0][1] = minX;
        coordinates_min_max[1][0] = maxY;
        coordinates_min_max[1][1] = minY;

        return coordinates_min_max;
    }

    ;

    private List<obstacleRegion> getObstacles() {
        ObstacleRegionLoader loader = new ObstacleRegionLoader(context, obstaclefilename);
        List<obstacleRegion> obstacleRegions = loader.getObstacles();
        return obstacleRegions;
    }

    private double[][] getPsoBoundaryObstacles(List<obstacleRegion> obstacleRegions) {
        return ObstacleRegionLoader.getObstaclesBounds(obstacleRegions);
    }

    public void setBeaconList(List<String> beaconList, List<String> positionList) {
        this.currentBeaconList = beaconList;
        this.currentPositionList = positionList;
    }

    public static double[][] concatenateArray(double[][] array1, double[][] array2) {
        if (array1[0].length != array2[0].length) {
            throw new IllegalArgumentException("Array must have the same column length");
        }

        double[][] results = new double[array1.length + array2.length][array1[0].length];

        for (int i = 0; i < array1.length; i++) {
            System.arraycopy(array1[i], 0, results[i], 0, array1[i].length);
        }

        for (int i = 0; i < array2.length; i++) {
            System.arraycopy(array2[i], 0, results[i + array1.length], 0, array2[i].length);
        }
        return results;
    }

    public interface PositionUpdateCallback {
        void onPositionUpdated(double[] position);
    }

    private PositionUpdateCallback positionCallback;

    public void setPositionUpdateCallback(PositionUpdateCallback callback) {
        this.positionCallback = callback;
    }

    // 在 processBLEblock 处理完成后触发回调
    private void notifyPositionUpdate(double[] position) {
        if (positionCallback != null) {
            positionCallback.onPositionUpdated(position);
        }
    }

    @Override
    public AssetManager getAssets() {
        return null;
    }

    @Override
    public Resources getResources() {
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        return null;
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    @Override
    public Looper getMainLooper() {
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return null;
    }

    @Override
    public void setTheme(int resid) {

    }

    @Override
    public Resources.Theme getTheme() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public String getPackageName() {
        return "";
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return null;
    }

    @Override
    public String getPackageResourcePath() {
        return "";
    }

    @Override
    public String getPackageCodePath() {
        return "";
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return null;
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        return false;
    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return null;
    }

    @Override
    public boolean deleteFile(String name) {
        return false;
    }

    @Override
    public File getFileStreamPath(String name) {
        return null;
    }

    @Override
    public File getDataDir() {
        return null;
    }

    @Override
    public File getFilesDir() {
        return null;
    }

    @Override
    public File getNoBackupFilesDir() {
        return null;
    }

    @Nullable
    @Override
    public File getExternalFilesDir(@Nullable String type) {
        return null;
    }

    @Override
    public File[] getExternalFilesDirs(String type) {
        return new File[0];
    }

    @Override
    public File getObbDir() {
        return null;
    }

    @Override
    public File[] getObbDirs() {
        return new File[0];
    }

    @Override
    public File getCacheDir() {
        return null;
    }

    @Override
    public File getCodeCacheDir() {
        return null;
    }

    @Nullable
    @Override
    public File getExternalCacheDir() {
        return null;
    }

    @Override
    public File[] getExternalCacheDirs() {
        return new File[0];
    }

    @Override
    public File[] getExternalMediaDirs() {
        return new File[0];
    }

    @Override
    public String[] fileList() {
        return new String[0];
    }

    @Override
    public File getDir(String name, int mode) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, @Nullable DatabaseErrorHandler errorHandler) {
        return null;
    }

    @Override
    public boolean moveDatabaseFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteDatabase(String name) {
        return false;
    }

    @Override
    public File getDatabasePath(String name) {
        return null;
    }

    @Override
    public String[] databaseList() {
        return new String[0];
    }

    @Override
    public Drawable getWallpaper() {
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        return 0;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        return 0;
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {

    }

    @Override
    public void setWallpaper(InputStream data) throws IOException {

    }

    @Override
    public void clearWallpaper() throws IOException {

    }

    @Override
    public void startActivity(Intent intent) {

    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {

    }

    @Override
    public void startActivities(Intent[] intents) {

    }

    @Override
    public void startActivities(Intent[] intents, Bundle options) {

    }

    @Override
    public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags) throws IntentSender.SendIntentException {

    }

    @Override
    public void startIntentSender(IntentSender intent, @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, @Nullable Bundle options) throws IntentSender.SendIntentException {

    }

    @Override
    public void sendBroadcast(Intent intent) {

    }

    @Override
    public void sendBroadcast(Intent intent, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcast(@NonNull Intent intent, @Nullable String receiverPermission, @Nullable BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, @Nullable String receiverPermission, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void sendStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle user, BroadcastReceiver resultReceiver, @Nullable Handler scheduler, int initialCode, @Nullable String initialData, @Nullable Bundle initialExtras) {

    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle user) {

    }

    @Nullable
    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler) {
        return null;
    }

    @Nullable
    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, @Nullable String broadcastPermission, @Nullable Handler scheduler, int flags) {
        return null;
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {

    }

    @Nullable
    @Override
    public ComponentName startService(Intent service) {
        return null;
    }

    @Nullable
    @Override
    public ComponentName startForegroundService(Intent service) {
        return null;
    }

    @Override
    public boolean stopService(Intent service) {
        return false;
    }

    @Override
    public boolean bindService(@NonNull Intent service, @NonNull ServiceConnection conn, int flags) {
        return false;
    }

    @Override
    public void unbindService(@NonNull ServiceConnection conn) {

    }

    @Override
    public boolean startInstrumentation(@NonNull ComponentName className, @Nullable String profileFile, @Nullable Bundle arguments) {
        return false;
    }

    @Override
    public Object getSystemService(@NonNull String name) {
        return null;
    }

    @Nullable
    @Override
    public String getSystemServiceName(@NonNull Class<?> serviceClass) {
        return "";
    }

    @Override
    public int checkPermission(@NonNull String permission, int pid, int uid) {
        return 0;
    }

    @Override
    public int checkCallingPermission(@NonNull String permission) {
        return 0;
    }

    @Override
    public int checkCallingOrSelfPermission(@NonNull String permission) {
        return 0;
    }

    @Override
    public int checkSelfPermission(@NonNull String permission) {
        return 0;
    }

    @Override
    public void enforcePermission(@NonNull String permission, int pid, int uid, @Nullable String message) {

    }

    @Override
    public void enforceCallingPermission(@NonNull String permission, @Nullable String message) {

    }

    @Override
    public void enforceCallingOrSelfPermission(@NonNull String permission, @Nullable String message) {

    }

    @Override
    public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(Uri uri, int modeFlags) {

    }

    @Override
    public void revokeUriPermission(String toPackage, Uri uri, int modeFlags) {

    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags) {
        return 0;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int modeFlags) {
        return 0;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int modeFlags) {
        return 0;
    }

    @Override
    public int checkUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags) {
        return 0;
    }

    @Override
    public void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message) {

    }

    @Override
    public void enforceUriPermission(@Nullable Uri uri, @Nullable String readPermission, @Nullable String writePermission, int pid, int uid, int modeFlags, @Nullable String message) {

    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createContextForSplit(String splitName) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createConfigurationContext(@NonNull Configuration overrideConfiguration) {
        return null;
    }

    @Override
    public Context createDisplayContext(@NonNull Display display) {
        return null;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        return null;
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        return false;
    }
}
