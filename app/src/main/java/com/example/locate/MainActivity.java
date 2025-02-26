package com.example.locate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;


/**
 * Created on 2024/4/11 09:44
 * Author: ZST
 */

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, ImuDataCallback{
    private BLEManagerMain BLEManagerMain;
    private WifiManagerMain wifiManager;

    private UsbSerialManager usbSerialManager;
    private BLEserialManager BLEserialManager;
    private PermissionManager permissionManager;
    private SensorManagerMain SensorManagerMain;
    private PDRKalman pdrKalman;
    private StepDetection StepDetection;

    static customimageview imageView_map;
    private static scaleanddrag scaleanddrag;
    private String mode = "";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private TextView tvScanResult;
    private TextView tvPSOResult;
    private DataProcessor dataProcessor;

    private TextView navigationHint;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        getSupportFragmentManager().addOnBackStackChangedListener(this);
//        if(savedInstanceState == null){
//            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
//        } else {
//            onBackStackChanged();
//        }

        setupViews();
        setupButtons();
        tvScanResult = findViewById(R.id.raw_RSSI_text);
        tvPSOResult = findViewById(R.id.pso_Error_text);

    }

    private void setupButtons() {

        Button buttonUSB = findViewById(R.id.buttonUSB);
        Button buttonBLESerial = findViewById(R.id.buttonBLESerial);
        Button buttonWifi = findViewById(R.id.buttonWIFI);
        Button buttonBLE = findViewById(R.id.buttonBLE);
        Button buttonMainOffice = findViewById(R.id.buttonMainOffice);
        Button buttonPantry = findViewById(R.id.buttonPantry);
        Button buttonMeetingRoom = findViewById(R.id.buttonMeetingRoom);
        Button buttonMCPEArea = findViewById(R.id.buttonmcpeArea);
        Button buttonStop = findViewById(R.id.buttonBLESerial);
        Button buttonNavigation = findViewById(R.id.buttonNavigation);
        navigationHint = findViewById(R.id.navigationHint);
        buttonNavigation.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NavigationActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
//        buttonUSB.setVisibility(TextView.GONE);
//        buttonWifi.setVisibility(TextView.GONE);
////        buttonMainOffice.setVisibility(TextView.GONE);
//        buttonPantry.setVisibility(TextView.GONE);
//        buttonMeetingRoom.setVisibility(TextView.GONE);
//        buttonMCPEArea.setVisibility(TextView.GONE);

        // add permission check here
        if (checkAndRequestPermissions()) {
            initializeManagers();
        };

        buttonMainOffice.setOnClickListener(v -> {
            BLEManagerMain.setBeaconList(BLEManagerMain.mainOfficeBLEBeaconList, BLEManagerMain.mainOfficeBLEPositionList);
//            BLEManagerMain.setBeaconList(BLEManagerMain.backupBLEBeaconList, BLEManagerMain.backupBLEPositionList);
            BLEManagerMain.reinitializeKalmanFiltersAndPositions();
        });

        buttonPantry.setOnClickListener(v -> {
            BLEManagerMain.setBeaconList(BLEManagerMain.pantryBLEBeaconList, BLEManagerMain.pantryBLEPositionList);
            BLEManagerMain.reinitializeKalmanFiltersAndPositions();

        });

        buttonMeetingRoom.setOnClickListener(v -> {
            BLEManagerMain.setBeaconList(BLEManagerMain.meetingRoomBLEBeaconList, BLEManagerMain.meetingRoomBLEPositionList);
            BLEManagerMain.reinitializeKalmanFiltersAndPositions();
        });

        buttonMCPEArea.setOnClickListener(v -> {
            BLEManagerMain.setBeaconList(BLEManagerMain.mcpeBLEBeaconList, BLEManagerMain.mcpeAreaBLEPositionList);
            BLEManagerMain.reinitializeKalmanFiltersAndPositions();
        });


//        Button buttonPause = findViewById(R.id.buttonPause);
        buttonStop.setOnClickListener(v -> {
            stopTasks();
            clearMap();
        });

        buttonUSB.setOnClickListener(v -> {
            stopTasks();
            mode = "USB";
            clearMap();
            if(dataProcessor!=null) {
                dataProcessor.stopDisplaying();
            }
            imageView_map.startAddingPoints();

            initializeManagers();
            startTasks();
        });

        buttonWifi.setOnClickListener(v -> {
            stopTasks();
            mode = "WIFI";
            clearMap();
            imageView_map.startAddingPoints();

            initializeManagers();
            startTasks();
        });

        buttonBLE.setOnClickListener(v -> {
            stopTasks();
            mode = "BLE";
            clearMap();
            imageView_map.startAddingPoints();

            initializeManagers();
            startTasks();
        });

        buttonBLESerial.setOnClickListener(v ->{
            stopTasks();
            mode = "BLE Serial";
            clearMap();
            imageView_map.startAddingPoints();

            initializeManagers();
            startTasks();
        });
    }

    private void setupViews() {
        imageView_map = findViewById(R.id.imageView_map);
        // drag
        scaleanddrag = new scaleanddrag();
        imageView_map.setOnTouchListener(scaleanddrag::onTouch);

    }

    private void initializeManagers() {
        permissionManager = new PermissionManager(this);
        pdrKalman = new PDRKalman();
        StepDetection  = new StepDetection(this, pdrKalman);
        SensorManagerMain  = new SensorManagerMain(this);
        SensorManagerMain.setCallback(StepDetection);
        usbSerialManager = new UsbSerialManager(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));
        wifiManager = new WifiManagerMain(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));
        BLEManagerMain = new BLEManagerMain(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text), pdrKalman);
        BLEserialManager = new BLEserialManager(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));
    }

    private void resetViewsAndManagers() {
        stopTasks();
        setupViews();
        initializeManagers();
    }

    private void startTasks() {
        permissionManager.requestInitialPermissions();
        if (mode.equals("USB")) {
            usbSerialManager.start();
            usbSerialManager.startRepeatingTask();
        } else if (mode.equals("WIFI")){
            wifiManager.startWifiScan();
            wifiManager.startRepeatingTask();
        } else if (mode.equals("BLE")) {
            BLEManagerMain.startBleScan();
            BLEManagerMain.startRepeatingTask();
        } else {
            BLEserialManager.start();
            BLEserialManager.startRepeatingTask();
        }
    }

    private void stopTasks() {

        if (wifiManager != null) {
            wifiManager.cleanup();
            wifiManager = null;
        }

        if (usbSerialManager != null && usbSerialManager.connected){
            usbSerialManager.cleanup();
            usbSerialManager = null;
        }

        if (BLEManagerMain != null){
            BLEManagerMain.cleanup();
            BLEManagerMain = null;
        }

        if (BLEserialManager != null) {
            BLEserialManager.cleanup();
            BLEserialManager = null;
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTasks();
    }
    public static void addPointsToScaledMap_imageView(Float x, Float y) {
        imageView_map.addPointsToScaledMap(x, y);
    }

    public static void addGroundTruthToScaledMap_imageView(Float x, Float y) {
        imageView_map.addGroundTruthToScaledMap(x, y);
    }

    public static void clearMap() {
        imageView_map.clearpoints();
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
        };
        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    // Check if the necessary permissions have been granted
    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // Permissions were granted, initialize managers and start tasks
                initializeManagers();
                startTasks();
            } else {
                // Handle permission denial
                Toast.makeText(this, "Permissions are required to proceed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onAccelerometerData(float x, float y, float z, long timestamp) {

    }

    @Override
    public void onRotationVectorData(float[] rotationVector) {

    }

    @Override
    public void onOrientationData(float azimuthInDegrees, String direction) {

    }
}
