package com.example.locate;

import android.util.Log;


public class PDRKalman {
    private double[][] Q;  // Process noise covariance.  4 by 4 in this case
    private double[][] R;  // Measurement noise covariance. BLE measurement only contains x y, thus 2 by 2
    // [consider x y and thesta and stridelength, 4 by 4 then]
    private double[][] F;  // State transition matrix x y position to next x y position.
    private double[][] H;  // Measurement matrix 2 by 4, connecting measurements to state status
    private double[][] P;  // Estimation error covariance. 4 by 4 in this case
    private double[][] I;  // Identity matrix
    private static double[][] x;  // Estimated state


    public PDRKalman(double[][] Q, double[][] R, double[][] F, double[][] H) {
        this.Q = Q;
        this.R = R;
        this.F = F;
        this.H = H;
        this.P = new double[4][4];
        this.I = new double[4][4];
        this.x = new double[4][1];

        // Initialize the identity matrix
        for (int i = 0; i < 4; i++) {
            I[i][i] = 1.0;
        }
        // initialize the covariance matrix
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                P[i][j] = 0.0;
            }
        }
        // initialize the x position
        for (int i = 0; i < 4; i++) {
            x[i][0] = 0.0;
        }
    }

    public PDRKalman() {
        this(new double[4][4], new double[2][2], new double[4][4], new double[2][4]);
        // initialize the process noise covariance matrix
        for (int i = 0; i < 4; i++) {
            Q[i][i] = 0.1;
        }

        // initialize the measurement noise covariance matrix
        for (int i = 0; i < 2; i++) {
            R[i][i] = 1;
        }
        // initialize the state transition matrix
        setPosition(0.0, 0.0, 0.0, 0.0);
        updateF();
        // initialize the measurement matrix
        H[0][0] = 1.0;
        H[1][1] = 1.0;
    }

    public void PDRUpdateState(double theta, double stride) {
        // get the stride and theta from the PDRProcessor
        setPosition(x[0][0], x[1][0], theta, stride);
        updateF();

        double[][] x_pred = MatrixUtil.multiply(F, x);
        double[][] P_pred = MatrixUtil.add(Q, MatrixUtil.multiply(MatrixUtil.multiply(F, P), MatrixUtil.transpose(F)));
        x = x_pred;
        P = P_pred;
        Log.d("PDRPosition", String.valueOf(x[0][0]));
        MainActivity.addPointsToScaledMap_imageView((float) x[0][0] * 100, (float) x[1][0] * 100);
        NavigationActivity.addPointsToScaledMapNavigation((float) x[0][0] * 100, (float) x[1][0] * 100);

    }

    public void BLEUpdateState(double[] BLEPosition) {
//        double[][] measurement = new double[2][1];
//
//
//        measurement[0][0] = BLEPosition[0];
//        measurement[1][0] = BLEPosition[1];
        setPosition(x[0][0], x[1][0], 0, 0);
        updateF();
//
        double[][] x_pred = MatrixUtil.multiply(F, x);
        double[][] P_pred = MatrixUtil.add(MatrixUtil.multiply(MatrixUtil.multiply(F, P), MatrixUtil.transpose(F)), Q);

//        double[][] S = MatrixUtil.add(R, MatrixUtil.multiply(MatrixUtil.multiply(H, P_pred), MatrixUtil.transpose(H)));
//        double[][] K = MatrixUtil.multiply(MatrixUtil.multiply(P_pred, MatrixUtil.transpose(H)), MatrixUtil.inverse(S));
//        double[][] I_KH = MatrixUtil.subtract(I, MatrixUtil.multiply(K, H));
//        x = MatrixUtil.add(x_pred, MatrixUtil.multiply(K, MatrixUtil.subtract(measurement, MatrixUtil.multiply(H, x_pred))));
//        P = MatrixUtil.multiply(I_KH, P_pred);

        // No need to update F matrix here since we're not predicting movement
        // We're only correcting our current position estimate with BLE measurement

        // Innovation: difference between measurement and predicted measurement
//        double[][] innovation = MatrixUtil.subtract(measurement, MatrixUtil.multiply(H, x));
        double[][] innovation = MatrixUtil.subtract(
                new double[][]{{BLEPosition[0]}, {BLEPosition[1]}},
                MatrixUtil.multiply(H, x_pred)
        );
        // Innovation covariance: S = H * P * H^T + R
//        double[][] S = MatrixUtil.add(R, MatrixUtil.multiply(MatrixUtil.multiply(H, P), MatrixUtil.transpose(H)));
        double[][] S = MatrixUtil.add(
                R,
                MatrixUtil.multiply(MatrixUtil.multiply(H, P_pred), MatrixUtil.transpose(H))
        );
        // Kalman gain: K = P * H^T * S^(-1)
//        double[][] K = MatrixUtil.multiply(MatrixUtil.multiply(P, MatrixUtil.transpose(H)), MatrixUtil.inverse(S));
        double[][] K = MatrixUtil.multiply(
                MatrixUtil.multiply(P_pred, MatrixUtil.transpose(H)),
                MatrixUtil.inverse(S)
        );
        // State update: x = x + K * innovation
        x = MatrixUtil.add(x_pred, MatrixUtil.multiply(K, innovation));

        // Covariance update: P = (I - K*H) * P
        double[][] I_KH = MatrixUtil.subtract(I, MatrixUtil.multiply(K, H));
        P = MatrixUtil.multiply(I_KH, P_pred);
        Log.d("BLEPosition", String.valueOf(x[0][0]));

        MainActivity.addPointsToScaledMap_imageView((float) x[0][0] * 100, (float) x[1][0] * 100);
        NavigationActivity.addPointsToScaledMapNavigation((float) x[0][0] * 100, (float) x[1][0] * 100);

    }


    public void setPosition(double x, double y, double theta, double strideLength) {
        this.x[0][0] = x;
        this.x[1][0] = y;
        this.x[2][0] = theta;
        this.x[3][0] = strideLength;
    }

    public void updateF() {
        F[0][0] = 1.0;
        F[1][1] = 1.0;
        F[0][2] = Math.sin(x[2][0]) * x[3][0];
        F[0][3] = Math.cos(x[2][0]);
        F[1][2] = -Math.cos(x[2][0]) * x[3][0];
        F[1][3] = Math.sin(x[2][0]);
        F[2][2] = 1.0;
        F[3][3] = 1.0;
    }

    public static double[] getPosition() {
        double[] position = new double[2];
        position[0] = x[0][0];
        position[1] = x[1][0];
        Log.d("getPosition", "CurrentX " + position[0] + "currentY " + position[1]);

        return position;

    }

}
