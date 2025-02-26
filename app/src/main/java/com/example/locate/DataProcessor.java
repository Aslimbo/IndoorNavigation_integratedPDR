package com.example.locate;

import android.content.Context;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created on 2024/6/20 17:11
 * Author: ZST
 */
public class DataProcessor {
    private static Handler handler = new Handler();
    private Runnable readRunnable;
    private static BufferedReader reader;
    private static BufferedReader groundtruthReader;
    private static ArrayList<String> wifiBlock = new ArrayList<>();
    private TextView tvStep;
    private TextView tvHeading;
    private TextView display;
    private Context context;
    private static Module module;
    private static HashMap<String, Integer> bssidDi = new HashMap<>();
    private static ArrayList<Integer> bssids = new ArrayList<>();
    private static List<Double> filterBuffer;
    private static final int FILTER_SIZE = 50;
    private static final double STEP_LENGTH = 0.45;  // TODO: 这里假设步长固定，实际场景应改为自适应步长
    private static int detect;
    private static int detect0;
    private static int steps;  // 记录步数
    private double heading = 0;
    private static double a1;
    private static double b1;
//    private PlotMap plotMap;
    private static Fusion kfX;
    private static Fusion kfY;
    private static int initial = 0;  // 是否已经得到初始位置（通过wifi得到的第一个绝对位置）
    private static final Map<Integer, String> floorMap = new HashMap<>();
    private String floor;

    public DataProcessor(Context context) {
        this.context = context;
        this.kfX = new Fusion();
        this.kfY = new Fusion();

        filterBuffer = new ArrayList<>();
        for (int i = 0; i < FILTER_SIZE; i++) {
            filterBuffer.add(0.0);
        }
        steps = 0;
        detect0 = 0;
        detect = 0;

        floorMap.put(0, "F1");
        floorMap.put(1, "F2");
        floorMap.put(2, "F3");
        floorMap.put(3, "F4");
        floorMap.put(4, "F5");
        floorMap.put(5, "F6");
        floorMap.put(6, "F7");
        floorMap.put(7, "F8");
        floorMap.put(8, "F9");
        floorMap.put(9, "F10");
        floorMap.put(10, "F11");
        floorMap.put(-1, "B1");
        floorMap.put(-2, "B2");

        loadData();
    }

    private void loadData() {
        try {
            module = Module.load(assetFilePath(context, "modelF11_2.pt"));

            InputStream is = context.getAssets().open("bssiddiF11_2.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int value = jsonObject.getInt(key);
                bssidDi.put(key, value);
            }

            InputStream is2 = context.getAssets().open("bssidsF11_2.json");
            int size2 = is2.available();
            byte[] buffer2 = new byte[size2];
            is2.read(buffer2);
            is2.close();
            String json2 = new String(buffer2, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(json2);
            for (int i = 0; i < jsonArray.length(); i++) {
                bssids.add(jsonArray.getInt(i)); // 解析为Integer
            }

            String pathNum = "16";
            InputStream is3 = context.getAssets().open(pathNum+"_imuAndwifi.txt");
            InputStream is4 = context.getAssets().open("path"+pathNum+"_Location_result_.txt");
//
            reader = new BufferedReader(new InputStreamReader(is3, StandardCharsets.UTF_8));
            groundtruthReader = new BufferedReader((new InputStreamReader(is4, StandardCharsets.UTF_8)));

        } catch (IOException | JSONException e) {
//            Log.e("loadFiles", "Error reading assets", e);
            e.printStackTrace();
        }
    }

    public void readData() {
        readRunnable = new Runnable() {
            public void run() {
                try {
                    String line;
                    String timestamp = "";
//                    reader.reset();

                    // Read the "pathXX_Location_result_.txt" and add the groundTruth to the map
                    while ((line = groundtruthReader.readLine()) != null) {
                        String[] lineData = line.split("\t");
                        float[] groundTruth = {Float.parseFloat(lineData[2]), Float.parseFloat(lineData[3])};
                        MainActivity.addGroundTruthToScaledMap_imageView(groundTruth[0], groundTruth[1]);
                    }


                    // Read the "XX_imuAndwifi.txt", use the information inside as input and let the model predict the user location
                    while ((line = reader.readLine()) != null) {
                        String[] lineData = line.split("\t");
                        String tempTimeStamp = "";

                        // This section of code do the prediction when it met wifi information
                        // Met "TYPE_WIFI" line --> Store the Same timestamp 's wifi lines --> pick the first 20 strongest signal strength wifi line --> Predict
//                          lineData:
//                          [0]: timestamp
//                          [1]: "TYPE_WIFI"
//                          [2]: wifi name
//                          [3]: BSSID / fingerprint
//                          [4]: RSSI / signal strength

                        if (lineData[1].equals("TYPE_WIFI")) {
                            if(timestamp.isEmpty()){
                                timestamp = lineData[0];
                            }
                            tempTimeStamp = lineData[0];

                            if(timestamp.equals(tempTimeStamp)){
                                wifiBlock.add(line);        // Store wifi data from same timestamp into a block
                            } else {                        // if it met wifi data from different/new timestamp, process the stored old wifi data
                                processBlock(wifiBlock);  // wifi fingerprints计算位置

                                wifiBlock.clear();      // clear wifi block storage, so it can store the wifi data from next timestamp

                                timestamp = tempTimeStamp;  // update timestamp
                                wifiBlock.add(line);        // add the new wifi data from the new timestamp
                            }

                        } else {                                            // Process wifi data and do prediction when it read new data that is not wifi (i.e IMU data)
                            if (!wifiBlock.isEmpty()) {
                                processBlock(wifiBlock);  // wifi fingerprints计算位置
                                wifiBlock.clear();
                            }
                            if (lineData[1].equals("TYPE_LINEAR_ACCELERATION") && initial == 1) {
//                                stepDetection(lineData);
                            }
                            if (lineData[1].equals("TYPE_ROTATION_VECTOR") && initial == 1) {
//                                calculateDirection(lineData);
                            }
                            handler.postDelayed(this, 2);  // 模拟信号接收间隔
                            return;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        handler.post(readRunnable);
    }

    static void stepDetection(float[] values) {
        double t = values[0];
        double x = values[1];
        double y = values[2];
        double z = values[3];
        double a = Math.sqrt(x * x + y * y + z * z + 100);
        long timestamp = new Date().getTime();


//        filterBuffer.remove(0);
//        filterBuffer.add(a);
//
//        double fa = Mean(filterBuffer, FILTER_SIZE);
//        final double threshold = 10.3;
//        detect = (fa > threshold) ? 1 : 0;

        if (a < 14.2 && a > 13.8) {
//            if( Constants.static_timestamp_start_recorded == false){
//                Constants.static_timestamp_start_recorded = true;
//                Constants.static_timestamp_start = timestamp;
//            }
//            else{
//                if (timestamp - Constants.static_timestamp_start > 1000){
//                    Constants.moving = false;
//
//                }
//            }
//            Log.d("notmoving", String.valueOf(a));
            Constants.moving = false;
        }
        else {
            steps++;
            Constants.moving = true;
//                Constants.static_timestamp_start_recorded = false;



//            Log.d("moving", String.valueOf(a));

//            plotMap.addPointToMap(a1, b1);
//            plotMap.addPointToMap2(a1, b1);


//            String outputString = String.format(Locale.ENGLISH, "%.2f, %.2f, %s", a1, b1, floor);
//            display.setText(outputString);
//            Log.d("fusion", "position: [" + a1 + ", " + b1 + "]");

//            calculatePosition();
        }
//        tvStep.setText("Steps: " + steps);
        detect0 = detect;
    }

    static void oldstepDetection(float[] values) {
        double x = values[1];
        double y = values[2];
        double z = values[3];
        double a = Math.sqrt(x * x + y * y + z * z + 100);
        long timestamp = new Date().getTime();

        filterBuffer.remove(0);
        filterBuffer.add(a);

        double fa = Mean(filterBuffer, FILTER_SIZE);
        final double threshold = 10.3;
//        Log.d("fao", String.valueOf(fa));
//        Log.d("threshold", String.valueOf(threshold));


        detect = (fa > threshold) ? 1 : 0;
//        Log.d("detect", String.valueOf(detect));
        if (fa > 14.2) {
            Constants.moving = true;

        }else{
            Constants.moving = false;

        }
//        if (detect - detect0 == 1) {
//            Constants.static_timestamp_start = timestamp;
//            steps++;
//            Constants.moving = true;
//        }
//        if ((timestamp - Constants.static_timestamp_start) > 1000){
//            Constants.moving = false;
//        }
//        detect0 = detect;
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
        tvHeading.setText("Heading: " + String.format("%.2f", heading));
    }

//    private void calculatePosition() {
//        double radians = Math.toRadians(heading);
//        double deltaX = STEP_LENGTH * Math.sin(radians);
//        double deltaY = STEP_LENGTH * Math.cos(radians);
//
//        kfX.predict(deltaX, 1);
//        kfY.predict(deltaY, 1);
//        a1 = kfX.getState();
//        b1 = kfY.getState();
//        MainActivity.addPoints_imageView((float) a1, (float) b1);
//    }

    private static double Mean(List<Double> values, int size) {
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += values.get(i);
        }
        return sum / size;
    }

    static float[] processBlock(ArrayList<String> block) {
        float[] result;
        float rssi;
        int size = Constants.inputSize * Constants.inputSize;
        float[] fingerprints  = new float[size];
        Arrays.fill(fingerprints, 0);
        int lineCount = 0;
        for (String line : block) {
            if (lineCount >= 20) break;
            // // TODO: assigning 20 lines to input block (fingerprints) now this block is mostly the same number
            String[] parts = line.split("\t");
            Integer bssid = bssidDi.getOrDefault(String.valueOf(parts[3]), -1);
            if (bssid!=-1) {
                int idx = bssid;
                try {
                    rssi = Float.parseFloat(parts[4]);
//                    Log.d("RSSI",idx +" "+ String.valueOf(rssi));
                } catch (NumberFormatException e) {

//                    System.err.println("Unable to transition: " + parts[4]);
                    rssi = Constants.rssiRange[0];
                }
                fingerprints[idx] = (Math.max(Constants.rssiRange[0], Math.min(rssi, Constants.rssiRange[1])) - Constants.rssiRange[0]) / (Constants.rssiRange[1] - Constants.rssiRange[0]);
            }
            lineCount++;
//            handler.postDelayed((Runnable) this, 2);  // 模拟信号接收间隔

        }
//        Log.d("fingerprints", Arrays.toString(fingerprints));
        Tensor input = Tensor.fromBlob(fingerprints, new long[]{1, 1, 16, 16});
        Tensor output = module.forward(IValue.from(input)).toTensor();
        result = output.getDataAsFloatArray();

//        MainActivity.addPointsToScaledMap_imageView((float) result[0] * 100, (float)  result[1]* 100);

//        Log.d("CNN", "wifi result: " + Arrays.toString(result));

        return result;
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public static String mapFloor(double input) {
        int roundedValue = (int) Math.round(input);
        String floorResult = floorMap.get(roundedValue);
        if (floorResult == null) {  // 如果找不到对应的楼层，返回一个默认值
            return "Unknown Floor";
        }
        return floorResult;
    }

    public void stopDisplaying() {
        handler.removeCallbacks(readRunnable);
        loadData();
    }

}
