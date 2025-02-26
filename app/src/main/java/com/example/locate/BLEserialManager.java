package com.example.locate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.TextView;

import static com.example.locate.BLEManagerMain.concatenateArray;
import static com.example.locate.Constants.C;
import static com.example.locate.Constants.F;
import static com.example.locate.Constants.G;
import static com.example.locate.Constants.MainOfficeWiFiRefDistanceList;
import static com.example.locate.Constants.MainOfficeWiFiRefRssi;
import static com.example.locate.Constants.Q;
import static com.example.locate.Constants.R;
import static com.example.locate.Constants.deviceAddress;
import static com.example.locate.Constants.mainOfficeWiFiList;
import static com.example.locate.Constants.mainOfficeWiFiPositionList;
import static com.example.locate.Constants.obstaclefilename;

import com.example.locate.data.ObstacleRegionLoader;
import com.example.locate.data.Position;
import com.example.locate.data.obstacleRegion;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLEserialManager implements ServiceConnection, SerialListener {

    private final Handler mainLooper;
    private final Context context;
    public static Map<String, KalmanFilter> apFilters = new HashMap<>();
    public static Map<String, Position> apPositions = new HashMap<>();
    public static Map<String, Double> apFilteredRSSI = new HashMap<>();
    public static Map<String, Double> apRawRSSI = new HashMap<>();
    public static TextView tvScanResult;
    private TextView tvPSOResult;
    private List<String> currentAPList;
    private List<String> currentPositionList;
    private double[][] psoBoundary;
    private Handler bleSerialScanHandler = new Handler();
    public SerialSocket socket;
    private double [][] psoBoundary_obs;
    private double [][] psoBoundary_map;

    public  BLEserialManager (Context context, TextView tvScanResult, TextView tvPSOResult){
        this.context = context;
        this.tvScanResult = tvScanResult;
        this.tvPSOResult = tvPSOResult;
        setAPList(mainOfficeWiFiList, mainOfficeWiFiPositionList);

//        psoBoundary = getPSOBoundary();

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
        this.mainLooper = new Handler(Looper.getMainLooper());
    }

    private Runnable BLESerialScanRunnable = new Runnable() {
        @Override
        public void run(){

            double[] filteredValuesArray = getFilteredRSSIValuesArray(apFilteredRSSI);
            double[][] apPositionsArray = getAPPositionValuesArray();

            if (filteredValuesArray.length == currentAPList.size()) {
                WifiProcessor.processWifiBlock(apPositionsArray, filteredValuesArray, psoBoundary, MainOfficeWiFiRefDistanceList, MainOfficeWiFiRefRssi, tvPSOResult);
//                Log.d("starttimestamp", "");
            }

            bleSerialScanHandler.postDelayed(this, 1000);
        }
    };

    public void start() {
//        // Register the USB permission receiver
//        IntentFilter filter = new IntentFilter(INTENT_ACTION_GRANT_BLE);
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            // For Android 12 and above, specify if the receiver is exported
//            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED);
//            receiverRegistered = true;
//        } else {
//            context.registerReceiver(broadcastReceiver, filter);
//            receiverRegistered = true;
//        }
        connected();
    }

    public void connected(){
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

            this.socket = new SerialSocket(context.getApplicationContext(), device);

            socket.connect();
        } catch (Exception e){
            onSerialConnectError(e);
        }
    }

    public void startRepeatingTask() {
        BLESerialScanRunnable.run();
    }

    public void setAPList(List<String> apList, List<String> positionList) {
        this.currentAPList = apList;
        this.currentPositionList = positionList;
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

//        double[][] coordinatesMinMax = new double[2][2];
//        coordinatesMinMax[0][0] = 17.6;//maxX;
//        coordinatesMinMax[0][1] = 1;//minX;
//        coordinatesMinMax[1][0] = 14;//maxY;
//        coordinatesMinMax[1][1] = 2;//minY;
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

    private void initializeAPPositions() {
        for (int i = 0; i < currentAPList.size(); i++) {
            String bssid = currentAPList.get(i);
            double[] position = parsePosition(currentPositionList.get(i));
            apPositions.put(bssid, new Position(position[0], position[1]));
            apFilteredRSSI.put(bssid, (double) -100);
            apRawRSSI.put(bssid, (double) -100);
        }
    }

    public static double[] getFilteredRSSIValuesArray(Map<String, Double> map) {
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

    public void cleanup(){
        stopRepeatingTask();
    }
    public void stopRepeatingTask() {
        bleSerialScanHandler.removeCallbacks(BLESerialScanRunnable);
        if(socket != null){
            socket.disconnect();
        }
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
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {

    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public void onSerialConnect() {

    }

    @Override
    public void onSerialConnectError(Exception e) {

    }

    @Override
    public void onSerialRead(byte[] data) {

    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {

    }

    @Override
    public void onSerialIoError(Exception e) {

    }


}


//public UsbSerialManager(Context context, TextView tvScanResult, TextView tvPSOResult) {
//    this.context = context;
//    this.tvScanResult = tvScanResult;
//    this.tvPSOResult = tvPSOResult;
//    setAPList(mainOfficeWiFiList, mainOfficeWiFiPositionList);
//
//    psoBoundary = getPSOBoundary();
//
//    for (String apAddress: currentAPList) {
//        KalmanFilter kalmanFilter = new KalmanFilter(Q, R, F, G, C);
//        kalmanFilter.setInitialState(-50.0);
//        apFilters.put(apAddress, kalmanFilter);
//    }
//
//    initializeAPPositions();
//    this.mainLooper = new Handler(Looper.getMainLooper());
//}