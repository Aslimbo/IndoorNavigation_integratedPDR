package com.example.locate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import com.example.locate.StepDetection.StepDetectionListener;

public class NavigationOverlay extends View implements StepDetectionListener {

    // Region: Path Visualization Components
    private List<PointF> fullPathPoints;
    private Path fullPath;
    private Path traveledPath;
    private Paint fullPathPaint;
    private Paint traveledPathPaint;

    // Region: Actual Path Tracking
    private Path actualPath;
    private Paint actualPathPaint;
    private List<PointF> actualPathPoints = new ArrayList<>();

    // Region: Direction Visualization
    private Paint arrowPaint;
    private float currentAzimuth = 0f;
    private float stepLength = 0.4f;

    // Region: Coordinate System Parameters
    // mapWidth=(2643.49);
    // mapHeight=(3264.33);
    private final float BASE_WIDTH = 928f;
    private final float BASE_HEIGHT = 1168f;
    private Matrix transformMatrix = new Matrix();
    private PointF currentPosition;
    private float scaleFactor = 35.4f; // Pixels per meter

    // Region: Path Calculation
    private float accumulatedDistance = 0f;
    private float traveledDistance = 0f;
    private List<Float> segmentLengths = new ArrayList<>();
    private float totalPathLength = 0f;

    public NavigationOverlay(Context context) {
        super(context);
        init();
    }

    public NavigationOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NavigationOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialize the overlay by creating necessary objects and setting up paints.
     */
    private void init() {
        fullPathPoints = new ArrayList<>();
        fullPath = new Path();
        traveledPath = new Path();
        actualPath = new Path();

        // Initialize paints
        fullPathPaint = createPaint(Color.RED, 5f);
        traveledPathPaint = createPaint(Color.BLUE, 5f);
        actualPathPaint = createPaint(Color.GREEN, 8f);
        arrowPaint = createPaint(Color.MAGENTA, 5f);
        arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    private Paint createPaint(int color, float strokeWidth) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        return paint;
    }

    @Override
    public void onStepDetected(int stepCount, double stepLength, float azimuth) {
        Log.d("PATH_DEBUG", "Received step - length:" + stepLength
                + " azimuth:" + azimuth
                + " scaleFactor:" + scaleFactor);
        this.stepLength = (float) stepLength;
        this.currentAzimuth = azimuth;

        if (totalPathLength <= 0) {
            Log.e("PATH_DEBUG", "Path not initialized!");
            return; // 路径未初始化时直接返回
        }
        float[] newPos = calculateNewPosition(stepLength, azimuth);
        updateActualPath(newPos[0], newPos[1]);
        Log.d("PATH_DEBUG", "New position - X:" + newPos[0] + " Y:" + newPos[1]);
        accumulatedDistance += (float) stepLength * scaleFactor;
        accumulatedDistance = Math.min(accumulatedDistance, totalPathLength);
        Log.d("PATH_DEBUG", "Total traveled: " + accumulatedDistance
                + "/" + totalPathLength
                + " (" + (accumulatedDistance/totalPathLength*100) + "%)");
        postInvalidate();
    }

    @Override
    public void onStepError(String errorMessage) {
        Log.e("NavOverlay", "Step Error: " + errorMessage);
        // Add visual error indication if needed
    }

    private float[] calculateNewPosition(double stepLength, float azimuth) {
        double radians = Math.toRadians(azimuth);
        float dx = (float) (stepLength * Math.sin(radians) * scaleFactor);
        float dy = (float) (stepLength * Math.cos(radians) * scaleFactor);

        if (actualPathPoints.isEmpty()) {
            PointF start = !fullPathPoints.isEmpty() ? fullPathPoints.get(0) : new PointF(0, 0);
            return new float[]{start.x + dx, start.y - dy};
        }

        PointF last = actualPathPoints.get(actualPathPoints.size() - 1);
        return new float[]{last.x + dx, last.y - dy};
    }

    private void updateActualPath(float x, float y) {
        Log.d("PATH_DEBUG", "Adding point - X:" + x + " Y:" + y);
        PointF newPoint = new PointF(x, y);
        if (actualPathPoints.isEmpty()) {
            actualPath.moveTo(x, y);
        } else {
            actualPath.lineTo(x, y);
        }
        actualPathPoints.add(newPoint);
    }

    /**
     * Set the full navigation path.
     * The input array should contain coordinates in the order: [x1, y1, x2, y2, ..., xn, yn]
     * Coordinates are assumed to be in the base coordinate system and will be scaled.
     *
     * @param points The array of path points.
     */
    public void setNavigationPath(float[] points) {
        fullPathPoints.clear();
        fullPath.reset();
        segmentLengths.clear();
        totalPathLength = 0f;
        accumulatedDistance = 0f;
        if (points != null && points.length >= 2) {
            PointF prevPoint = null;
            for (int i = 0; i < points.length; i += 2) {
                PointF current = new PointF(points[i], points[i + 1]);
                fullPathPoints.add(current);

                if (prevPoint != null) {
                    float length = (float) Math.hypot(current.x - prevPoint.x, current.y - prevPoint.y);
                    segmentLengths.add(length);
                    totalPathLength += length;
                }
                prevPoint = current;
            }

            if (!fullPathPoints.isEmpty()) {
                PointF start = fullPathPoints.get(0);
                fullPath.moveTo(start.x, start.y);
                for (int i = 1; i < fullPathPoints.size(); i++) {
                    PointF pt = fullPathPoints.get(i);
                    fullPath.lineTo(pt.x, pt.y);
                }
            }
        }
        postInvalidate();

        Log.d("PATH_DEBUG", "Received path points: " + points.length/2 + " points");
        Log.d("PATH_DEBUG", "Total path length: " + totalPathLength + " pixels");
        Log.d("PATH_DEBUG", "First point: (" + fullPathPoints.get(0).x + "," + fullPathPoints.get(0).y + ")");
        Log.d("PATH_DEBUG", "Last point: (" + fullPathPoints.get(fullPathPoints.size()-1).x + "," + fullPathPoints.get(fullPathPoints.size()-1).y + ")");
    }

    public void resetNavigation() {
        actualPathPoints.clear();
        actualPath.reset();
        traveledDistance = 0f;
        postInvalidate();
    }

    /**
     * ============================================================================================
     * Region: Drawing Operations
     * onDraw, Several drawing methods, reset methods
     */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Apply the transformation matrix from the map view.
        canvas.save();
        Matrix combinedMatrix = new Matrix();
        combinedMatrix.setConcat(transformMatrix, getImageBaseMatrix());
        canvas.setMatrix(combinedMatrix);
        // Draw the full navigation path (scaled based on onSizeChanged values)
        drawFullPath(canvas);
        drawTraveledPath(canvas);
        drawActualPath(canvas);
        drawDirectionArrow(canvas);

        canvas.restore();
    }

    private void drawFullPath(Canvas canvas) {
        if (!fullPath.isEmpty()) {
            canvas.drawPath(fullPath, fullPathPaint);
        }
    }

    private void drawTraveledPath(Canvas canvas) {
//        if (currentPosition != null && !fullPathPoints.isEmpty()) {
//            traveledPath.reset();
//            traveledPath.moveTo(fullPathPoints.get(0).x, fullPathPoints.get(0).y);
//
//            PointF projection = getProjectionOnPath(currentPosition);
//            if (projection != null) {
//                int segmentIndex = getSegmentIndexForProjection(currentPosition);
//                for (int i = 1; i <= segmentIndex; i++) {
//                    traveledPath.lineTo(fullPathPoints.get(i).x, fullPathPoints.get(i).y);
//                }
//                traveledPath.lineTo(projection.x, projection.y);
//                canvas.drawPath(traveledPath, traveledPathPaint);
//            }
//        }
        if (fullPathPoints.isEmpty()) {
            Log.d("PATH_DEBUG", "No path points available");
            return;
        }

        Log.d("PATH_DEBUG", "Start drawing with accumulated: " + accumulatedDistance);
        traveledPath.reset();
        traveledPath.moveTo(fullPathPoints.get(0).x, fullPathPoints.get(0).y);

        float remaining = accumulatedDistance;
        for (int i = 0; i < segmentLengths.size(); i++) {
            float segmentLen = segmentLengths.get(i);

            if (remaining <= segmentLen) {
                PointF start = fullPathPoints.get(i);
                PointF end = fullPathPoints.get(i + 1);
                float ratio = remaining / segmentLen;

                // 计算线段上的插值点
                float x = start.x + (end.x - start.x) * ratio;
                float y = start.y + (end.y - start.y) * ratio;
                traveledPath.lineTo(x, y);
                break;
            } else {
                // 完整绘制当前线段
                traveledPath.lineTo(fullPathPoints.get(i + 1).x, fullPathPoints.get(i + 1).y);
                remaining -= segmentLen;
            }
        }

        canvas.drawPath(traveledPath, traveledPathPaint);
    }

    private void drawActualPath(Canvas canvas) {
        if (!actualPath.isEmpty()) {
            canvas.drawPath(actualPath, actualPathPaint);
        }
    }

    private void drawDirectionArrow(Canvas canvas) {
        if (!actualPathPoints.isEmpty()) {
            PointF lastPoint = actualPathPoints.get(actualPathPoints.size() - 1);
            float arrowLength = 50f;
            float angle = 30f;

            double radians = Math.toRadians(currentAzimuth);
            float endX = lastPoint.x + arrowLength * (float) Math.sin(radians);
            float endY = lastPoint.y - arrowLength * (float) Math.cos(radians);

            // Draw main arrow line
            canvas.drawLine(lastPoint.x, lastPoint.y, endX, endY, arrowPaint);

            // Draw arrow head
            Path arrowHead = new Path();
            arrowHead.moveTo(endX, endY);
            arrowHead.lineTo(
                    endX - 20f * (float) Math.sin(radians - Math.toRadians(angle)),
                    endY + 20f * (float) Math.cos(radians - Math.toRadians(angle))
            );
            arrowHead.lineTo(
                    endX - 20f * (float) Math.sin(radians + Math.toRadians(angle)),
                    endY + 20f * (float) Math.cos(radians + Math.toRadians(angle))
            );
            arrowHead.close();
            canvas.drawPath(arrowHead, arrowPaint);
        }
    }

    public void resetActualPath() {
        actualPath.reset();
        actualPathPoints.clear();
        invalidate();
        Log.d("PATH_DEBUG", "Actual path cleared");
    }
    /**
     * ============================================================================================
     * Region: Utility Methods
     * getImageBaseMatrix, setTransformMatrix,updateCurrentPosition
     */
    private Matrix getImageBaseMatrix() {
        Matrix matrix = new Matrix();
        float scaleX = 3248f / BASE_WIDTH;
        float scaleY = 4088f / BASE_HEIGHT;
        // 应用非均匀缩放
        matrix.postScale(scaleX, scaleY);
        Log.d("BaseMatrix", "scaleX: " + scaleX + " scaleY: " + scaleY);
        return matrix;
    }

    /**
     * Set the transformation matrix.
     * This matrix is typically obtained from customimageview's ImageMatrix.
     * It will be applied to the canvas in onDraw() so that the overlay moves with the map.
     *
     * @param matrix The transformation matrix.
     */
    public void setTransformMatrix(Matrix matrix) {
        if (matrix != null) {
            transformMatrix.set(matrix);
        } else {
            transformMatrix.reset();
        }
        invalidate();
    }

    /**
     * Update the current position marker.
     *
     * @param pos The current position as a PointF.
     */
    public void updateCurrentPosition(PointF pos) {
        this.currentPosition = pos;
        invalidate();
    }

    /**
     * Find the projection of a given point onto the full path.
     *
     * @param currentPos The current position.
     * @return The projection point on the path.
     */
    private PointF getProjectionOnPath(PointF currentPos) {
        if (fullPathPoints.size() < 2) return null;
        float minDistance = Float.MAX_VALUE;
        PointF bestProjection = null;
        for (int i = 0; i < fullPathPoints.size() - 1; i++) {
            PointF a = fullPathPoints.get(i);
            PointF b = fullPathPoints.get(i + 1);
            PointF proj = projectPointOnSegment(currentPos, a, b);
            float distance = distance(currentPos, proj);
            if (distance < minDistance) {
                minDistance = distance;
                bestProjection = proj;
            }
        }
        return bestProjection;
    }

    /**
     * Get the index of the segment (ending index) where the projection of currentPos lies.
     *
     * @param currentPos The current position.
     * @return The segment index (i+1).
     */
    private int getSegmentIndexForProjection(PointF currentPos) {
        if (fullPathPoints.size() < 2) return 0;
        float minDistance = Float.MAX_VALUE;
        int bestIndex = 0;
        for (int i = 0; i < fullPathPoints.size() - 1; i++) {
            PointF a = fullPathPoints.get(i);
            PointF b = fullPathPoints.get(i + 1);
            PointF proj = projectPointOnSegment(currentPos, a, b);
            float distance = distance(currentPos, proj);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i + 1;
            }
        }
        return bestIndex;
    }

    /**
     * Project point P onto segment AB.
     *
     * @param P The point to project.
     * @param A Start point of the segment.
     * @param B End point of the segment.
     * @return The projected point on segment AB.
     */
    private PointF projectPointOnSegment(PointF P, PointF A, PointF B) {
        float vx = B.x - A.x;
        float vy = B.y - A.y;
        float mag2 = vx * vx + vy * vy;
        if (mag2 == 0) return A;  // A and B are the same point
        float u = ((P.x - A.x) * vx + (P.y - A.y) * vy) / mag2;
        u = Math.max(0, Math.min(1, u));  // Clamp u between 0 and 1
        return new PointF(A.x + u * vx, A.y + u * vy);
    }

    /**
     * Compute the Euclidean distance between two points.
     *
     * @param a First point.
     * @param b Second point.
     * @return The Euclidean distance.
     */
    private float distance(PointF a, PointF b) {
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Do not consume touch events so that they are passed to underlying views.
//        Log.d("NavigationOverlay", "onTouchEvent triggered: " + event.toString());
        return false;
    }
}