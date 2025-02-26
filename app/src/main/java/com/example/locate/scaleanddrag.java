package com.example.locate;

import static java.lang.Math.sqrt;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class scaleanddrag {
    private ImageView imageView_map;
    private FrameLayout mPhotoBox;
    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();
    private Matrix inverseMatrix = new Matrix();

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    static final int PRESS = 3;
    int mode = NONE;
    int drag_count = 0;
    float map_scale = 0.0f;

    private static final float MIN_DRAG_DISTANCE = 10f; // Minimum drag distance
    private static final float MIN_SCALE = 0.4f; // Minimum zoom level
    private static final float MAX_SCALE = 1.5f; // Maximum zoom level

    // Remember some things for zooming
    PointF start = new PointF();
    PointF mid = new PointF();
    float oldDist = 1f;

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    public boolean onTouch(View v, MotionEvent event) {
        // Try to cast v to ImageView; if not, attempt to get an ImageView from its children if v is a ViewGroup.
        ImageView view = (v instanceof ImageView) ? (ImageView) v : null;
        if (view == null && v instanceof FrameLayout) {
            FrameLayout fl = (FrameLayout) v;
            for (int i = 0; i < fl.getChildCount(); i++) {
                View child = fl.getChildAt(i);
                if (child instanceof ImageView) {
                    view = (ImageView) child;
                    break;
                }
            }
        }
        float[] values = new float[9];
        matrix.getValues(values);
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = PRESS;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;

            case MotionEvent.ACTION_UP:
                drag_count = 0;
                if (mode == PRESS) {
                    // Only proceed if an ImageView is available
                    if (view != null) {
                        float[] touchPoint = new float[]{event.getX(), event.getY()};
                        // Invert the ImageView's matrix to convert touch coordinates to bitmap coordinates
                        view.getImageMatrix().invert(inverseMatrix);
                        inverseMatrix.mapPoints(touchPoint);
                        float x = touchPoint[0];
                        float y = touchPoint[1];
                        Drawable drawable = view.getDrawable();
                        if (drawable != null) {
                            MainActivity.addPointsToScaledMap_imageView(touchPoint[0], touchPoint[1]);
                            // For debug: convert to normalized coordinates and then to real-world coordinates
                            int intrinsicWidth = drawable.getIntrinsicWidth();
                            int intrinsicHeight = drawable.getIntrinsicHeight();
                            float normalizedX = x / intrinsicWidth;
                            float normalizedY = y / intrinsicHeight;
                            float[] realworldXY = {(normalizedX * Constants.mapWidth), (normalizedY * Constants.mapHeight)};
                            Log.d("scaled_xy", "x = " + realworldXY[0] + ", y = " + realworldXY[1]);
                        }
                    } else {
                        Log.e("scaleanddrag", "ACTION_UP: No ImageView found for processing touch point.");
                    }
                } else if (mode == DRAG && view != null) {
                    // When dragging, perform boundary rectification
                    rectifyTranslation(view);
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mode == ZOOM && view != null) {
                    rectifyTranslation(view);
                }
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    // Dragging logic
                    float dx = event.getX() - start.x;
                    float dy = event.getY() - start.y;

                    matrix.set(savedMatrix);
                    matrix.postTranslate(dx, dy);
                } else if (mode == ZOOM) {
                    // Zooming logic
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        float scale = newDist / oldDist;
                        matrix.getValues(values);

                        float currentScale = values[Matrix.MSCALE_X];
                        float targetScale = Math.max(MIN_SCALE, Math.min(currentScale * scale, MAX_SCALE));

                        scale = targetScale / currentScale; // Adjust scale to stay within limits
                        matrix.set(savedMatrix);
                        matrix.postScale(scale, scale, mid.x, mid.y);
                        // Check bounds after scaling
                        RectF bounds = getMatrixBounds(matrix, v);
                        float dx = 0, dy = 0;
                        if (bounds.left > 0) dx = -bounds.left; // Prevent left overflow
                        if (bounds.top > 0) dy = -bounds.top;   // Prevent top overflow
                        if (bounds.right < view.getWidth())
                            dx = view.getWidth() - bounds.right; // Prevent right overflow
                        if (bounds.bottom < view.getHeight())
                            dy = view.getHeight() - bounds.bottom; // Prevent bottom overflow

                        matrix.postTranslate(dx, dy); // Adjust position
                    }
                } else if (mode == PRESS) {
                    // Detect drag transition
                    float dx = event.getX() - start.x;
                    float dy = event.getY() - start.y;

                    if (Math.sqrt(dx * dx + dy * dy) > MIN_DRAG_DISTANCE) {
                        mode = DRAG; // Transition to DRAG mode
                    }
                }
                break;
        }

        // Only update the image matrix if the view is an ImageView
        if (v instanceof ImageView) {
            ((ImageView) v).setImageMatrix(matrix);
        }
        return true; // indicate event was handled
    }

    private RectF getMatrixBounds(Matrix matrix, View view) {
        ImageView imageView = null;
        // If view is an ImageView, cast it; otherwise, try to find an ImageView child if view is a ViewGroup.
        if (view instanceof ImageView) {
            imageView = (ImageView) view;
        } else if (view instanceof FrameLayout) {
            // Try to get the first child that is an ImageView.
            for (int i = 0; i < ((FrameLayout) view).getChildCount(); i++) {
                View child = ((FrameLayout) view).getChildAt(i);
                if (child instanceof ImageView) {
                    imageView = (ImageView) child;
                    break;
                }
            }
        }

        if (imageView == null) {
            // If no ImageView is found, return a default RectF using view's dimensions.
            return new RectF(0, 0, view.getWidth(), view.getHeight());
        }

        Drawable drawable = imageView.getDrawable();
        if (drawable == null) return new RectF(0, 0, 0, 0);

        // 获取图片的原始尺寸
        RectF drawableRect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(drawableRect);
        return drawableRect;
    }

    private void rectifyTranslation(View v) {
        ImageView imageView = null;
        if (v instanceof ImageView) {
            imageView = (ImageView) v;
        } else if (v instanceof FrameLayout) {
            // Attempt to retrieve the first ImageView child from the FrameLayout.
            for (int i = 0; i < ((FrameLayout) v).getChildCount(); i++) {
                View child = ((FrameLayout) v).getChildAt(i);
                if (child instanceof ImageView) {
                    imageView = (ImageView) child;
                    break;
                }
            }
        }
        if (imageView == null) {
            // If no ImageView found, do nothing.
            return;
        }

        RectF bounds = getMatrixBounds(matrix, imageView);
        float dx = 0, dy = 0;

        // 宽度比视图大时，贴左右边；比视图小时，则居中
        if (bounds.width() >= imageView.getWidth()) {
            if (bounds.left > 0) {
                dx = -bounds.left;
            } else if (bounds.right < imageView.getWidth()) {
                dx = imageView.getWidth() - bounds.right;
            }
        } else {
            // 让小图居中
            dx = (imageView.getWidth() - bounds.width()) / 2f - bounds.left;
        }

        // 高度同理
        if (bounds.height() >= imageView.getHeight()) {
            if (bounds.top > 0) {
                dy = -bounds.top;
            } else if (bounds.bottom < imageView.getHeight()) {
                dy = imageView.getHeight() - bounds.bottom;
            }
        } else {
            // 居中
            dy = (imageView.getHeight() - bounds.height()) / 2f - bounds.top;
        }

        matrix.postTranslate(dx, dy);
    }
}
