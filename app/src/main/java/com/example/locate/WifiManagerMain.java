package com.example.locate;


import static com.example.locate.BLEManagerMain.concatenateArray;
import static com.example.locate.Constants.C;
import static com.example.locate.Constants.F;
import static com.example.locate.Constants.G;
import static com.example.locate.Constants.Q;
import static com.example.locate.Constants.R;
import static com.example.locate.Constants.MainOfficeWiFiRefDistanceList;
import static com.example.locate.Constants.MainOfficeWiFiRefRssi;

import static com.example.locate.Constants.mainOfficeHotSpotList;
import static com.example.locate.Constants.mainOfficeWiFiList;
import static com.example.locate.Constants.mainOfficeWiFiPhonePositionList;
import static com.example.locate.Constants.mainOfficeWiFiPositionList;
import static com.example.locate.Constants.obstaclefilename;

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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.UserHandle;
import android.view.Display;
import android.widget.TextView;
import android.os.Handler;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.locate.data.ObstacleRegionLoader;
import com.example.locate.data.Position;
import com.example.locate.data.obstacleRegion;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;


/**
 * Created on 2024/4/15 15:55
 * Author: ZST
 */

public class WifiManagerMain extends Context {
    private WifiManager wifiManager;
    public boolean wifiReceiverRegistered = false;

    private Context context;
    private TextView tvWifiInfo;
    private TextView tvScanResult;
    private TextView tvPSOResult;
    private double[][] psoBoundary;
    private boolean isScanning = false;

    // Map to store a KalmanFilter for each WiFi AP BSSID
    private Map<String, KalmanFilter> apFilters = new HashMap<>();
    private Map<String, Position> apPositions = new HashMap<>();
    private Map<String, Double> apFilteredRSSI = new HashMap<>();
    private Map<String, Double> apRawRSSI = new HashMap<>();
    private List<String> currentAPList;
    private List<String> currentPositionList;
    private double[] distance_ref;
    private double[] rssi_ref;
    private Handler wifiScanHandler = new Handler();
    private double [][] psoBoundary_obs;
    private double [][] psoBoundary_map;


    private Runnable wifiScanRunnable = new Runnable() {
        @Override
        public void run() {
            startWifiScan();
            double[] filteredValuesArray = getFilteredRSSIValuesArray(apFilteredRSSI);
            double[][] apPositionsArray = getAPPositionValuesArray();

            if (filteredValuesArray.length == currentAPList.size()) {
                WifiProcessor.processWifiBlock(apPositionsArray, filteredValuesArray, psoBoundary, distance_ref, rssi_ref, tvPSOResult);
                Log.d("starttimestamp", "");
            }

            // Repeat the scan every ? seconds
            wifiScanHandler.postDelayed(this, 1000);
        }
    };

    public WifiManagerMain(Context context, TextView tvScanResult, TextView tvPSOResult) {
        this.context = context;
        this.tvScanResult = tvScanResult;
        this.tvPSOResult = tvPSOResult;
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        setAPList(mainOfficeWiFiList, mainOfficeWiFiPositionList);


        setAPList(mainOfficeWiFiList, mainOfficeWiFiPositionList);
        List<obstacleRegion> obstaclesRegions = getObstacles();
        psoBoundary_obs = getPsoBoundaryObstacles(obstaclesRegions);
        psoBoundary_map = getPSOBoundary();
        psoBoundary = concatenateArray(psoBoundary_map, psoBoundary_obs);


        for (String apAddress: currentAPList) {
            KalmanFilter kalmanFilter = new KalmanFilter(Q, R, F, G, C);
            kalmanFilter.setInitialState(-50.0);
            apFilters.put(apAddress, kalmanFilter);
        }
        initializeAPPositions();
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);
    }

    void startWifiScan() {
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            boolean success = wifiManager.startScan();
            if (!success) {
                // Scan initiation failed
                Log.e("WifiManagerMain", "WiFi scan initiation failed");
            } else {
                isScanning = true;
                Log.d("WifiManagerMain", "WiFi scan started");
            }
        } else {
            Log.e("WifiManagerMain", "WiFi is disabled");
        }
    }

    private void stopWifiScan() {
        if (isScanning && wifiManager != null) {
            isScanning = false;
            Log.d("WifiManagerMain", "WiFi scan stopped");
        }
    }


    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null && intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                processScanResults();
            }
        }
    };

    private void processScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        double[] rawValuesArray = new double[4];
        for (ScanResult result : results) {
            String bssid = result.BSSID;

            // Check if this BSSID has a corresponding Kalman Filter
            if (apFilters.containsKey(bssid)) {
                KalmanFilter filter = apFilters.get(bssid);

                // Log raw RSSI value
                Log.d("raw_rssi_" + bssid, String.valueOf(result.level));

                double rssiCurrent = result.level;

                // Apply the Kalman filter to the RSSI value
                double filteredValue = filter.update(rssiCurrent);
                // Log the filtered RSSI value
                Log.d("filtered_rssi_" + bssid, String.valueOf(filteredValue));

                // Update maps with raw and filtered RSSI values
                apRawRSSI.put(bssid, rssiCurrent);
                apFilteredRSSI.put(bssid, filteredValue);

                rawValuesArray = getFilteredRSSIValuesArray(apRawRSSI);

                // Update the UI using the context's main thread
                double[] finalRawValuesArray = rawValuesArray;
                new Handler(Looper.getMainLooper()).post(() -> {
                    String resultText = "RAW WiFi RSSIs: " + Arrays.toString(finalRawValuesArray);
                    tvScanResult.setText(resultText);
                });
            }

            Log.d("wifiScanResults", bssid);
        }
        Log.d("RawValues", Arrays.toString(rawValuesArray));

    }


    public void startRepeatingTask() {
        isScanning = true;
        wifiScanRunnable.run();
    }

    public void stopRepeatingTask() {
        wifiScanHandler.removeCallbacks(wifiScanRunnable);
        stopWifiScan();
        context.unregisterReceiver(wifiScanReceiver);
    }

    public void cleanup() {
        stopRepeatingTask();
        stopWifiScan();
    }

    public double[] getFilteredRSSIValuesArray(Map<String, Double> map) {
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

    public void reinitializeKalmanFiltersAndPositions() {
        // Clear the existing filters and positions
        apFilters.clear();
        apPositions.clear();
        apFilteredRSSI.clear();
        apRawRSSI.clear();

        // Reinitialize Kalman Filters for the new AP list
        for (String bssid : currentAPList) {
            KalmanFilter kalmanFilter = new KalmanFilter(Q, R, F, G, C);
            kalmanFilter.setInitialState(-50.0);
            apFilters.put(bssid, kalmanFilter);
        }

        // Reinitialize AP positions for the new AP list
        psoBoundary = getPSOBoundary();
        initializeAPPositions();
    }


    public double[][] getAPPositionValuesArray() {
        // Retrieve the values (Position objects) from the map
        Collection<Position> positions = apPositions.values();

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

    private void initializeAPPositions() {
        for (int i = 0; i < currentAPList.size(); i++) {
            String bssid = currentAPList.get(i);
            double[] position = parsePosition(currentPositionList.get(i));
            apPositions.put(bssid, new Position(position[0], position[1]));
            apFilteredRSSI.put(bssid, (double) -100);
            apRawRSSI.put(bssid, (double) -100);
        }
    }

    private double[][] getPSOBoundary() {
        double[][] coordinates = new double[currentPositionList.size()][2];

        for (int i = 0; i < currentPositionList.size(); i++) {
            coordinates[i] = parsePosition(currentPositionList.get(i));
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (double[] coordinate : coordinates) {
            double x = coordinate[0];
            double y = coordinate[1];

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);

            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        double[][] coordinatesMinMax = new double[1][4];
        coordinatesMinMax[0][0] = 17;//maxX;
        coordinatesMinMax[0][1] = 1;//minX;
        coordinatesMinMax[0][2] = 13;//maxY;
        coordinatesMinMax[0][3] = 1;//minY;

        return coordinatesMinMax;
    }

    private double[] parsePosition(String positionStr) {
        String[] coordinates = positionStr.split(",");

        double x = Double.parseDouble(coordinates[0].trim());
        double y = Double.parseDouble(coordinates[1].trim());

        return new double[]{x, y};
    }

    public void setAPList(List<String> apList, List<String> positionList) {
        this.currentAPList = apList;
        this.currentPositionList = positionList;
        this.distance_ref = MainOfficeWiFiRefDistanceList;
        this.rssi_ref = MainOfficeWiFiRefRssi;
    }

    private List<obstacleRegion> getObstacles(){
        ObstacleRegionLoader loader = new ObstacleRegionLoader(context, obstaclefilename);
        List<obstacleRegion> obstacleRegions = loader.getObstacles();
        return obstacleRegions;
    }

    private double[][] getPsoBoundaryObstacles(List<obstacleRegion> obstacleRegions) {
        return ObstacleRegionLoader.getObstaclesBounds(obstacleRegions);
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
