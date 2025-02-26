package com.example.locate;

import java.util.Arrays;
import java.util.List;

/**
 * Created on 2024/4/15 15:55
 * Author: ZST
 */

public class Constants {
    public static final boolean experiment = false;
    public static final int REQUEST_FINE_LOCATION = 100;
    public static final int REQUEST_WRITE_EXTERNAL_STORAGE = 101;
    public static final int REQUEST_READ_EXTERNAL_STORAGE = 102;
    public static final int REQUEST_BLUETOOTH = 103;
//    public static final String absoluteFilePath = "/storage/emulated/0/Download/example.txt";
    public static final long simuWifiInterval = 2 * 1000;
    public static final long simuImuInterval = 20;
    public static final int inputSize = 16;
    public static final float[] rssiRange = new float[]{-100.0f, -30.0f};

    public static float mapWidth = (float)(2643.49);
    public static float mapHeight = (float)(3264.33);
    public static boolean moving = true;
    public static float lastmoving_timestamp = 0;
    public static float static_timestamp_start;
    public static boolean static_timestamp_start_recorded = false;

    public static double smartphone_height = 1.15;
    public static double beacon_height = 1.75; // 2.75
    public static double ap_height = 2.8;


//  Constant parameters For Kalman Filter
    public static double Q = 0.001; // Process noise covariance
    public static double R = 0.1;   // Measurement noise covariance
    public static double F = 1.0;   // State transition matrix
    public static double G = 0.0;   // Control input matrix (not used in this example)
    public static double C = 1.0;   // Measurement matrix

//////////////////////////////////////////////////////////////
    public static final List<String> mainOfficeWiFiList = Arrays.asList(
            "dc:ae:eb:05:4b:e8",
            "dc:ae:eb:05:58:c8",
            "dc:ae:eb:05:2a:18",
            "70:ca:97:03:21:78",
            "70:ca:97:2b:09:f8"
    );

    public static final List<String> mainOfficeWiFiPhoneList = Arrays.asList(
            "7e:fe:8a:e8:fc:2f",
            "ce:40:67:02:ac:6c",
            "62:80:af:04:3e:b7",
            "fe:14:2d:b3:15:2f",
            "da:b0:ef:e1:76:e7"
    );


    public static final List<String> mainOfficeHotSpotList = Arrays.asList(
//            "d0:a0:11:dd:c0:17",
            "e8:de:27:e3:fe:2a",
            "d0:a0:11:da:76:42",
            "d0:a0:11:db:2d:f8",
            "d0:a0:11:db:2f:b3",
            "d0:a0:11:db:0d:14"

    );

    public static final List<String> mainOfficeWiFiPositionList = Arrays.asList(
            "5.19, 5.19",
            "12.76, 5.03",
            "4.82, 11.89",
            "12.75, 12.14",
            "21.77, 11.26"
    );

    public static final List<String> mainOfficeWiFiPhonePositionList = Arrays.asList(
            "0.58, 3.65",
            "17.58, 3.76",
            "17.10, 13.79",
            "2.23, 12.57",
            "9.12, 8.29"
    );


    public static final double[] MainOfficeWiFiRefDistanceList = {1.6, 1.8, 1.1, 1.1, 1.2};

    public static final double[] MainOfficeWiFiRefRssi = {-44.0, -45.0, -41.0, -41.0, -42.0};

    public static final String obstaclefilename = "obstacleRegions1" + ".txt";

    static final String INTENT_ACTION_DISCONNECT = "com.example.locate.Disconnect";


    // For BLE Serial device
    public static final String deviceAddress = "98:3D:AE:EA:19:22"; // 8C:BF:EA:CB:AC:9E (Xiao)
    public static final String serviceUUID= "0000abf0-0000-1000-8000-00805f9b34fb";
    public static final String readCharacteristicUUID = "0000abf2-0000-1000-8000-00805f9b34fb";
    public static final String writeCharacteristicUUID = "0000abf1-0000-1000-8000-00805f9b34fb";

}
