package com.example.locate;

import android.util.Log;

public class MatrixUtil {

    // Method to multiply two matrices
    public static double[][] multiply(double[][] A, double[][] B) {
        int rowsA = A.length;
        int colsA = A[0].length;
        int rowsB = B.length;
        int colsB = B[0].length;

        if (colsA != rowsB) {
            throw new IllegalArgumentException("Matrix dimensions do not match for multiplication");
        }

        double[][] result = new double[rowsA][colsB];

        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                for (int k = 0; k < colsA; k++) {
                    result[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        return result;
    }

    // Method to add two matrices
    public static double[][] add(double[][] A, double[][] B) {

        int rowsA = A.length;
        int colsA = A[0].length;
        int rowsB = B.length;
        int colsB = B[0].length;

        if (rowsA != rowsB || colsA != colsB) {
            throw new IllegalArgumentException("Matrix dimensions do not match for addition");
        }

        double[][] result = new double[rowsA][colsA];

        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsA; j++) {
                result[i][j] = A[i][j] + B[i][j];
            }
        }

        return result;
    }

    public static double[][] transpose(double[][] A) {
        int rowsA = A.length;
        int colsA = A[0].length;

        double[][] result = new double[colsA][rowsA];

        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsA; j++) {
                result[j][i] = A[i][j];
            }
        }
        return result;
    }

    public static double[][] subtract(double[][] A, double[][] B) {
        int rowsA = A.length;
        int colsA = A[0].length;
        int rowsB = B.length;
        int colsB = B[0].length;

        if (rowsA != rowsB || colsA != colsB) {
            throw new IllegalArgumentException("Matrix dimensions do not match for subtraction");
        }

        double[][] result = new double[rowsA][colsA];

        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsA; j++) {
                result[i][j] = A[i][j] - B[i][j];
            }
        }

        return result;
    }

    public static double[][] inverse(double[][] A) {
        int n = A.length;

        // Check if the matrix is square
        if (n != A[0].length) {
            throw new IllegalArgumentException("Matrix must be square to compute its inverse.");
        }

        // Augment the matrix A with the identity matrix to form [A | I]
        double[][] augmented = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n + i] = 1; // Add the identity matrix
        }

        // Perform Gaussian elimination
        for (int i = 0; i < n; i++) {
            // Find the pivot element
            double pivot = augmented[i][i];
            if (pivot == 0) {
                throw new ArithmeticException("Matrix is singular and cannot be inverted.");
            }

            // Normalize the pivot row
            for (int j = 0; j < 2 * n; j++) {
                augmented[i][j] /= pivot;
            }

            // Eliminate the current column in other rows
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = augmented[k][i];
                    for (int j = 0; j < 2 * n; j++) {
                        augmented[k][j] -= factor * augmented[i][j];
                    }
                }
            }
        }

        // Extract the right half of the augmented matrix as the inverse
        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                inverse[i][j] = augmented[i][n + j];
            }
        }

        return inverse;
    }

    public static void logMatrix(double[][] matrix, String name) {
        StringBuilder builder = new StringBuilder();
        builder.append("Matrix: ").append(name).append("\n");
        for (double[] row : matrix) {
            for (double value : row) {
                builder.append(String.format("%10.4f ", value)); // Format for 4 decimal places
            }
            builder.append("\n");
        }
        Log.d("MatrixUtil", builder.toString());
    }


}
