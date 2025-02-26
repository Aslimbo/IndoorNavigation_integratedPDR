package com.example.locate;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.example.locate.ImuDataCallback;
//import com.google.android.gms.location.FusedLocationClient;
//import com.google.android.gms.location.LocationServices;
import android.hardware.GeomagneticField;
import java.util.Date;

public class SensorManagerMain implements SensorEventListener {
    private ImuDataCallback callback;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor rotationVectorSensor;
    private Sensor magnetometer;
    float[] rotationMatrix = new float[16];
    private float[] orientationAngles = new float[3];
    private float[] accelerometerReading = new float[3];
    private float[] magnetometerReading = new float[3];
    private Context context;

    public SensorManagerMain(Context context) {
        this.context = context;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void setCallback(ImuDataCallback callback) {
        this.callback = callback;
    }

    public void start() {
        registerSensors();
    }

    private void registerSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, accelerometer, 100000);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            String rotationData = "Rotation Vector: X=" + event.values[0] +
                    " Y=" + event.values[1] +
                    " Z=" + event.values[2] +
                    " w=" + event.values[3];
            Log.d("Sensor", rotationData);

            // Convert rotation vector to orientation angles
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            // Convert azimuth to degrees
            float azimuthInDegrees = (float) Math.toDegrees(orientationAngles[0]);
            // Normalize to 0-360
            if (azimuthInDegrees < 0) {
                azimuthInDegrees += 360;
            }

            String direction = getCardinalDirection(azimuthInDegrees);

            if (callback != null) {
                callback.onOrientationData(azimuthInDegrees, direction);
            }
        } else if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float[] worldAcceleration = new float[4];
            float[] deviceAcceleration = new float[4];

            // Create a 4D vector (x,y,z,1) for matrix multiplication
            deviceAcceleration[0] = event.values[0];
            deviceAcceleration[1] = event.values[1];
            deviceAcceleration[2] = event.values[2];
            deviceAcceleration[3] = 1;

            // Transform device acceleration to world coordinates
            // rotationMatrix should be maintained from rotation vector updates

            if (rotationMatrix.length < 16) {
                Log.e("SensorError", "rotationMatrix size is incorrect: " + rotationMatrix.length);
            }

            android.opengl.Matrix.multiplyMV(
                    worldAcceleration, 0,
                    rotationMatrix, 0,
                    deviceAcceleration, 0
            );

            String accelData = "World Acceleration: X=" + worldAcceleration[0] +
                    " Y=" + worldAcceleration[1] +
                    " Z=" + worldAcceleration[2];
            long timestamp = new Date().getTime();
            Log.d("SensoraccelData", accelData);

            if (callback != null) {
                callback.onAccelerometerData(
                        worldAcceleration[0],  // East-West
                        worldAcceleration[1],  // North-South
                        worldAcceleration[2],  // Up-Down
                        timestamp
                );
            }
        }
    }

    private String getCardinalDirection(double heading) {
        if (heading >= 337.5 || heading < 22.5) {
            return "N";
        } else if (heading >= 22.5 && heading < 67.5) {
            return "NE";
        } else if (heading >= 67.5 && heading < 112.5) {
            return "E";
        } else if (heading >= 112.5 && heading < 157.5) {
            return "SE";
        } else if (heading >= 157.5 && heading < 202.5) {
            return "S";
        } else if (heading >= 202.5 && heading < 247.5) {
            return "SW";
        } else if (heading >= 247.5 && heading < 292.5) {
            return "W";
        } else if (heading >= 292.5 && heading < 337.5) {
            return "NW";
        }
        return "?";
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    public void cleanup() {
        sensorManager.unregisterListener(this);
    }
}