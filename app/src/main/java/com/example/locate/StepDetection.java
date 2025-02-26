package com.example.locate;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class StepDetection implements ImuDataCallback {
    private int step = 0;
    private float StepAzimuthInDegrees = 0.0f;
    private Context context;
    private double[] accelerometerData;
    private double threshold = 2.0; // Threshold for step detection
    private double[] recentValues = new double[3];
    private long[] recentTimestamps = new long[3];
    private int currentIndex = 0;
    private boolean isPotentialPeak = false;
    private long lastStepTime = 0;
    private long MIN_STEP_INTERVAL = 400;

    // step length
    private double weinbergK = 0.5;
    private double maxVerticalAcceleration = Double.MIN_VALUE;
    private double minVerticalAcceleration = Double.MAX_VALUE;
    private double currentStepLength = 0.0;

    //step calculate
    private static double currentX;
    private static double currentY;
    private double absoluteX = 0;
    private double absoluteY = 0;

    private PDRKalman pdrKalman;
    private float rawAzimuth;


    /**
     * Listener interface for step detection events
     */
    public interface StepDetectionListener {
        /**
         * Called when a valid step is detected
         *
         * @param stepCount  Total number of steps detected
         * @param stepLength Calculated step length in meters
         * @param azimuth    Movement direction in degrees
         */
        void onStepDetected(int stepCount, double stepLength, float azimuth);

        /**
         * Called when step calculation error occurs
         *
         * @param errorMessage Description of the error
         */
        void onStepError(String errorMessage);
    }

    private StepDetectionListener listener;

    /**
     * Registers a callback for step detection events
     *
     * @param listener Implementation to receive events
     */
    public void setStepDetectionListener(StepDetectionListener listener) {
        this.listener = listener;
    }

    /**
     * Estimates step length using Weinberg method
     *
     * @param verticalAcceleration The vertical acceleration value
     * @return Estimated step length in meters
     */
    public double estimateStepLength(double verticalAcceleration) {
        // Update max and min values
        maxVerticalAcceleration = Math.max(maxVerticalAcceleration, verticalAcceleration);
        minVerticalAcceleration = Math.min(minVerticalAcceleration, verticalAcceleration);

        // Calculate step length using Weinberg formula
        double difference = maxVerticalAcceleration - minVerticalAcceleration;
        if (difference < 0.1) {
            return 0; // Keep last valid step length instead of 0
        }

        currentStepLength = weinbergK * Math.pow(difference, 0.25);

        // Reset max and min for next step
        resetAccelerationExtremes();
        return currentStepLength;
    }

    /**
     * Set the Weinberg calibration constant
     *
     * @param k The calibration constant
     */
    public void setWeinbergConstant(double k) {
        this.weinbergK = k;
    }

    /**
     * Get the current step length
     *
     * @return Current step length in meters
     */
    public double getCurrentStepLength() {
        return currentStepLength;
    }


    /**
     * Constructor for batch processing
     *
     * @param accelerometerData Array of acceleration magnitude values calculated from x,y,z
     */
    public StepDetection(double[] accelerometerData) {
        this.accelerometerData = accelerometerData;
    }

    /**
     * Constructor for real-time processing
     */
    public StepDetection(Context context, PDRKalman pdrKalman) {
        this.context = context;
        this.pdrKalman = pdrKalman;
        reset();
    }

    /**
     * Calculates acceleration magnitude from 3-axis values using equation (1)
     *
     * @param x X-axis acceleration
     * @param y Y-axis acceleration
     * @param z Z-axis acceleration
     * @return Magnitude of acceleration
     */
    public static double calculateMagnitude(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Main step detection algorithm for batch processing
     *
     * @return Total number of steps detected
     */
    public int detectSteps() {
        if (accelerometerData == null || accelerometerData.length < 3) {
            return 0;
        }

        int n = accelerometerData.length;
        int[] p = new int[n];  // Array to mark peak points
        int step = 0;
        int i = 2;  // Start from index 2 as we need to compare with previous points

        // First phase: Mark peak points
        while (i < n - 1) {  // Changed to n-1 to prevent array bounds exception
            // Check if current point is greater than both neighbors
            if (accelerometerData[i] > accelerometerData[i - 1] &&
                    accelerometerData[i] > accelerometerData[i + 1]) {
                p[i] = 1;  // Mark as peak
            } else {
                p[i] = 0;
            }
            i++;
        }

        // Second phase: Count steps based on peaks
        int j = 0;
        int k = 0;

        while (j < n) {
            if (p[j] == 1) {  // If peak point found
                if (k == 0) {
                    k = j;  // Store first peak position
                } else {
                    int D = j - k - 1;  // Calculate distance between peaks
                    if (D > threshold) {
                        step++;  // Increment step count
                    }
                    k = j;  // Update last peak position
                }
            }
            j++;
        }

        // Handle last segment
        if (j == n) {
            int D = n - k;
            if (D > threshold) {
                step++;
            }
        }

        return step;
    }

    /**
     * Real-time step detection with system timestamp
     *
     * @param newAcceleration New acceleration magnitude value
     * @return true if a step is detected, false otherwise
     */
    public boolean detectStepInRealTime(double newAcceleration) {
        return detectStepInRealTime(newAcceleration, System.currentTimeMillis());
    }

    /**
     * Real-time step detection with custom timestamp
     *
     * @param newAcceleration New acceleration magnitude value
     * @param timestamp       Timestamp of the acceleration reading in milliseconds
     * @return true if a step is detected, false otherwise
     */
    public boolean detectStepInRealTime(double newAcceleration, long timestamp) {
        // Debug log incoming data
        Log.d("stepdetection", String.format("New data: value=%.2f, time=%d", newAcceleration, timestamp));

        // Log buffer state before update
        Log.d("stepdetection", String.format("Before update - Buffer[0]=%.2f, Buffer[1]=%.2f, Buffer[2]=%.2f",
                recentValues[0], recentValues[1], recentValues[2]));
        Log.d("stepdetection", "Current Index: " + currentIndex);

        // Update recent values and timestamps buffers
        recentValues[currentIndex] = newAcceleration;
        recentTimestamps[currentIndex] = timestamp;

        // Store current index before updating
        int prevIndex = currentIndex;

        // Update index for next iteration
        currentIndex = (currentIndex + 1) % 3;

        // Wait until we have at least 3 valid readings
        if (recentTimestamps[0] != 0 && recentTimestamps[1] != 0 && recentTimestamps[2] != 0) {
            // Ensure timestamps are in sequence
            long t0 = recentTimestamps[0];
            long t1 = recentTimestamps[1];
            long t2 = recentTimestamps[2];

            // Log timestamp sequence
            Log.d("stepdetection", String.format("Timestamps: t0=%d, t1=%d, t2=%d", t0, t1, t2));

            // Get values in chronological order
            double v0 = recentValues[0];
            double v1 = recentValues[1];
            double v2 = recentValues[2];

            // Check if middle point is a peak
            if (v1 > v0 && v1 > v2 && v1 > 2.0) {
                if (timestamp - lastStepTime > MIN_STEP_INTERVAL) {
                    lastStepTime = timestamp;
                    Log.d("stepdetection", "Step detected with magnitude: " + v1);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reset the real-time detection state
     */
    public void reset() {
        recentValues = new double[3];
        recentTimestamps = new long[3];
        currentIndex = 0;
        lastStepTime = 0;
        Log.d("stepdetection", "Detection state reset");
    }

    /**
     * Get the timestamp of the last detected step
     *
     * @return timestamp of the last step in milliseconds
     */
    public long getLastStepTime() {
        return lastStepTime;
    }

    /**
     * Set the threshold for minimum distance between peaks
     *
     * @param threshold New threshold value
     */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Set the minimum interval between steps
     *
     * @param intervalMs minimum interval in milliseconds
     */
    public void setMinStepInterval(long intervalMs) {
        this.MIN_STEP_INTERVAL = intervalMs;
    }

    /**
     * Get the current threshold value
     *
     * @return current threshold
     */
    public double getThreshold() {
        return threshold;
    }

    /**
     * Get the current minimum step interval
     *
     * @return current minimum step interval in milliseconds
     */
    public long getMinStepInterval() {
        return MIN_STEP_INTERVAL;
    }

    @Override
    public void onAccelerometerData(float x, float y, float z, long timestamp) {
        double magnitude = calculateMagnitude(x, y, z);
        boolean isStep = detectStepInRealTime(magnitude, timestamp);
        Log.d("stepdatain", String.valueOf(magnitude));
        double verticalAcceleration = z;

        if (isStep) {
            step++;
            Log.d("stepcount", "step " + String.valueOf(step));
            double stepLength = estimateStepLength(verticalAcceleration);
            notifyStepDetection(stepLength);
            Toast.makeText(context, "Steptaken" + step + " Length: " +
                    String.format("%.2f", stepLength) + "m Direction " + StepAzimuthInDegrees, Toast.LENGTH_SHORT).show();
            computePosition(StepAzimuthInDegrees, stepLength);
        }
    }

    @Override
    public void onRotationVectorData(float[] rotationVector) {

    }

    @Override
    public void onOrientationData(float azimuthInDegrees, String direction) {
        this.rawAzimuth = azimuthInDegrees;
        this.StepAzimuthInDegrees = computeAdjustedHeading(azimuthInDegrees);
    }

    private float computeAdjustedHeading(float rawHeading) {
        float sensorOffset = MapCalibration.getSensorOffset(Build.MODEL);
        float magneticDeclination = MapCalibration.getMagneticDeclination();
        return (rawHeading + sensorOffset - magneticDeclination + 360) % 360;
    }

    /**
     * Notifies listeners about detected step
     *
     * @param stepLength Calculated step length
     */
    private void notifyStepDetection(double stepLength) {
        if (listener != null) {
            listener.onStepDetected(step, stepLength, StepAzimuthInDegrees);
        }

        // Legacy Toast notification
        Toast.makeText(context,
                "Step taken: " + step +
                        " Length: " + String.format("%.2f", stepLength) + "m" +
                        " Direction: " + StepAzimuthInDegrees,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Resets acceleration tracking values
     */
    private void resetAccelerationExtremes() {
        maxVerticalAcceleration = Double.MIN_VALUE;
        minVerticalAcceleration = Double.MAX_VALUE;
    }

    private void computePosition(float rawHeading, double stepLength) {
        float adjustedHeading = computeAdjustedHeading(rawHeading);
        double rad = Math.toRadians(adjustedHeading);
        if (step == 1) { // update the PDR based on BLE prediction
            currentX = absoluteX + stepLength * Math.sin(rad);
            currentY = absoluteY - stepLength * Math.cos(rad); // Invert vertical movement

        } else {
            // already takes the absoluteX and absoluteY into account
            currentX += stepLength * Math.sin(rad);
            currentY -= stepLength * Math.cos(rad); // Invert vertical movement

        }
        pdrKalman.PDRUpdateState(rad, stepLength);
//        MainActivity.addPointsToScaledMap_imageView((float) currentX * 100, (float)  currentY * 100);

        Log.d("PDRPosition", "CurrentX " + currentX + "currentY " + currentY);
    }

    static void setPosition(float x, float y) {
        currentX = x;
        currentY = y;
    }

}