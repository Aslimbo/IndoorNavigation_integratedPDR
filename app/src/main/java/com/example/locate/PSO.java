package com.example.locate;

import android.util.Log;

import java.util.Arrays;
import java.util.Random;

public class PSO {
    private int swarmSize;
    private int numBeacons;
    private int mapSize;
    private int maxIterations;
    private double functionTolerance;
    private Random random;
    private int optimizedBeacons;
    private int patience;

    // Personal best and global best storage
    private double[][] pBestPositions;
    private double[] pBestFitness;
    private double[] gBestPosition;
    private double gBestFitness;
    private double noImprovementCount;
    private double[][] psoBoundary;

    public PSO(int swarmSize, int numBeacons, int optimizedBeacons, int mapSize,
               int maxIterations, double functionTolerance, int patience, double[][] psoBoundary) {
        this.swarmSize = swarmSize;
        this.numBeacons = numBeacons;
        this.optimizedBeacons = optimizedBeacons;
        this.mapSize = mapSize;
        this.maxIterations = maxIterations;
        this.functionTolerance = functionTolerance;
        this.random = new Random();
        this.patience = patience;
        this.psoBoundary = psoBoundary;
    }

    public double[] optimize(double[][] beaconPositions, double[] rssiMeasurements) {
        // PSO Parameters (e.g., cognitive, social components)
        double c1 = 1.5, c2 = 1.5, inertiaMax = 0.9, inertiaMin = 0.4;
        double inertiaWeight = inertiaMax;
        double function_improvement = 100;
        double[][] swarm = new double[swarmSize][3 + optimizedBeacons];  // x, y, n, RSSI0
        double[][] velocities = new double[swarmSize][3 + optimizedBeacons];  // velocities for each particle

        // Initialize the swarm with random positions and velocities
        for (int i = 0; i < swarmSize; i++) {
            swarm[i][0] = random.nextDouble() * (17.5067-0.56) + 0.56; // x
            swarm[i][1] = random.nextDouble() * (13.2158-3.5805)+ 3.5805; // y
            swarm[i][2] = random.nextDouble() * 2 + 2;  // n (path loss exponent)
//            for (int j = 3; j < optimizedBeacons + 3; j++) {
//                swarm[i][j] = -48 + random.nextDouble() * 5 - 2.5; // RSSI0 for each beacon
//            }
            swarm[i][3] = -48 + random.nextDouble() * 20 -10 ;
            // Random initial velocities
//            for (int j = 0; j < optimizedBeacons + 3; j++) {
//                if (j == 2){
//                    velocities[i][j] = random.nextDouble() * 0.2 - 0.1;
//                }
//                else if (j == 3){
//                    velocities[i][j] = random.nextDouble() * 5 - 2.5;
//                }
//                else velocities[i][0] = random.nextDouble() * (17.5067-0.56) - (17.5067-0.56);  // Random velocities between -1 and 1
//            }
            velocities[i][0] = random.nextDouble() * 2 * (17.5067-0.56) - (17.5067-0.56);
            velocities[i][1] = random.nextDouble() * 2 *(13.2158-3.5805) - (13.2158-3.5805);
            velocities[i][2] = random.nextDouble() * 2 * (2) - (2);
            velocities[i][3] = random.nextDouble() * 2 * (20) - (20);
            //ddddddd
        }

        // Initialize personal best and global best
        pBestPositions = new double[swarmSize][optimizedBeacons + 3];
        pBestFitness = new double[swarmSize];
        gBestPosition = new double[optimizedBeacons + 3];
        gBestFitness = Double.POSITIVE_INFINITY;

        // PSO main loop
        for (int iter = 0; iter < maxIterations; iter++) {
            boolean improved = false;

            // Evaluate each particle's fitness
            for (int i = 0; i < swarmSize; i++) {
                double[] particle = swarm[i];
                double fitness = evaluateFitness(particle, beaconPositions, rssiMeasurements);

                // Update personal best
                if (fitness < pBestFitness[i] || iter == 0) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particle, 0, pBestPositions[i], 0, optimizedBeacons + 3);
                }

                // Update global best
                if (fitness < gBestFitness || iter == 0) {
                    function_improvement = Math.abs(gBestFitness - fitness);
                    gBestFitness = fitness;
                    System.arraycopy(particle, 0, gBestPosition, 0, optimizedBeacons + 3);
                    improved = true;  // Mark that we found a new global best
                } else {
                    function_improvement = 0;
                }
            }

            // Update inertia weight (adaptive)
            if (improved) {
                inertiaWeight = Math.max(inertiaMin, inertiaWeight * 0.9);  // Decrease inertia (focus on exploitation)
            } else {
                inertiaWeight = Math.min(inertiaMax, inertiaWeight * 1.1);  // Increase inertia (encourage exploration)
            }

            // Update velocity and position for each particle
            for (int i = 0; i < swarmSize; i++) {
                for (int j = 0; j < optimizedBeacons + 3; j++) {
                    // Velocity update
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    velocities[i][j] = inertiaWeight * velocities[i][j]
                            + c1 * r1 * (pBestPositions[i][j] - swarm[i][j])  // Cognitive component
                            + c2 * r2 * (gBestPosition[j] - swarm[i][j]);    // Social component

                    // Position update
                    swarm[i][j] += velocities[i][j];


                    swarm[i][0] = Math.max(psoBoundary[0][1], Math.min(swarm[i][0], psoBoundary[0][0]));
                    swarm[i][1] = Math.max(psoBoundary[0][3], Math.min(swarm[i][1], psoBoundary[0][2]));

//                    for (int i_bound = 1; i_bound < psoBoundary.length; i_bound++) {
//                        double boundMaxX = psoBoundary[i_bound][0];
//                        double boundMinX = psoBoundary[i_bound][1];
//                        double boundMaxY = psoBoundary[i_bound][2];
//                        double boundMinY = psoBoundary[i_bound][3];
//                        // check if swarm point is inside the region, if so, move it to the boundary
//                        if (swarm[i][0] > boundMinX
//                                && swarm[i][0] < boundMaxX
//                                && swarm[i][1] > boundMinY
//                                && swarm[i][1] < boundMaxY) {
//                            double obsLength = boundMaxX - boundMinX;
//                            double obsHeight = boundMaxY - boundMinY;
//                            swarm[i][0] = (swarm[i][0] - boundMinX) / obsLength >= 0.5? boundMaxX:boundMinX;
//                            swarm[i][1] = (swarm[i][1] - boundMinY) / obsHeight >= 0.5? boundMaxY:boundMinY;
//                            //                            Log.d("Bounds_found", Arrays.toString(psoBoundary[i_bound]));
//                            break; // break the for loop
//                        }
//                    }
                    swarm[i][2] = Math.max(2, Math.min(swarm[i][2], 5));
                    swarm[i][3] = Math.max(-70, Math.min(swarm[i][3], -30));

                }
            }

            // Check stopping criteria
            if (functionTolerance > 0 && function_improvement < functionTolerance) {
                noImprovementCount += 1;
            } else {
                noImprovementCount = 0;
            }
            if (noImprovementCount > patience){
                System.out.println("The optimization ends on iteration: " + (iter + 1));
                break;
            } else if (iter == maxIterations - 1) {
                System.out.println("The optimization ends on iteration: " + maxIterations);
            }
        }
        // Return the best solution found
        return gBestPosition;
    }

    // Objective function for PSO
//    public double evaluateFitness(double[] params, double[][] beaconPositions, double[] rssiMeasurements) {
//        double error = 0.0;
//        double[] distances = BLEProcessor.calculateDistances(beaconPositions, new double[]{params[0], params[1]});
//        double estimatedRSSI;
//
//        for (int i = 0; i < rssiMeasurements.length; i++) {
//            if (optimizedBeacons == 1) {
//                estimatedRSSI = params[3] - 10 * params[2] * Math.log10(distances[i] + 1e-9);
//            } else {
//                estimatedRSSI = params[3 + i] - 10 * params[2] * Math.log10(distances[i] + 1e-9);
//            }
//            error += Math.abs(rssiMeasurements[i] - estimatedRSSI);
//        }
//        return error;
//    }
    public double evaluateFitness(double[] params, double[][] beaconPositions, double[] rssiMeasurements) {
        double error = 0.0;
        double[] distances = BLEProcessor.calculateDistances(beaconPositions, new double[]{params[0], params[1]});
        double estimatedRSSI;
        double estimatedDistance;

        for (int i = 0; i < rssiMeasurements.length; i++) {
            // Calculate estimated distance for the current RSSI measurement
            estimatedDistance = Math.pow(10, (rssiMeasurements[i] - params[3]) / (-10 * params[2]));
//            estimatedDistance = distance_ref[i] * Math.pow(10, ( rssiMeasurements[i] - params[3] + 10 * Math.log10(distances[i]) ) / ( - 10 * params[2] ));
            // Accumulate the error between the estimated distance and actual distance
            error += Math.pow((estimatedDistance - distances[i]),2);
        }
        return error;
    }

    public double[] optimize_adjusted(double[][] beaconPositions, double[] rssiMeasurements,
                                      double[] distance_ref, double[] rssi_ref) {
        // PSO Parameters (e.g., cognitive, social components)
        double c1 = 1.5, c2 = 1.5, inertiaMax = 0.9, inertiaMin = 0.4;
        double inertiaWeight = inertiaMax;
        double function_improvement = 100;
        double[][] swarm = new double[swarmSize][3 + optimizedBeacons];  // x, y, n, RSSI0
        double[][] velocities = new double[swarmSize][3 + optimizedBeacons];  // velocities for each particle

        // Initialize the swarm with random positions and velocities
        for (int i = 0; i < swarmSize; i++) {
            swarm[i][0] = random.nextDouble() * (17.5067-0.56) + 0.56; // x
            swarm[i][1] = random.nextDouble() * (13.2158-3.5805)+ 3.5805; // y
            swarm[i][2] = random.nextDouble() * 2 + 2;  // n (path loss exponent)
//            for (int j = 3; j < optimizedBeacons + 3; j++) {
//                swarm[i][j] = -48 + random.nextDouble() * 5 - 2.5; // RSSI0 for each beacon
//            }
            swarm[i][3] = -48 + random.nextDouble() * 20 -10 ;

            velocities[i][0] = random.nextDouble() * 2 * (17.5067-0.56) - (17.5067-0.56);
            velocities[i][1] = random.nextDouble() * 2 *(13.2158-3.5805) - (13.2158-3.5805);
            velocities[i][2] = random.nextDouble() * 2 * (2) - (2);
            velocities[i][3] = random.nextDouble() * 2 * (20) - (20);
        }

        // Initialize personal best and global best
        pBestPositions = new double[swarmSize][optimizedBeacons + 3];
        pBestFitness = new double[swarmSize];
        gBestPosition = new double[optimizedBeacons + 3];
        gBestFitness = Double.POSITIVE_INFINITY;

        // PSO main loop
        for (int iter = 0; iter < maxIterations; iter++) {
            boolean improved = false;

            // Evaluate each particle's fitness
            for (int i = 0; i < swarmSize; i++) {
                double[] particle = swarm[i];
                double fitness = evaluateFitness_adjusted(particle, beaconPositions, rssiMeasurements,
                        distance_ref, rssi_ref);

                // Update personal best
                if (fitness < pBestFitness[i] || iter == 0) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particle, 0, pBestPositions[i], 0, optimizedBeacons + 3);
                }

                // Update global best
                if (fitness < gBestFitness || iter == 0) {
                    function_improvement = Math.abs(gBestFitness - fitness);
                    gBestFitness = fitness;
                    System.arraycopy(particle, 0, gBestPosition, 0, optimizedBeacons + 3);
                    improved = true;  // Mark that we found a new global best
                } else {
                    function_improvement = 0;
                }
            }

            // Update inertia weight (adaptive)
            if (improved) {
                inertiaWeight = Math.max(inertiaMin, inertiaWeight * 0.9);  // Decrease inertia (focus on exploitation)
            } else {
                inertiaWeight = Math.min(inertiaMax, inertiaWeight * 1.1);  // Increase inertia (encourage exploration)
            }

            // Update velocity and position for each particle
            for (int i = 0; i < swarmSize; i++) {
                for (int j = 0; j < optimizedBeacons + 3; j++) {
                    // Velocity update
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    velocities[i][j] = inertiaWeight * velocities[i][j]
                            + c1 * r1 * (pBestPositions[i][j] - swarm[i][j])  // Cognitive component
                            + c2 * r2 * (gBestPosition[j] - swarm[i][j]);    // Social component

                    // Position update
                    swarm[i][j] += velocities[i][j];

                    swarm[i][0] = Math.max(psoBoundary[0][1], Math.min(swarm[i][0], psoBoundary[0][0]));
                    swarm[i][1] = Math.max(psoBoundary[1][1], Math.min(swarm[i][1], psoBoundary[1][0]));
                    swarm[i][2] = Math.max(2, Math.min(swarm[i][2], 5));
                    swarm[i][3] = Math.max(-70, Math.min(swarm[i][3], -30));

                }
            }

            // Check stopping criteria
            if (functionTolerance > 0 && function_improvement < functionTolerance) {
                noImprovementCount += 1;
            } else {
                noImprovementCount = 0;
            }
            if (noImprovementCount > patience){
                System.out.println("The optimization ends on iteration: " + (iter + 1));
                break;
            } else if (iter == maxIterations - 1) {
                System.out.println("The optimization ends on iteration: " + maxIterations);
            }
        }
        // Return the best solution found
        return gBestPosition;
    }

    public double evaluateFitness_adjusted(double[] params, double[][] beaconPositions, double[] rssiMeasurements,
                                           double[] distance_ref, double[] rssi_ref) {
        double error = 0.0;
        double r0;
        double[] distances = BLEProcessor.calculateDistances(beaconPositions, new double[]{params[0], params[1]});
        double estimatedRSSI;
        double estimatedDistance;



        for (int i = 0; i < rssiMeasurements.length; i++) {
            // Calculate estimated distance for the current RSSI measurement
            // adjust the rssi0 according to the reference rssi measurements
            r0 = params[3] * (rssi_ref[i] + 10 * params[2] * Math.log10(distance_ref[i])) / (rssi_ref[0] + 10 * params[2] * Math.log10(distance_ref[0]));
            estimatedDistance = distance_ref[i] * Math.pow(10,
                    ( rssiMeasurements[i] - r0 + 10 * Math.log10(distances[i]) ) / ( - 10 * params[2] ) );
            // Accumulate the error between the estimated distance and actual distance
            error += Math.pow((estimatedDistance - distances[i]),2);
        }
        return error;
    }

    public static double[] calculateDistances(double[][] beaconPositions, double[] smartphonePosition) {
        double[] distances = new double[beaconPositions.length];
        for (int i = 0; i < beaconPositions.length; i++) {
            distances[i] = Math.sqrt(Math.pow(beaconPositions[i][0] - smartphonePosition[0], 2) +
                    Math.pow(beaconPositions[i][1] - smartphonePosition[1], 2));
        }
        return distances;
    }

    // getter
    public double getgBestFitness() {
        return gBestFitness;
    }
}
