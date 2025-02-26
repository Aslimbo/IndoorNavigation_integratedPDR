package com.example.locate.data;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.*;
import java.util.*;
import android.util.Log;

public class ObstacleRegionLoader {
    private Context context;
    private String filename;
    private double[] X;
    private double[] Y;

    public ObstacleRegionLoader(Context context, String filename) {
        this.context = context;
        this.filename = filename;
    }

    public List<obstacleRegion> getObstacles() {
        List<obstacleRegion> obstacleRegions = new ArrayList<>();

        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            obstacleRegion currentRegion = new obstacleRegion();

            int countLine = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                String[] coordinates = line.split(",");
                Log.d("inputline ", Arrays.toString(coordinates));
                if (countLine % 2 == 0) {
                    X = new double[coordinates.length];
                    for (int i = 0; i < coordinates.length; i++) {
                        X[i] = Double.parseDouble(coordinates[i]);
                    }
                } else {
                    Y = new double[coordinates.length];
                    for (int i = 0; i < coordinates.length; i++) {
                        Y[i] = Double.parseDouble(coordinates[i]);
                    }
                    // store coordinates into position
                    for (int i = 0; i < coordinates.length; i++) {
                        currentRegion.addPosition(new Position(X[i], Y[i]));
                    }
                    obstacleRegions.add(currentRegion);
                    currentRegion = new obstacleRegion();

                }
                countLine++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return obstacleRegions;
    }

    public static double[][] getObstaclesBounds(List<obstacleRegion> obstacleRegionsList) {
        double [][] obstaclesBounds = new double[obstacleRegionsList.size()][4]; // maxX minX maxY minY
        List<Position> currentPositions;
        for (int i = 0; i < obstacleRegionsList.size(); i++) {
            currentPositions = obstacleRegionsList.get(i).getPositions();
            double[] temp_x = new double[currentPositions.size()];
            double[] temp_y = new double[currentPositions.size()];
            for (int j = 0; j < currentPositions.size(); j++) {
                Log.d("CurrentPosition", String.valueOf(currentPositions.get(j).getX()));
                temp_x[j] = currentPositions.get(j).getX();
                temp_y[j] = currentPositions.get(j).getY();
            }
            obstaclesBounds[i][0] = arrayMax(temp_x);
            obstaclesBounds[i][1] = arrayMin(temp_x);
            obstaclesBounds[i][2] = arrayMax(temp_y);
            obstaclesBounds[i][3] = arrayMin(temp_y);
            Log.d("tempX", Arrays.toString(temp_x));
            Log.d("tempY", Arrays.toString(temp_y));
            Log.d("Bounds", Arrays.toString(obstaclesBounds[i]));

        }
        return obstaclesBounds;
    }

    private static double arrayMin(double[] array) {
        double min = Double.POSITIVE_INFINITY;
        for (double num : array) {
            min = Math.min(min, num);
        }
        return min;
    }

    private static double arrayMax(double[] array) {
        double max = Double.NEGATIVE_INFINITY;
        for (double num : array) {
            max = Math.max(max, num);
        }
        return max;
    }

}
