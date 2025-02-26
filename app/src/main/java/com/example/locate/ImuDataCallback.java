package com.example.locate;

public interface ImuDataCallback {
    void onAccelerometerData(float x, float y, float z, long timestamp);
    void onRotationVectorData(float[] rotationVector);

    void onOrientationData(float azimuthInDegrees, String direction);
}
