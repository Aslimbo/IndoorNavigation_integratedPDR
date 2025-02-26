package com.example.locate;

import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Created on 2024/4/16 10:41
 * Author: ZST
 */

public class WifiReader {
    private Handler handler = new Handler();
    private Runnable readRunnable;
    private static BufferedReader reader;
    private static ArrayList<String> wifiBlock = new ArrayList<>();
    private TextView display;
    private static long interval;
    private Context context;
    private Module module;
    private HashMap<String, Integer> bssidDi = new HashMap<>();
    private ArrayList<Integer> bssids = new ArrayList<>();
    private PlotMap plotMap;
    private ImuReader imuReader;

    public WifiReader(Context context, TextView displayView, PlotMap plotMap) {
        this.context = context;
        this.display = displayView;
        interval = Constants.simuWifiInterval;
        this.plotMap = plotMap;
        loadData();
    }

    public void setImuReader(ImuReader imuReader) {
        this.imuReader = imuReader;
    }

    private void loadData() {
        try {
            module = Module.load(assetFilePath(context, "model.pt"));

            InputStream is = context.getAssets().open("bssiddi.json");
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

            InputStream is2 = context.getAssets().open("bssids.json");
            int size2 = is2.available();
            byte[] buffer2 = new byte[size2];
            is2.read(buffer2);
            is2.close();
            String json2 = new String(buffer2, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(json2);
            for (int i = 0; i < jsonArray.length(); i++) {
                bssids.add(jsonArray.getInt(i)); // 解析为Integer
            }

            InputStream is3 = context.getAssets().open("wifidata.txt");
            reader = new BufferedReader(new InputStreamReader(is3, StandardCharsets.UTF_8));

        } catch (IOException | JSONException e) {
            Log.e("loadFiles", "Error reading assets", e);
            e.printStackTrace();
        }
    }

    public void readWifiBlock() {
        readRunnable = new Runnable() {
            public void run() {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] lineData = line.split("\t");
                        if (!line.isEmpty()) {
                            wifiBlock.add(line);
                        }
                        else {
                            if (!wifiBlock.isEmpty()) {
                                float[] output = processBlock(wifiBlock);
                                String outputString = String.format(Locale.ENGLISH, "%.2f, %.2f, %d", output[0], output[1], Math.round(output[2]));
                                plotMap.addPointToMap(output[0], output[1]);
                                display.setText(outputString);
                                wifiBlock.clear();
                                if (imuReader != null) {
                                    imuReader.updateLocation(output[0], output[1]);
                                }
                                handler.postDelayed(this, interval);
                                return;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        handler.post(readRunnable);
    }

    private float[] processBlock(ArrayList<String> block) {
        float[] result;
        float rssi;
        int size = Constants.inputSize * Constants.inputSize;
        float[] fingerprints  = new float[size];
        Arrays.fill(fingerprints, Constants.rssiRange[0]);
        int lineCount = 0;
        for (String line : block) {
            if (lineCount >= 20) break;
            String[] parts = line.split("\t");
            Integer bssid = bssidDi.getOrDefault(parts[3], -1);
            if (bssids.contains(bssid)) {
                int idx = bssids.indexOf(bssid);
                try {
                    rssi = Float.parseFloat(parts[4]);
                } catch (NumberFormatException e) {
                    System.err.println("Unable to transition: " + parts[4]);
                    rssi = Constants.rssiRange[0];
                }
                fingerprints[idx] = (Math.max(Constants.rssiRange[0], Math.min(rssi, Constants.rssiRange[1])) - Constants.rssiRange[0]) / (Constants.rssiRange[1]- Constants.rssiRange[0]);
            }
            lineCount++;
        }
        Tensor input = Tensor.fromBlob(fingerprints, new long[]{1, 1, 48, 48});
        Tensor output = module.forward(IValue.from(input)).toTensor();
        result = output.getDataAsFloatArray();
        Log.d("CNN", "location result" + Arrays.toString(result));

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

    public void stopDisplaying() {
        handler.removeCallbacks(readRunnable);
    }
}
