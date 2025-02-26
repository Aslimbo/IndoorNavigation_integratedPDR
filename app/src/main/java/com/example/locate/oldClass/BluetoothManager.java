package com.example.locate;

import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.TextView;
import android.os.Handler;

import java.util.Collections;
import java.util.HashMap;
import android.util.Log;

/**
 * Created on 2024/4/15 15:55
 * Author: ZST
 */

public class BluetoothManager {

    private BluetoothAdapter bluetoothAdapter;
    private Context context;
    private TextView tvBluetoothInfo; // UI component to display Bluetooth device info
    private HashMap<String, String> deviceMap = new HashMap<>();
    private Handler bluetoothScanHandler = new Handler();
    private Runnable bluetoothScanRunnable = new Runnable() {
        @Override
        public void run() {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                startBluetoothDiscovery();
            }
            bluetoothScanHandler.postDelayed(this, 1000); // Repeat every 30 seconds
        }
    };

    public BluetoothManager(Context context) {
        this.context = context;
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Handle lack of Bluetooth support
            tvBluetoothInfo.setText("Bluetooth not supported on this device");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            context.startActivity(enableBtIntent);
            // Assuming enabling will be successful, setup discovery
        }
        setupBluetoothDiscovery();
    }

    private void setupBluetoothDiscovery() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(bluetoothReceiver, filter);
        startBluetoothDiscovery();
    }

    public void startBluetoothDiscovery() {
//        if (bluetoothAdapter.isDiscovering()) {
//            bluetoothAdapter.cancelDiscovery();
//        }
        bluetoothAdapter.startDiscovery();
//        Log.d("Bluetooth", "Starting Bluetooth scan...");
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                String deviceInfo = "Device: " + device.getName() + " (" + device.getAddress() + "), RSSI: " + rssi + "dBm\n";
                deviceMap.put(device.getAddress(), deviceInfo);
                Log.d("deviceMap", String.valueOf(deviceMap));
                updateBluetoothDisplay();
//            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//                startBluetoothDiscovery(); // Restart discovery after it finishes
            }
        }
//        @Override
//        public void onScanResult(int callbackType, ScanResult result) {
////                    super.onScanResult(callbackType, result);
////            filewriter.BLEResultsToFile(Collections.singletonList(result));
//            Log.d("BLEscan", String.valueOf(result));
////
//        }
    };

    private void updateBluetoothDisplay() {
        StringBuilder sb = new StringBuilder("BLE:\n");
        for (String deviceInfo : deviceMap.values()) {
            sb.append(deviceInfo);
        }
    }

    public void startRepeatingTask() {
        bluetoothScanRunnable.run();
    }

    public void stopRepeatingTask() {
        bluetoothScanHandler.removeCallbacks(bluetoothScanRunnable);
    }

    public void cleanup() {
        stopRepeatingTask();
        context.unregisterReceiver(bluetoothReceiver);
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
    }
}
