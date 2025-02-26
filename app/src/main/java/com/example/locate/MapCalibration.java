package com.example.locate;

import android.location.Location;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class MapCalibration {
    private static final Map<String, Float> DEVICE_OFFSETS = new HashMap<>();
    private static Location mockLocation = new Location("mock"); // 香港中心坐标

    static {
        // Victoria Harbor, Hong Kong (22.2795° N, 114.1628° E)
        mockLocation.setLatitude(22.2795);
        mockLocation.setLongitude(114.1628);

        DEVICE_OFFSETS.put("pixel 7", 95f);      // Google Pixel 7
        DEVICE_OFFSETS.put("sm-g991b", -48f);    // 三星 Galaxy S21
        DEVICE_OFFSETS.put("xiaomi 14", 182f);   // 小米14
    }

    public static float getSensorOffset(String model) {
        String normalizedModel = model.toLowerCase();

        // 处理常见设备别名
        if (normalizedModel.contains("pixel 7")) {
            return DEVICE_OFFSETS.get("pixel 7");
        } else if (normalizedModel.contains("xiaomi 14")) {
            return DEVICE_OFFSETS.get("xiaomi 14");
        } else if (normalizedModel.contains("galaxy s21")) {
            return DEVICE_OFFSETS.get("sm-g991b");
        }

        return DEVICE_OFFSETS.getOrDefault(normalizedModel, 0f);
    }

    // 香港地区磁偏角（2023年数据）
    public static float getMagneticDeclination() {
        return 2.3f;
    }

    public static void initLocation(Location location) {
        if (location != null) {
            mockLocation = location;
        }
    }

    public static Location getLastLocation() {
        return mockLocation;
    }
}