package com.example.locate;

import static com.example.locate.BLEManagerMain.concatenateArray;
import static com.example.locate.Constants.C;
import static com.example.locate.Constants.F;
import static com.example.locate.Constants.G;
import static com.example.locate.Constants.Q;
import static com.example.locate.Constants.R;
import static com.example.locate.Constants.MainOfficeWiFiRefDistanceList;
import static com.example.locate.Constants.MainOfficeWiFiRefRssi;
import static com.example.locate.Constants.mainOfficeWiFiList;
import static com.example.locate.Constants.mainOfficeWiFiPositionList;
import static com.example.locate.Constants.obstaclefilename;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.locate.data.ObstacleRegionLoader;
import com.example.locate.data.Position;
import com.example.locate.data.obstacleRegion;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsbSerialManager implements SerialInputOutputManager.Listener {

    private enum UsbPermission { Unknown, Requested, Granted, Denied }

    private static final String INTENT_ACTION_GRANT_USB = "com.hoho.android.usbserial.GRANT_USB";
    private final Handler mainLooper;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    public boolean connected = false;

    /////////////////////////////////////////////////////////////
    private Context context;
    private Map<String, KalmanFilter> apFilters = new HashMap<>();
    private Map<String, Position> apPositions = new HashMap<>();
    private Map<String, Double> apFilteredRSSI = new HashMap<>();
    private Map<String, Double> apRawRSSI = new HashMap<>();
    private TextView tvScanResult;
    private TextView tvPSOResult;
    private List<String> currentAPList;
    private List<String> currentPositionList;
    private double[][] psoBoundary;
    private Handler usbScanHandler = new Handler();
    private boolean isScanning = false;

    private boolean receiverRegistered = false;
    private double [][] psoBoundary_obs;
    private double [][] psoBoundary_map;

    public UsbSerialManager(Context context, TextView tvScanResult, TextView tvPSOResult) {
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

    private Runnable usbSerialScanRunnable = new Runnable() {
        @Override
        public void run(){
//            start();
            double[] filteredValuesArray = getFilteredRSSIValuesArray(apFilteredRSSI);
            double[][] apPositionsArray = getAPPositionValuesArray();

            if (filteredValuesArray.length == currentAPList.size()) {
                WifiProcessor.processWifiBlock(apPositionsArray, filteredValuesArray, psoBoundary, MainOfficeWiFiRefDistanceList, MainOfficeWiFiRefRssi, tvPSOResult);
//                Log.d("starttimestamp", "");
            }

            usbScanHandler.postDelayed(this, 1000);
        }
    };

    public void start() {
        // Register the USB permission receiver
        IntentFilter filter = new IntentFilter(INTENT_ACTION_GRANT_USB);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 12 and above, specify if the receiver is exported
            context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } else {
            context.registerReceiver(broadcastReceiver, filter);
            receiverRegistered = true;
        }


        connect();
    }

    public void startRepeatingTask() {
        isScanning = true;
        usbSerialScanRunnable.run();
    }

    public void cleanup(){
        stopRepeatingTask();
        stop();
    }

    public void stop() {
        // Unregister the USB permission receiver
        if(receiverRegistered){
            context.unregisterReceiver(broadcastReceiver);
            receiverRegistered = false;
        }
        if(connected){
            disconnect();
        }
    }

    public void stopRepeatingTask(){
        isScanning = false;
        usbScanHandler.removeCallbacks(usbSerialScanRunnable);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        ? UsbPermission.Granted : UsbPermission.Denied;
                connect();
            }
        }
    };

    private void connect() {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // Auto-connect to the first connected USB device
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            device = v;
            break; // Pick the first available device
        }

        if (device == null) {
            status("connection failed: no device found");
            cleanup();
            receiverRegistered = false;
            return;
        }

        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }

        usbSerialPort = driver.getPorts().get(0); // Use the first port of the device
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());

        if (usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }

        if (usbConnection == null) {
            status("connection failed: open failed or permission denied");
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(115200, 8, 1, UsbSerialPort.PARITY_NONE);
            usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
            usbIoManager.start();
            status("connected");
            connected = true;
        } catch (Exception e) {
            status("connection failed: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        if (usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            usbSerialPort.close();
        } catch (IOException ignored) {
        }
        usbSerialPort = null;
    }

    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receiveData(data);
        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage());
            disconnect();
        });
    }
    private final Object lock = new Object();
    private String oneLineOfWifiData = "";
    private String newline = TextUtil.newline_crlf;
    private boolean pendingNewline = false;
    private void receiveData(byte[] data) {
        synchronized (lock) {
            SpannableStringBuilder spn = new SpannableStringBuilder();

            // Hex dump display
//            if (data.length > 0) {
//                String hexString = HexDump.dumpHexString(data);
//                String[] hexLines = hexString.split("\n");
//                spn.append("Hex Dump: \n");
//                for (String line : hexLines) {
//                    spn.append(line).append("\n");
//                }
//            }

            // Convert the byte array to a string (assuming UTF-8 encoding or ASCII)
//            String dataString = null;
//            try {
//                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
//                    dataString = new String(data, StandardCharsets.UTF_8);
//                }
//            } catch (Exception e) {
//                Log.e("DataConversion", "Error converting byte array to string", e);
//            }

            String msg = new String(data);
            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                    // don't show CR as ^M if directly before LF
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                    // special handling if CR and LF come in separate fragments
                if (pendingNewline && msg.charAt(0) == '\n') {
                    if(spn.length() >= 2) {
                            spn.delete(spn.length() - 2, spn.length());
                    }
                }
                    pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            spn.append(TextUtil.toCaretString(msg, newline.length() != 0));
            String dataString = String.valueOf(spn);


            char[] charArray = dataString.toCharArray();

            for (int i=0; i<charArray.length; i++){
                if (charArray[i]=='^'){
                    String[] parts = oneLineOfWifiData.split(",");
                    if(parts.length==4){
                        String macAddress = parts[2];
                        String rssi = parts[3];
                        Log.d("dataString", "macAddress: "+macAddress+" | rssi: "+rssi);
                        oneLineOfWifiData = "";
                        i++;


                        if (apFilters.containsKey(macAddress)) {
                            KalmanFilter filter = apFilters.get(macAddress);

                            // Log raw RSSI value
//                            Log.d("raw_rssi_" + deviceAddress, String.valueOf(result.getRssi()));

                            // only filter rssi that is rational in range of [-10, -100]

                            double rssi_current = Double.parseDouble(rssi);
                            if (true) { // rssi_current < -10 && rssi_current > -100
                                // Apply the Kalman filter to the RSSI value
                                double filteredValue = filter.update(rssi_current);
                                // Log the filtered RSSI value
//                                Log.d("filtered_rssi_" + macAddress, String.valueOf(filteredValue));

                                // Further processing, e.g., pass to PSO
                                apRawRSSI.put(macAddress, Double.parseDouble(rssi));
                                apFilteredRSSI.put(macAddress,filteredValue);

                                double[] RawValuesArray = getFilteredRSSIValuesArray(apRawRSSI);
//                                double[] FilteredValuesArray = getFilteredRSSIValuesArray(apFilteredRSSI);
//                                double[][] BeaconPositions = getAPPositionValuesArray();


//                                Log.d("RawValuesArray", Arrays.toString(RawValuesArray));
                                // Update the UI using the context's main thread
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    String resultText = "RAW RSSIs: " + Arrays.toString(RawValuesArray);
                                    tvScanResult.setText(resultText);
                                });

//                                Log.d("FilteredValuesArray", Arrays.toString(FilteredValuesArray));
//                                Log.d("BeaconPosition", String.valueOf(BeaconPositions.length));
//                if (FilteredValuesArray.length == 4){
//                    BLEProcessor.processBLEblock(BeaconPositions,FilteredValuesArray);
//                    Log.d("starttimestamp", "");
//                }
//                BLEProcessor.processBLEblock(BeaconPositions,FilteredValuesArray);
                            }
                        }
                    } else {
                        oneLineOfWifiData = oneLineOfWifiData + charArray[i];
                    }
                } else {
                    oneLineOfWifiData = oneLineOfWifiData + charArray[i];
                }
            }



        }
    }

    private void status(String message) {
        // Handle status messages (for example, show a Toast or log it)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
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

    private List<obstacleRegion> getObstacles(){
        ObstacleRegionLoader loader = new ObstacleRegionLoader(context, obstaclefilename);
        List<obstacleRegion> obstacleRegions = loader.getObstacles();
        return obstacleRegions;
    }

    private double[][] getPsoBoundaryObstacles(List<obstacleRegion> obstacleRegions) {
        return ObstacleRegionLoader.getObstaclesBounds(obstacleRegions);
    }
}
