package com.example.locate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;


public class customimageview extends AppCompatImageView {
    private Paint paint;

    // pointsX  pointsY: The point prediction history to draw on the map
    private List<Float> pointsX;
    private List<Float> pointsY;

    private List<Float> prediction_pointsX;
    private List<Float> prediction_pointsY;

    private List<Float> groundTruthX;
    private List<Float> groundTruthY;
    public Matrix matrix;
    public Matrix savedmatrix;

    private float meanX = 0f;
    private float meanY = 0f;

    private float[] meanX_record = new float[10];
    private float[] currentPoint = new float[2];


    private Float currentPtX;
    private Float currentPtY;
    static boolean moving = false;

    private boolean locationLocked = false;
    private float[] lockedLocation = new float[2];

    private Handler handler = new Handler();
    private Runnable addLastPointRunnable;
    private int MAX_POINTS = 100;
    private float THRESHOLD = 300F;

    // ================= Listener =================

    /**
     * Define a callback interface
     * Will notify the external program
     * When valid (in the drawable coordinate system) coordinates have been obtained.
     */
    public interface OnValidPointListener {
        /**
         * When valid coordinates have been obtained.
         *
         * @param validPoint drawable  {x, y}
         */
        void onValidPoint(float[] validPoint);
    }

    // 保存回调接口的引用
    private OnValidPointListener mOnValidPointListener;

    /**
     * 注册一个回调监听器
     *
     * @param listener OnValidPointListener 实例
     */
    public void setOnValidPointListener(OnValidPointListener listener) {
        this.mOnValidPointListener = listener;
    }
    // ================= 回调接口部分结束 =================

    public customimageview(Context context) {
        super(context);
        init();
    }

    public customimageview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public customimageview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        pointsX = new ArrayList<>();
        pointsY = new ArrayList<>();
        groundTruthX = new ArrayList<>();
        groundTruthY = new ArrayList<>();
        prediction_pointsX = new ArrayList<>();
        prediction_pointsY = new ArrayList<>();

        addLastPointRunnable = new Runnable() {
            @Override
            public void run() {
//                Log.d("constant", String.valueOf(Constants.moving));
                if (Constants.moving == false) {
                    MAX_POINTS = 10;
                    ;
                    THRESHOLD = 1000F;
                    if (!pointsX.isEmpty()) {
                        // Add the last point from pointsX to prediction_pointsX
                        float lastPointX = pointsX.get(pointsX.size() - 1);
                        float lastPointY = pointsY.get(pointsY.size() - 1);
//                        if (prediction_pointsX.isEmpty()){
//                            prediction_pointsX.add(lastPointX);
//                            prediction_pointsY.add(lastPointY);
//                        }

                        prediction_pointsX.add(lastPointX);
                        prediction_pointsY.add(lastPointY);

                        if (prediction_pointsX.size() < MAX_POINTS) {
                            while (prediction_pointsX.size() < MAX_POINTS) {
                                prediction_pointsX.add(lastPointX * 0);
                                prediction_pointsY.add(lastPointY * 0);
                            }
                        } else {
                            while (prediction_pointsX.size() > MAX_POINTS) {
                                prediction_pointsX.remove(0);
                                prediction_pointsY.remove(0);
                            }

                        }
//                        Log.d("prediction_pointsX", prediction_pointsX.toString());
//                        Log.d("prediction_pointsX", prediction_pointsX.toString());

//                        }
                    }

                } else {
                    MAX_POINTS = 10;
                    THRESHOLD = 1000F;
                    if (!pointsX.isEmpty()) {
                        // Add the last point from pointsX to prediction_pointsX
                        float lastPointX = pointsX.get(pointsX.size() - 1);
                        float lastPointY = pointsY.get(pointsY.size() - 1);
                        if (prediction_pointsX.isEmpty()) {
                            prediction_pointsX.add(lastPointX);
                            prediction_pointsY.add(lastPointY);
                        }

                        prediction_pointsX.add(lastPointX);
                        prediction_pointsY.add(lastPointY);

                        if (prediction_pointsX.size() < MAX_POINTS) {
                            while (prediction_pointsX.size() < MAX_POINTS) {
                                prediction_pointsX.add(lastPointX);
                                prediction_pointsY.add(lastPointY);
                            }
                        } else if (prediction_pointsX.size() > MAX_POINTS) {
                            prediction_pointsX.remove(0);
                            prediction_pointsY.remove(0);
                        }
                    }

                }

                invalidate(); // Request a redraw to update the point


                // Schedule the next execution after 100ms
                handler.postDelayed(this, 100);
            }
        };

    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //find mean and variance
        float[] result = mean_variance_points();
        setCurrentPoint(result[0], result[1]);
        float variance = result[2];
        float[] meanPoint = {meanX, meanY};

        if (!pointsX.isEmpty() && isValidPoint(meanX, meanY) && mOnValidPointListener != null) {
            mOnValidPointListener.onValidPoint(new float[]{meanX, meanY});
        }

        currentPoint[0] = meanPoint[0];
        currentPoint[1] = meanPoint[1];
        matrix = this.getImageMatrix();
        Log.d("getCurrentPoint", "meanX: " + meanX + ", meanY: " + meanY + ", mapped: [" + currentPoint[0] + ", " + currentPoint[1] + "]");
        matrix.mapPoints(meanPoint);
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(35);
        canvas.drawPoint(meanPoint[0], meanPoint[1], paint);

        // Draw a circle around the mean point using variance
        float[] radiusVector = {(float) Math.sqrt(variance), 0};
        matrix.mapVectors(radiusVector);

        paint.setColor(Color.argb(50, 0, 0, 255)); // Semi-transparent blue
        canvas.drawCircle(meanPoint[0], meanPoint[1], radiusVector[0], paint);

        if (!groundTruthX.isEmpty()) {
            for (int i = 0; i < groundTruthY.size(); i += 1) {
                float[] pts = {groundTruthX.get(i), groundTruthY.get(i)};
                matrix = this.getImageMatrix();
                matrix.mapPoints(pts);
                paint.setColor(Color.GREEN);
                paint.setStrokeWidth(50);
                canvas.drawPoint(pts[0], pts[1], paint);
            }
        }
    }

    private boolean isValidPoint(float x, float y) {
        return (x != 0f || y != 0f);
    }

    public float[] mean_variance_points() {
        if (prediction_pointsX.size() == MAX_POINTS) {
            // Calculate mean of the points
            meanX = calculateMean(prediction_pointsX);
            meanY = calculateMean(prediction_pointsY);

            // Filter out the points that are beyond the threshold distance from the mean
            List<Float> filteredPointsX = new ArrayList<>();
            List<Float> filteredPointsY = new ArrayList<>();
            for (int i = 0; i < prediction_pointsX.size(); i++) {
                float distance = distance(meanX, meanY, prediction_pointsX.get(i), prediction_pointsY.get(i));
                if (distance <= THRESHOLD) {
                    filteredPointsX.add(prediction_pointsX.get(i));
                    filteredPointsY.add(prediction_pointsY.get(i));
                }
            }

            // Calculate variance only using the filtered points
            float varianceX = calculateVariance(filteredPointsX, calculateMean(filteredPointsX));
            float varianceY = calculateVariance(filteredPointsY, calculateMean(filteredPointsY));
            float variance = (varianceX + varianceY) / 2;

            // Draw the mean point
            float[] meanPoint = {meanX, meanY};

            return new float[]{meanX, meanY, variance};
        }

        // Return a default value if the condition is not met
        return new float[]{meanX, meanY, 0};
    }

    public float[] getCurrentPoint() {
//        Log.d("getCurrentPoint", "meanX: " + meanX + ", meanY: " + meanY + ", mapped: [" + currentPoint[0] + ", " + currentPoint[1] + "]");
        return currentPoint.clone();
    }

    private void setCurrentPoint(float x, float y) {
        meanX = x;
        meanY = y;
    }

    // Set the coordinates for the point to be drawn
    public void addpoints(float x, float y) {
        pointsX.add(x);
        pointsY.add(y);
//        invalidate(); // Request a redraw to update the point
    }

    // this function convert the model predicted x y to image view scale and add the point
//    public void addPointsToScaledMap(float x, float y) {
//        Drawable drawable = this.getDrawable();
//        if (drawable != null) {
//            // the actual width height of the image is varied from different devices
//            // get the actual width height of the image in this device and normalize it
//            int intrinsicWidth = drawable.getIntrinsicWidth();
//            int intrinsicHeight = drawable.getIntrinsicHeight();
//
//            // Suppose x and y is the prediction outcome from the model
//            // below normalized the prediction outcome with the device image scale for display
//            float normalizedX = ((x / Constants.mapWidth) * intrinsicWidth);
//            float normalizedY = ((y / Constants.mapHeight) * intrinsicHeight);
//
//            currentPtX = normalizedX;
//            currentPtY = normalizedY;
//            pointsX.add(normalizedX);
//            pointsY.add(normalizedY);
//
////            Log.d("confirmation", "mapX: "+mapXY[0]+" ,mapY: "+mapXY[1]);
//            // Request a redraw of the view to update the display with the new point
//
//
//            invalidate();
//        }
//    }

    public void addPointsToScaledMapForNavigation(float x, float y) {
        Drawable drawable = this.getDrawable();
        if (drawable != null) {
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();

            Log.d("DEBUG", "intrinsicWidth: " + intrinsicWidth + ", intrinsicHeight: " + intrinsicHeight);
            Log.d("DEBUG", "Constants.mapWidth: " + Constants.mapWidth + ", Constants.mapHeight: " + Constants.mapHeight);

            float normalizedX = ((x / Constants.mapWidth) * intrinsicWidth);
            float normalizedY = ((y / Constants.mapHeight) * intrinsicHeight);

            pointsX.add(normalizedX);
            pointsY.add(normalizedY);

            // 触发 onDraw(...) 重绘
            invalidate();
        }
    }

    public void addPointsToScaledMap(float x, float y) {
//        Log.d("pointsX", pointsX.toString());
        Drawable drawable = this.getDrawable();
        if (drawable != null) {
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();

            float normalizedX = ((x / Constants.mapWidth) * intrinsicWidth);
            float normalizedY = ((y / Constants.mapHeight) * intrinsicHeight);
//            //check if outlier
//            if (!pointsX.isEmpty()) {
//                float meanX = calculateMean(pointsX);
//                float meanY = calculateMean(pointsY);
//                float distance = distance(meanX, meanY, normalizedX, normalizedY);
//                if (distance <= THRESHOLD) {
//                    pointsX.add(normalizedX);
//                    pointsY.add(normalizedY);
//                }
//            } else{
            pointsX.add(normalizedX);
            pointsY.add(normalizedY);
//            Log.d("normalizedX", String.valueOf(normalizedX) + " " + String.valueOf(x));
//            Log.d("normalizedY", String.valueOf(normalizedY) + " " + String.valueOf(y));
//            }

            invalidate(); // Request a redraw to update the point
        }
    }

    // similar function with addPointsToScaledMap, but the input is the groundtruth from the "pathXX_Location_result_.txt" instead
    public void addGroundTruthToScaledMap(float x, float y) {
        Drawable drawable = this.getDrawable();
        if (drawable != null) {
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            float normalizedX = (x / Constants.mapWidth) * intrinsicWidth;  // reference to /assets/floor_info.json
            float normalizedY = (y / Constants.mapHeight) * intrinsicHeight;
            groundTruthX.add(normalizedX);
            groundTruthY.add(normalizedY);
            invalidate();
        }
    }


    public void clearpoints() {
        pointsX.clear();
        pointsY.clear();
        groundTruthX.clear();
        groundTruthY.clear();
        currentPtX = null;
        currentPtY = null;
        invalidate(); // Request a redraw to update the point
    }

    private float calculateMean(List<Float> points) {
        float sum = 0;
        for (float point : points) {
            sum += point;
        }
        return sum / points.size();
    }

    private float calculateVariance(List<Float> points, float mean) {
        float sum = 0;
        for (float point : points) {
            sum += Math.pow(point - mean, 2);
        }
        return sum / points.size();
    }

    public void startAddingPoints() {
        // Start the repeating task
        handler.post(addLastPointRunnable);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public void ismoving() {
        // Start the repeating task
        moving = true;
    }

    public void notmoving() {
        // Start the repeating task
        moving = false;
    }

}

