package com.example.locate;

/**
 * Created on 2024/6/21 16:24
 * Author: ZST
 */

public class Fusion {
    private double q = 0.0001; // 过程噪声协方差
    private double r = 0.01; // 测量噪声协方差
    private double x = 0; // 估计值
    private double p = 1; // 估计误差协方差
    private double k; // 卡尔曼增益

    public void setState(double state, double covariance) {
        this.x = state;
        this.p = covariance;
    }

    public void correct(double measurement) {
        // 计算卡尔曼增益
        k = p / (p + r);
        // 更新估计值
        x = x + k * (measurement - x);
        // 更新估计误差协方差
        p = (1 - k) * p;
    }

    public void predict(double control, double controlFactor) {
        // 预测估计值
        x = x + controlFactor * control;
        // 预测估计误差协方差
        p = p + q;
    }

    public double getState() {
        return x;
    }
}
