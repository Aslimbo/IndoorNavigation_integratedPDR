package com.example.locate;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.ViewDebug;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.androidplot.Plot;
import com.androidplot.ui.HorizontalPositioning;
import com.androidplot.ui.VerticalPositioning;
import com.androidplot.util.PixelUtils;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.FastLineAndPointRenderer;

import org.json.JSONObject;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created on 2024/5/20 17:19
 * Author: ZST
 */

public class SensorCalculate implements SensorEventListener {
    private static final int FILTER_SIZE = 80;
    private static final double STEP_LENGTH = 0.2;
    private int detect;  // 当前加速度检测状态
    private int detect0;  // 前一次加速度检测状态
    private int steps;  // 记录步数
    private double width;
    private double height;

    private Context context;
    private SensorManager sensorCalculateManager;
    private Sensor accelerometer;
    private Sensor rotationVectorSensor;
//    private TextView tvStep;
//    private TextView tvHeading;

    private List<Double> filterBuffer;
    private double heading = 0;

    private double a1 = 100, b1 = 100; // Current position
    private double a0 = 100, b0 = 100; // Previous position

    private XYPlot tracePlot;
    private LineAndPointFormatter red = null;  // 用于格式化绘制的红色线条和点的格式化器
    private SimpleXYSeries gtXYSeries = null;  // 数据序列，存储ground truth
    private LineAndPointFormatter blue = null;  // 用于格式化绘制的蓝色线条和点的格式化器
    private SimpleXYSeries traABSeries = null;  // 数据序列，存储轨迹
    private Redrawer redrawer;

    public SensorCalculate(Context context, TextView tvStep, TextView tvHeading, XYPlot plot) {
        this.context = context;
        this.sensorCalculateManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
//        this.tvStep = tvStep;
//        this.tvHeading = tvHeading;
        this.tracePlot = plot;

        // Initialize
        filterBuffer = new ArrayList<>();
        for (int i = 0; i < FILTER_SIZE; i++) {
            filterBuffer.add(0.0);
        }
        steps = 0;
        detect0 = 0;
        detect = 0;

        // Configure plot
        configurePlot();

        // Register sensors
        registerSensors();
    }

    private void configurePlot() {
//        Paint mPaint = new Paint();
//        mPaint.setColor(Color.TRANSPARENT);
        tracePlot.setBackgroundPaint(null);
        tracePlot.getGraph().setBackgroundPaint(null);
        tracePlot.getGraph().setGridBackgroundPaint(null);

        loadFloorInfo();
        double aspectRatio = width / height;
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) tracePlot.getLayoutParams();
        params.dimensionRatio = "H," + aspectRatio + ":1";
        tracePlot.setLayoutParams(params);

        // 设置图例的位置和大小
        tracePlot.getLegend().setVisible(true);
        tracePlot.getLegend().setWidth(0.2f); // 设为总宽度的20%
        tracePlot.getLegend().setHeight(0.1f); // 设为总高度的10%
        tracePlot.getLegend().position(
                50, HorizontalPositioning.ABSOLUTE_FROM_LEFT,
                50, VerticalPositioning.ABSOLUTE_FROM_BOTTOM);  // 距离图的下边界xx像素
        tracePlot.getLegend().getTextPaint().setTextSize(PixelUtils.dpToPix(10)); // 设置图例文字
        tracePlot.getLegend().getTextPaint().setColor(Color.BLACK);

        gtXYSeries = new SimpleXYSeries("gt");
        red = new FastLineAndPointRenderer.Formatter(null, Color.RED, null);
        tracePlot.addSeries(gtXYSeries, red);

        gtXYSeries.addLast(0, 0);  // 添加初始点到gtXYSeries
        gtXYSeries.addLast(width, height);  // 添加初始点到gtXYSeries

        traABSeries = new SimpleXYSeries("path");
        blue = new FastLineAndPointRenderer.Formatter(null, Color.BLUE, null);
        tracePlot.addSeries(traABSeries, blue);

        tracePlot.setRangeBoundaries(0, height, BoundaryMode.FIXED);
        tracePlot.setDomainBoundaries(0, width, BoundaryMode.FIXED);

        redrawer = new Redrawer(tracePlot, 200, true);
    }

    private void registerSensors() {
        accelerometer = sensorCalculateManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationVectorSensor = sensorCalculateManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorCalculateManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorCalculateManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            stepDetection(event);
        } else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            calculateDirection(event);
        }
    }

    private void stepDetection(SensorEvent event) {
        double x = event.values[0];
        double y = event.values[1];
        double z = event.values[2];
        double a = Math.sqrt(x * x + y * y + z * z);

        filterBuffer.remove(0);
        filterBuffer.add(a);

        double fa = Mean(filterBuffer, FILTER_SIZE);
//        tvAccelerometer.setText("Filtered Accel: " + String.format("%.2f", fa));

        final double threshold = 10.5;
        detect = (fa > threshold) ? 1 : 0;

        if (detect - detect0 == 1) {
            steps++;
            traABSeries.addLast(a1,b1);  // 将当前的位置 (a1, b1) 添加到绘图序列 traABSeries 中，用于在屏幕上显示轨迹
            calculatePosition();

        }
//        tvStep.setText("Steps: " + steps);

        detect0 = detect;
    }

    private void calculateDirection(SensorEvent event) {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        heading = Math.toDegrees(orientation[0]);
//        tvHeading.setText("Heading: " + String.format("%.2f", heading));
    }

    private void calculatePosition() {
        double radians = Math.toRadians(heading);
        a1 = a0 + STEP_LENGTH * Math.sin(radians);
        b1 = b0 + STEP_LENGTH * Math.cos(radians);
        a0 = a1;
        b0 = b1;
    }

    private double Mean(List<Double> values, int size) {
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += values.get(i);
        }
        return sum / size;
    }

    private void loadFloorInfo() {
        try {
            InputStream is = context.getAssets().open("floor_info.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            JSONObject obj = new JSONObject(json);
            JSONObject mapInfo = obj.getJSONObject("map_info");
            width = mapInfo.getDouble("width");
            height = mapInfo.getDouble("height");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    public void cleanup() {
        sensorCalculateManager.unregisterListener(this);
        tracePlot.clear();
        redrawer.finish();
    }
}
