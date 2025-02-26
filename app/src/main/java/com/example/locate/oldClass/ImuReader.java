package com.example.locate;

import android.content.Context;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Created on 2024/6/5 11:20
 * Author: ZST
 */

public class ImuReader {
    private Handler handler = new Handler();
    private Runnable readRunnable;
    private static BufferedReader reader;
//    private TextView tvStep;
//    private TextView tvHeading;

    private static long interval;
    private Context context;
    private List<Double> filterBuffer;
    private static final int FILTER_SIZE = 10;
    private static final double STEP_LENGTH = 0.4;
    private int detect;  // 当前加速度检测状态
    private int detect0;  // 前一次加速度检测状态
    private int steps;  // 记录步数
    private double heading = 0;
    private double a1 = 0, b1 = 0;
    private double a0 = 0, b0 = 0;
    private double absoluteX = -100, absoluteY = -100;
    private PlotMap plotMap;


    public ImuReader(Context context, TextView tvStep, TextView tvHeading, PlotMap plotMap) {
        this.context = context;
        interval = Constants.simuImuInterval;
//        this.tvStep = tvStep;
//        this.tvHeading = tvHeading;
        this.plotMap = plotMap;

        filterBuffer = new ArrayList<>();
        for (int i = 0; i < FILTER_SIZE; i++) {
            filterBuffer.add(0.0);
        }
        steps = 0;
        detect0 = 0;
        detect = 0;

        loadData();
    }

    private void loadData() {
        try {
            InputStream is3 = context.getAssets().open("imudata.txt");
            reader = new BufferedReader(new InputStreamReader(is3, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e("loadFiles", "Error reading assets", e);
            e.printStackTrace();
        }
    }

    public void readImuBlock() {
        readRunnable = new Runnable() {
            public void run() {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] lineData = line.split("\t");

                        if (lineData[1].equals("TYPE_ACCELEROMETER")) {
                            stepDetection(lineData);
                        }
                        if (lineData[1].equals("TYPE_ROTATION_VECTOR")) {
                            calculateDirection(lineData);
                        }
                        handler.postDelayed(this, interval);
                        return; // 退出当前循环
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        handler.post(readRunnable);
    }

    private void stepDetection(String[] lineData) {
        double x = Double.parseDouble(lineData[2]);
        double y = Double.parseDouble(lineData[3]);
        double z = Double.parseDouble(lineData[4]);
        double a = Math.sqrt(x * x + y * y + z * z);

        filterBuffer.remove(0);
        filterBuffer.add(a);

        double fa = Mean(filterBuffer, FILTER_SIZE);
        final double threshold = 10.3;
        detect = (fa > threshold) ? 1 : 0;
//        Log.d("step", "filter acce " + fa);
//        Log.d("step", "detect " + detect);
//        Log.d("step", "detect0 " + detect0);

        if (detect - detect0 == 1) {
            steps++;
            plotMap.addPointToMap2(a1, b1);
            Log.d("step", "a " + a1);
            Log.d("step", "b " + b1);
            calculatePosition();
        }
//        tvStep.setText("Steps: " + steps);
        detect0 = detect;
    }

    private void calculateDirection(String[] lineData) {
        float[] rotationVector = new float[3];
        rotationVector[0] = Float.parseFloat(lineData[2]);
        rotationVector[1] = Float.parseFloat(lineData[3]);
        rotationVector[2] = Float.parseFloat(lineData[4]);
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        heading = Math.toDegrees(orientation[0]);
//        tvHeading.setText("Heading: " + String.format("%.2f", heading));
    }

    private void calculatePosition() {
        double radians = Math.toRadians(heading);

        if (a1 == 0) {
            a1 = a0 + STEP_LENGTH * Math.sin(radians) + absoluteX;
            b1 = b0 + STEP_LENGTH * Math.cos(radians) + absoluteY;
        }
        else {
            a1 = a0 + STEP_LENGTH * Math.sin(radians);
            b1 = b0 + STEP_LENGTH * Math.cos(radians);
        }
        a0 = a1;
        b0 = b1;
//        a1 = a0 + STEP_LENGTH * Math.sin(radians);
//        b1 = b0 + STEP_LENGTH * Math.cos(radians);
//        a0 = a1;
//        b0 = b1;
    }

    private double Mean(List<Double> values, int size) {
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += values.get(i);
        }
        return sum / size;
    }

    public void updateLocation(double x, double y) {
        this.absoluteX = x;
        this.absoluteY = y;
//        a0 = 0;
//        b0 = 0;
//        Log.d("LocationUpdate", "absoluteX: " + absoluteX + ", absoluteY: " + absoluteY);
    }


    public void stopDisplaying() {
        handler.removeCallbacks(readRunnable);
    }
}
