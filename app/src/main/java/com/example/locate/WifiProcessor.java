package com.example.locate;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Random;

public class WifiProcessor {
    private static final int MAP_SIZE = 17;
    private static final int NUM_APS = 4; // Number of Access Points
    private static final int SWARM_SIZE = 500;
    private static final int MAX_ITERATIONS = 2000;
    private static final double FUNCTION_TOLERANCE = 1e-8;
    private static int OPTIMIZED_APS = 1;
    private static int PATIENCE = 100;
    private static double[][] psoBoundary;

    public static void processWifiBlock(double[][] apPositions, double[] rssiMeasurements, double[][] psoBoundary,
                                        double[] distance_ref, double [] rssi_ref, TextView tvPSOResult) {
        PSO pso = new PSO(SWARM_SIZE, NUM_APS, OPTIMIZED_APS, MAP_SIZE, MAX_ITERATIONS, FUNCTION_TOLERANCE, PATIENCE, psoBoundary);
//        double[] estimatedParams = pso.optimize_adjusted(apPositions, rssiMeasurements, distance_ref, rssi_ref);
        double[] estimatedParams = pso.optimize(apPositions, rssiMeasurements);
        double error_record = pso.getgBestFitness();

        // Extract estimated position, RSSI, and n
        double[] estimatedPosition = Arrays.copyOfRange(estimatedParams, 0, 2);
        double estimatedN = estimatedParams[2];
        double[] estimatedRSSI0 = Arrays.copyOfRange(estimatedParams, 3, 3 + NUM_APS);

//        Log.d("endtimestamp", "");
//        Log.d("estimated position WiFi", "x = " + estimatedPosition[0] + " y = " + estimatedPosition[1]);
//        Log.d("estimated position error", String.valueOf(custommath.calculateDistance(13.73F, 8.9F, (float) estimatedPosition[0], (float) estimatedPosition[1])));
//        Log.d("estimatedN", String.valueOf(estimatedN));
//        Log.d("estimatedRSSI0", Arrays.toString(estimatedRSSI0));
//        Log.d("psoError", String.valueOf(error_record));

        // Display the PSO errors
        new Handler(Looper.getMainLooper()).post(() -> {
            @SuppressLint("DefaultLocale")
            String psoResultText = "PSO optimization error: " + String.format("%.2f", error_record);
            tvPSOResult.setText(psoResultText);
        });

        MainActivity.addPointsToScaledMap_imageView((float) estimatedPosition[0] * 100, (float) estimatedPosition[1] * 100);
    }

    // Method to generate random AP positions around the edges of the map
    public static double[][] generateAPPositions(int numAPs, int mapSize) {
        double[][] positions = new double[numAPs][2];
        positions[0] = new double[]{0, 0};
        positions[1] = new double[]{0, mapSize};
        positions[2] = new double[]{mapSize, mapSize};
        positions[3] = new double[]{mapSize, 0};
        positions[4] = new double[]{mapSize / 2, mapSize / 2};
        return positions;
    }

    // Method to generate a random position for the smartphone
    public static double[] generateRandomPosition(int mapSize) {
        Random random = new Random();
        return new double[]{random.nextDouble() * mapSize, random.nextDouble() * mapSize};
    }

    // Method to generate true RSSI0 values for each AP
    public static double[] generateTrueRSSI0(int numAPs) {
        Random random = new Random();
        double[] rssi0 = new double[numAPs];
        for (int i = 0; i < numAPs; i++) {
            rssi0[i] = -40 + random.nextDouble() * 2 - 1;
        }
        return rssi0;
    }

    // Calculate Euclidean distances between the smartphone and APs
    public static double[] calculateDistances(double[][] apPositions, double[] smartphonePosition) {
        double[] distances = new double[apPositions.length];
        for (int i = 0; i < apPositions.length; i++) {
            distances[i] = Math.sqrt(
                    Math.pow(apPositions[i][0] - smartphonePosition[0], 2) +
                            Math.pow(apPositions[i][1] - smartphonePosition[1], 2) +
                            Math.pow(Constants.ap_height - Constants.smartphone_height, 2)
            );
        }
        return distances;
    }

    // RSSI function based on log-distance path loss model
    public static double[] simulateRSSIMeasurements(double[] distances, double[] rssi0, double n) {
        double[] rssiMeasurements = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            rssiMeasurements[i] = rssi0[i] - 10 * n * Math.log10(distances[i] + 1e-9);
        }
        return rssiMeasurements;
    }
}
