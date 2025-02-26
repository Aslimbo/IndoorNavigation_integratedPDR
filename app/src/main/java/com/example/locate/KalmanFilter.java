package com.example.locate;

public class KalmanFilter {
    private double Q;  // Process noise covariance
    private double R;  // Measurement noise covariance
    private double F;  // State transition matrix
    private double G;  // Control input matrix
    private double C;  // Measurement matrix
    private double x;  // Estimated state
    private double P;  // Estimation error covariance
    private double u;  // Control input

    public KalmanFilter(double Q, double R, double F, double G, double C) {
        this.Q = Q;
        this.R = R;
        this.F = F;
        this.G = G;
        this.C = C;
        this.x = 0.0;
        this.P = 1.0;
        this.u = 0.0;
    }
//   double Q = 0.001; // Process noise covariance
//    double R = 0.1;   // Measurement noise covariance
//    double F = 1.0;   // State transition matrix
//    double G = 0.0;   // Control input matrix (not used in this example)
//    double C = 1.0;   // Measurement matrix
    // Method to set the initial state
    public void setInitialState(double x0) {
        this.x = x0;
    }

    // Method to perform the update (prediction + correction) step
    public double update(double measurement) {
        // Prediction step
        double x_pred = F * x + G * u;  // State prediction
        double P_pred = F * P * F + Q;  // Covariance prediction

        // Measurement update step
        double K = P_pred * C / (C * P_pred * C + R);  // Kalman gain
        x = x_pred + K * (measurement - C * x_pred);   // State update
        P = (1 - K * C) * P_pred;                      // Covariance update

        return x;  // Return the updated state estimate
    }

    // Optional setter for control input if needed
    public void setControlInput(double u) {
        this.u = u;
    }
}
