/**
 * Navigation page
 * Created on 2024/10/23
 * Added by: LZY WZD
 */

package com.example.locate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.JsonReader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class NavigationActivity extends AppCompatActivity implements ImuDataCallback {
    private BLEManagerMain BLEManagerMain;
    private WifiManagerMain wifiManager;
    private UsbSerialManager usbSerialManager;
    private BLEserialManager BLEserialManager;
    private PermissionManager permissionManager;
    private SensorManagerMain SensorManagerMain;
    private PDRKalman pdrKalman;
    private StepDetection StepDetection;
    private String mode = "";
    // mode
    // BLE          WIFI        USB         BLE Serial
    private boolean isLocating = false;
    private static boolean isObstaclesLoaded = false;
    private static Set<List<Integer>> obstacles = new HashSet<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, List<Integer>> fixedPoints = new HashMap<String, List<Integer>>() {{
        //[y, x]
        put("DirectorOffice", Arrays.asList(1081, 779));
        put("Pantry", Arrays.asList(809, 476));
//        put("Pantry", Arrays.asList(2890, 1738));
        put("PostdocArea", Arrays.asList(316, 244));
        put("DemoArea", Arrays.asList(969, 499));
        put("CommonRoom", Arrays.asList(332, 734));
        put("RAArea", Arrays.asList(252, 414));
        put("AdminOffice", Arrays.asList(733, 768));
        put("MeetingRoom", Arrays.asList(692, 581));
        put("Visitors", Arrays.asList(506, 763));
    }};
    private TextToSpeech textToSpeech; //TextSpeech
    private ImageView imageViewMap;
    private Bitmap bitmap;
    private Bitmap original_map_bitmap;
    private Context context;
    static customimageview imageView_map;
    private Future<?> currentTask;
    private Runnable drawPathRunnable;
    private ProgressBar progressBar;
    private List<List<Integer>> path;
    /*fake*/
    private List<List<Integer>> fixedPath = new ArrayList<>(); // 保存计算好的路径
    private volatile boolean isNavigating = false;
    private volatile boolean isMapLoaded = false;
    //preprocess image
    private TextView textViewStartEndCoordinates;
    private scaleanddrag scaleanddrag;
    private int navigationTaskId = 0;  // Every Single Navigation Task
    //choose point
    private EditText editTextStartPoint;
    private EditText editTextEndPoint;
    private boolean isSelectingStartPoint = false;
    private boolean isSelectingEndPoint = false;
    private NavigationOverlay navigationOverlay;
    private List<Integer> currentPosition = null;
    private List<Integer> fixedStart = null;
    private List<Integer> s_start = null;
    private List<Integer> s_goal = null;
    private List<Integer> s_start1 = null;
    private List<Integer> s_goal1 = null;
    private List<Integer> currentStartPoint = null;
    private List<Integer> currentEndPoint = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        textViewStartEndCoordinates = findViewById(R.id.textViewStartEndCoordinates);
        context = this;

        progressBar = findViewById(R.id.progressBar);
        imageView_map = findViewById(R.id.imageView_navigation_map);
        navigationOverlay = findViewById(R.id.navigationOverlay);
        StepDetection stepDetection = new StepDetection(this, pdrKalman);
        stepDetection.setStepDetectionListener(navigationOverlay);
        SensorManagerMain = new SensorManagerMain(this);
        SensorManagerMain.setCallback(this);

        // Register the callback for real-time position updates from customimageview
        imageView_map.setOnValidPointListener(validPoint -> {
            Drawable drawable = imageView_map.getDrawable();
            if (drawable != null && bitmap != null) {
                int intrinsicWidth = drawable.getIntrinsicWidth();
                int intrinsicHeight = drawable.getIntrinsicHeight();
                int actualWidth = bitmap.getWidth();
                int actualHeight = bitmap.getHeight();
                float factorX = (float) actualWidth / intrinsicWidth;
                float factorY = (float) actualHeight / intrinsicHeight;
                int pixelX = Math.round(validPoint[0] * factorX);
                int pixelY = Math.round(validPoint[1] * factorY);
                currentPosition = Arrays.asList(pixelY, pixelX);
                Log.d("NaviListener", "Updated currentPosition from callback: " + currentPosition);
                // Update the NavigationOverlay with the latest current position.
                // Convert currentPosition (List<Integer>) to PointF.
                PointF pos = new PointF(currentPosition.get(1), currentPosition.get(0));
                navigationOverlay.updateCurrentPosition(pos);
            }
        });

        //Initialize TTS
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported");
                }
            } else {
                Log.e("TTS", "Initialization failed");
            }
        });

        setupViews();
        setupButtons();

        //point choosing
//        editTextStartPoint = findViewById(R.id.editText_start_point);
//        editTextEndPoint = findViewById(R.id.editText_end_point);
//        TextInputLayout textInputLayoutStart = findViewById(R.id.textInputLayout_start);
//        TextInputLayout textInputLayoutEnd = findViewById(R.id.textInputLayout_end);

//        editTextStartPoint.setOnClickListener(v -> {
//            isSelectingStartPoint = true;
//            isSelectingEndPoint = false;
//            Toast.makeText(context, "Please select the start point on the map", Toast.LENGTH_SHORT).show();
//        });
//
//        editTextEndPoint.setOnClickListener(v -> {
//            isSelectingEndPoint = true;
//            isSelectingStartPoint = false;
//            Toast.makeText(context, "Please select the end point on the map", Toast.LENGTH_SHORT).show();
//        });

//        editTextStartPoint.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus) {
//                textInputLayoutStart.setHint(""); // 清除 hint，当有焦点时不显示
//            } else if (editTextStartPoint.getText().toString().isEmpty()) {
//                textInputLayoutStart.setHint("Choose the start point"); // 当没有输入时重新设置 hint
//            }
//        });
//
//        editTextEndPoint.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus) {
//                textInputLayoutEnd.setHint(""); // 清除 hint，当有焦点时不显示
//            } else if (editTextEndPoint.getText().toString().isEmpty()) {
//                textInputLayoutEnd.setHint("Choose the end point"); // 当没有输入时重新设置 hint
//            }
//        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupViews() {
        scaleanddrag = new scaleanddrag();
        // Bind the touch listener to the customimageview (map view) instead of the entire parent container.
        imageView_map.setOnTouchListener((v, event) -> {
            // Process the touch event using scaleanddrag utility.
            boolean handled = scaleanddrag.onTouch(v, event);
            // Get the current transformation matrix from scaleanddrag.
            Matrix currentMatrix = new Matrix(imageView_map.getImageMatrix());
            // Update the NavigationOverlay with the same transformation matrix.
            navigationOverlay.setTransformMatrix(currentMatrix);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Get touch coordinates relative to the map view.
                    float[] touchPoint = new float[]{event.getX(), event.getY()};
                    Log.d("NavigationActivity Touch Coords", "Original coordinates: " + Arrays.toString(touchPoint));
                    // Convert these coordinates from the map view to bitmap coordinates.
                    Matrix inverse = new Matrix();
                    imageView_map.getImageMatrix().invert(inverse);
                    inverse.mapPoints(touchPoint);
                    int imageX = (int) touchPoint[0];
                    int imageY = (int) touchPoint[1];
                    Drawable drawable = imageView_map.getDrawable();
                    if (drawable != null && bitmap != null) {
                        float intrinsicWidth = drawable.getIntrinsicWidth();
                        float intrinsicHeight = drawable.getIntrinsicHeight();
                        float normalizedX = imageX / intrinsicWidth;
                        float normalizedY = imageY / intrinsicHeight;
                        if (normalizedX >= 0 && normalizedX <= 1 && normalizedY >= 0 && normalizedY <= 1) {
                            int pixelX = (int) (normalizedX * 928);  // Base width
                            int pixelY = (int) (normalizedY * 1168); // Base height
                            Log.d("NavigationActivity Touch Coords", "Bitmap coordinates: (" + pixelX + ", " + pixelY + ")");
                        }
                    }
                    if (isSelectingStartPoint) {
                        Log.d("NavigationActivity", "Selecting start point: (" + imageX + ", " + imageY + ")");
                        if (imageX < 0 || imageY < 0 || imageX >= bitmap.getWidth() || imageY >= bitmap.getHeight()) {
                            Toast.makeText(context, "Please click within the map area", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        clearOldPoint(bitmap, currentStartPoint);
                        clearDashedLine(bitmap, currentStartPoint, s_start1);
                        currentStartPoint = Arrays.asList(imageY, imageX);
                        s_start = Arrays.asList(imageY, imageX);
                        isSelectingStartPoint = false;
                        runOnUiThread(() -> {
                            editTextStartPoint.setText("Start: (" + imageX + ", " + imageY + ")");
                            drawPoint(bitmap, imageX, imageY, Color.BLUE);
                            updateImageView(bitmap);
                        });
                        if (obstacles.contains(s_start)) {
                            findNearestNonObstaclePointAsync(s_start, nearestPoint -> {
                                if (nearestPoint != null) {
                                    s_start1 = nearestPoint;
                                    drawDashedLine(bitmap, s_start, s_start1, Color.BLUE);
                                    updateImageView(bitmap);
                                    Toast.makeText(context, "Start point is inside an obstacle, a path to the nearest accessible point has been drawn", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(context, "Cannot find an accessible area near the start point", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            s_start1 = s_start;
                        }
                        return true;
                    } else if (isSelectingEndPoint) {
                        if (imageX < 0 || imageY < 0 || imageX >= bitmap.getWidth() || imageY >= bitmap.getHeight()) {
                            Toast.makeText(context, "Please click within the map area", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        clearOldPoint(bitmap, currentEndPoint);
                        clearDashedLine(bitmap, s_goal1, currentEndPoint);
                        currentEndPoint = Arrays.asList(imageY, imageX);
                        Log.d("NavigationActivity", "Selecting end point: (" + imageX + ", " + imageY + ")");
                        s_goal = Arrays.asList(imageY, imageX);
                        isSelectingEndPoint = false;
                        runOnUiThread(() -> {
                            editTextEndPoint.setText("End: (" + imageX + ", " + imageY + ")");
                            Log.d("NavigationActivity", "End point updated in EditText");
                            drawPoint(bitmap, imageX, imageY, Color.GREEN);
                            updateImageView(bitmap);
                        });
                        if (obstacles.contains(s_goal)) {
                            findNearestNonObstaclePointAsync(s_goal, nearestPoint -> {
                                if (nearestPoint != null) {
                                    s_goal1 = nearestPoint;
                                    drawDashedLine(bitmap, s_goal1, s_goal, Color.GREEN);
                                    updateImageView(bitmap);
                                    Toast.makeText(context, "End point is inside an obstacle, a path to the nearest accessible point has been drawn.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(context, "Cannot find an accessible area near the end point.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            s_goal1 = s_goal;
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    hideKeyboard();
                    break;
            }
            return handled;
        });
        new Thread(() -> {
            try {
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
                // 禁用 BitmapFactory 的自动缩放
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false; // 禁止根据屏幕密度缩放
                options.inDensity = 160; // 假设原始图片是 mdpi (1x)
                options.inTargetDensity = 160; // 设置目标密度为 mdpi
                options.inMutable = true;
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.map_scaled2, options);
                if (bitmap == null) {
                    throw new IOException("Failed to load the map image.");
                }
                Log.d("NavigationActivity", "Loaded Bitmap dimensions: Width = " + bitmap.getWidth() + ", Height = " + bitmap.getHeight());
                original_map_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                // 在主线程中更新 ImageView
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    imageView_map.setImageBitmap(bitmap);
                    imageView_map.setScaleType(ImageView.ScaleType.MATRIX); // 设置为 MATRIX 模式
                    if (!isObstaclesLoaded) {
                        loadObsJsonAsync();
                    }
                });
            } catch (IOException e) {
                Log.e("NavigationActivity", "Error loading map image", e);
            }
        }).start();
    }

    private void setupButtons() {
        Button buttonQuitNavigation = findViewById(R.id.buttonQuitNavigation);
        Button buttonStartNavigation = findViewById(R.id.buttonStartNavigation);
        //choose destination
        Button buttonProfessorOffice = findViewById(R.id.buttonDirectorOffice);
        Button buttonPantry = findViewById(R.id.buttonPantry);
        Button buttonPostdocArea = findViewById(R.id.buttonPostdocArea);

        FloatingActionButton buttonLocateMe = findViewById(R.id.buttonLocateMe);

        buttonQuitNavigation.setOnClickListener(v -> {
            stopNavigation();

            Intent intent = new Intent(NavigationActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);

            finish();
            overridePendingTransition(0, 0);
        });

        buttonStartNavigation.setOnClickListener(v -> {
            if (!isNavigating) {
                isNavigating = true;
                buttonStartNavigation.setText("Stop Navigation");
                Log.d("NavigationActivity", "Starting Navigation");

                if (fixedPath != null && !fixedPath.isEmpty()
                        && s_start != null && s_goal != null
                        && s_start.equals(fixedPath.get(0)) && s_goal.equals(fixedPath.get(fixedPath.size() - 1))) {
                    clearCanvas();
                    drawPathOnCanvas(bitmap, fixedPath);  // 重新绘制已保存的路径
                    updateImageView(bitmap);
                } else {
                    performNavigation();  // 如果没有路径，或者起点终点变动，则重新计算
                }
            } else {
                isNavigating = false;
                buttonStartNavigation.setText("Start Navigation");
                Log.d("NavigationActivity", "Stopping Navigation");
                stopNavigation();
            }
        });

        buttonProfessorOffice.setOnClickListener(v -> startNavigation("DirectorOffice"));
        buttonPantry.setOnClickListener(v -> startNavigation("Pantry"));
        buttonPostdocArea.setOnClickListener(v -> startNavigation("PostdocArea"));
        buttonLocateMe.setOnClickListener(v -> {
            stopLocating();
            mode = "BLE";
            clearMap();
            imageView_map.startAddingPoints();
            initializeManagers();
            startLocating();
            Toast.makeText(context, "Locating... waiting for valid position", Toast.LENGTH_SHORT).show();

            // Delay to allow dynamic data to update
            handler.postDelayed(() -> {
                float[] currentCoord = imageView_map.getCurrentPoint();
                if (currentCoord != null && currentCoord.length == 2) {
                    Drawable drawable = imageView_map.getDrawable();
                    if (drawable != null && bitmap != null) {
                        int intrinsicWidth = drawable.getIntrinsicWidth();
                        int intrinsicHeight = drawable.getIntrinsicHeight();
                        int actualWidth = bitmap.getWidth();
                        int actualHeight = bitmap.getHeight();
                        float factorX = (float) actualWidth / intrinsicWidth;
                        float factorY = (float) actualHeight / intrinsicHeight;
                        int pixelX = Math.round(currentCoord[0] * factorX);
                        int pixelY = Math.round(currentCoord[1] * factorY);
                        fixedStart = Arrays.asList(pixelY, pixelX);
                        Log.d("NavigationActivity", "Fixed start point: " + fixedStart);
                    }
                }
            }, 10000);  // Delay
        });
    }

    private void startLocating() {
        // 先检查并请求权限
        permissionManager.requestInitialPermissions();

        switch (mode) {
            case "USB":
                usbSerialManager.start();
                usbSerialManager.startRepeatingTask();
                break;
            case "WIFI":
                wifiManager.startWifiScan();
                wifiManager.startRepeatingTask();
                break;
            case "BLE":
                BLEManagerMain.startBleScan();
                BLEManagerMain.startRepeatingTask();
                SensorManagerMain.start();
                break;
            case "BLE Serial":
                BLEserialManager.start();
                BLEserialManager.startRepeatingTask();
                break;
            default:
                // 如果 mode 为空或未定义，则可做个提示
                Toast.makeText(this, "Please set a valid mode before locating!", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void stopLocating() {
        if (wifiManager != null) {
            wifiManager.cleanup();
            wifiManager = null;
        }

        if (usbSerialManager != null && usbSerialManager.connected) {
            usbSerialManager.cleanup();
            usbSerialManager = null;
        }

        if (BLEManagerMain != null) {
            BLEManagerMain.cleanup();
            BLEManagerMain = null;
        }

        if (BLEserialManager != null) {
            BLEserialManager.cleanup();
            BLEserialManager = null;
        }
    }

    @SuppressLint("CutPasteId")
    private void initializeManagers() {
        permissionManager = new PermissionManager(this);
        pdrKalman = new PDRKalman();
        StepDetection = new StepDetection(this, pdrKalman);
        SensorManagerMain = new SensorManagerMain(this);
        SensorManagerMain.setCallback(StepDetection);
        usbSerialManager = new UsbSerialManager(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));
        wifiManager = new WifiManagerMain(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));
        BLEManagerMain = new BLEManagerMain(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text), pdrKalman);
        BLEserialManager = new BLEserialManager(this, findViewById(R.id.raw_RSSI_text), findViewById(R.id.pso_Error_text));


    }

    public static void addPointsToScaledMapNavigation(Float x, Float y) {
        if (imageView_map != null) {
            imageView_map.addPointsToScaledMapForNavigation(x, y);
        }
    }

    public static void addGroundTruthToScaledMapNavigation(Float x, Float y) {
        imageView_map.addGroundTruthToScaledMap(x, y);
    }

    public static void clearMap() {
        imageView_map.clearpoints();
    }

    private void startNavigation(String destinationKey) {
        if (navigationOverlay != null) {
            navigationOverlay.resetActualPath();
            Log.d("NavigationActivity", "Cleared previous path");
        }
        if (fixedStart == null) {
            Toast.makeText(context, "Unknown Start, please locate me first", Toast.LENGTH_SHORT).show();
            return;
        }
        s_start = fixedStart;
        List<Integer> destination = fixedPoints.get(destinationKey);

        Log.d("NavigationActivity", "Start point:Coordinates: " + s_start);
        Log.d("NavigationActivity", "End point: " + destinationKey + " - Coordinates: " + destination);

        if (obstacles.contains(s_start)) {
            findNearestNonObstaclePointAsync(s_start, nearestPoint -> {
                if (nearestPoint != null) {
                    s_start1 = nearestPoint;
                    drawDashedLine(bitmap, s_start, s_start1, Color.BLUE);
                    updateImageView(bitmap);
                } else {
                    Toast.makeText(context, "Cannot find an accessible point near current location", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            s_start1 = s_start;
        }
        s_goal = destination;
        s_goal1 = s_goal;
        String startText = (s_start != null) ? "Start point: " : "Start: N/A";
        String endText = (s_goal != null) ? "End point: " + destinationKey : "Destination: N/A";
        textViewStartEndCoordinates.setText(startText + "\n" + endText);

        StepDetection stepDetection = new StepDetection(this, pdrKalman);
        stepDetection.setStepDetectionListener(navigationOverlay);
        SensorManagerMain.setCallback(stepDetection);
        performNavigation();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            getCurrentFocus().clearFocus();  // 清除当前焦点
        }
    }

    private void loadObsJsonAsync() {
        if (isObstaclesLoaded) {
            // 如果已经加载过障碍物数据，则直接返回
            handler.post(() -> Log.d("NavigationActivity", "Obstacle data already loaded"));
            return;
        }

        executorService.submit(() -> {
            Set<List<Integer>> loadedObstacles = new HashSet<>();
            try (InputStream is = getAssets().open("obs_s.json");
                 InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 JsonReader reader = new JsonReader(isr)) {

                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginArray();
                    int x = reader.nextInt();
                    int y = reader.nextInt();
                    reader.endArray();

                    if (x >= 0 && x < bitmap.getHeight() && y >= 0 && y < bitmap.getWidth()) {
                        loadedObstacles.add(Arrays.asList(x, y));
                    }
                }
                reader.endArray();
                obstacles = loadedObstacles;
                isObstaclesLoaded = true;
            } catch (IOException e) {
                Log.e("NavigationActivity", "Error reading obs.json", e);
            }
        });
    }

    void performNavigation() {
        Log.d("NavigationActivity", "performNavigation() called");
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);  // Cancel any existing task
        }

        // Check if s_start1 and s_goal1 are available
        if (s_start1 == null || s_goal1 == null) {
            handler.post(() ->
                    Toast.makeText(context, "Cannot perform navigation, please ensure start and end points are accessible.", Toast.LENGTH_SHORT).show());
            return;
        }

        final int currentNavigationTaskId;
        synchronized (this) {
            currentNavigationTaskId = ++navigationTaskId;
        }

        if (fixedPath == null || fixedPath.isEmpty()
                || !s_start.equals(fixedPath.get(0)) || !s_goal.equals(fixedPath.get(fixedPath.size() - 1))) {
            clearCanvas();  // 清除画布，为新路径计算腾出空间
        }

        runOnUiThread(() -> {
            showLoadingIndicator();
            if (textToSpeech != null && textToSpeech.getEngines().size() > 0) {
                textToSpeech.speak("Processing, please wait.", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        currentTask = executorService.submit(() -> {
            Log.d("NavigationActivity", "New task submitted");
            // Load images from resources
            try {
                String heuristic_type = "euclidean";
//                String heuristic_type = "manhattan";
                if (fixedPath != null && !fixedPath.isEmpty()
                        && s_start1.get(0).equals(fixedPath.get(0).get(0)) && s_start1.get(1).equals(fixedPath.get(0).get(1))
                        && s_goal1.get(0).equals(fixedPath.get(fixedPath.size() - 1).get(0)) && s_goal1.get(1).equals(fixedPath.get(fixedPath.size() - 1).get(1))) {
                    path = new ArrayList<>(fixedPath); // 使用之前计算好的路径
                    Log.d("NavigationActivity", "Using previously calculated path");
                } else {
                    AStar aStar = new AStar(s_start1, s_goal1, heuristic_type, obstacles);
                    path = aStar.searching();
                    fixedPath = new ArrayList<>(path); // Save the calculated path
                    Log.d("NavigationActivity", "Path calculated using A*");
                }
                // Create a new matrix to convert from bitmap coordinates to view coordinates.
                // 使用 imageView_map 的 ImageMatrix 的逆矩阵进行转换。
                float[] routePoints = new float[path.size() * 2];
                for (int i = 0; i < path.size(); i++) {
                    List<Integer> point = path.get(i);
                    // 保持bitmap原始坐标（y,x）
                    routePoints[i * 2] = point.get(1); // x
                    routePoints[i * 2 + 1] = point.get(0); // y
                }
                Log.d("NavigationOverlay", "Path points: " + Arrays.toString(routePoints));
                navigationOverlay.setNavigationPath(routePoints);
                Log.d("PATH_DEBUG", "Setting path with " + routePoints.length + " elements");
                // Update the NavigationOverlay with the full navigation route without drawing on canvas.
                handler.post(() -> {
                    synchronized (this) {
                        if (bitmap == null) {
                            Log.e("NavigationActivity", "Bitmap is null, cannot proceed.");
                            handler.post(() -> Toast.makeText(context, "Failed to load the map image, navigation cannot proceed.", Toast.LENGTH_SHORT).show());
                            return;  // Exit to avoid null pointer exceptions
                        } else {
                            Log.d("NavigationActivity", "Bitmap dimensions in performNavigation: Width = " + bitmap.getWidth() + ", Height = " + bitmap.getHeight());
                        }
                        if (textToSpeech != null && textToSpeech.getEngines().size() > 0) {
                            textToSpeech.speak("Starting navigation", TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                        if (currentNavigationTaskId == navigationTaskId) {
                            clearCanvas();  // Clear previous drawings if any
                            // Update the NavigationOverlay with the full navigation route.
//                            navigationOverlay.setNavigationPath(routePoints);
                            // Optionally, update the ImageView if needed.
                            updateImageView(bitmap);
                            handler.post(this::hideLoadingIndicator);
                            Log.d("NavigationActivity", "NavigationOverlay updated with new path");
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("NavigationActivity", "IOException occurred: " + e.getClass().getSimpleName(), e);
                runOnUiThread(() -> {
                    // 出现异常时隐藏进度条
                    handler.post(this::hideLoadingIndicator);
                    Toast.makeText(context, "Failed to perform navigation", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoadingIndicator() {
        View loadingContainer = findViewById(R.id.loadingIndicatorContainer);
        ProgressBar loadingProgressBar = findViewById(R.id.loadingProgressBar);

        // 显示加载提示
        loadingContainer.setVisibility(View.VISIBLE);

        // 启动旋转动画
        Animation rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_animation);
        loadingProgressBar.startAnimation(rotateAnimation);
    }

    private void hideLoadingIndicator() {
        View loadingContainer = findViewById(R.id.loadingIndicatorContainer);
        ProgressBar loadingProgressBar = findViewById(R.id.loadingProgressBar);

        // 停止动画
        loadingProgressBar.clearAnimation();

        // 隐藏加载提示
        loadingContainer.setVisibility(View.GONE);
    }

    private void clearCanvas() {
        if (original_map_bitmap != null) {
            // 重置为初始地图图像
            bitmap = original_map_bitmap.copy(Bitmap.Config.ARGB_8888, true);

            // 在画布上重新绘制起点和终点
            if (s_start != null) {
                drawPoint(bitmap, s_start.get(1), s_start.get(0), Color.BLUE);
                if (s_start1 != null && !s_start.equals(s_start1)) {
                    drawDashedLine(bitmap, s_start, s_start1, Color.BLUE);
                }
            }
            if (s_goal != null) {
                drawPoint(bitmap, s_goal.get(1), s_goal.get(0), Color.GREEN);
                if (s_goal1 != null && !s_goal.equals(s_goal1)) {
                    drawDashedLine(bitmap, s_goal1, s_goal, Color.GREEN);
                }
            }


            updateImageView(bitmap);  // 刷新 ImageView 显示
        }
    }

    private void drawDashedLine(Bitmap bitmap, List<Integer> start, List<Integer> end,
                                int color) {
        if (start == null || end == null) return;

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0)); // Set dashed effect

        Path path = new Path();
        path.moveTo(start.get(1), start.get(0)); // Move to start point (x, y)
        path.lineTo(end.get(1), end.get(0));     // Line to end point (x, y)

        canvas.drawPath(path, paint);
    }

    private void clearDashedLine(Bitmap bitmap, List<Integer> start, List<Integer> end) {
        if (start == null || end == null || original_map_bitmap == null) return;

        // Define the area to restore
        Rect rect = new Rect(
                Math.min(start.get(1), end.get(1)) - 5,
                Math.min(start.get(0), end.get(0)) - 5,
                Math.max(start.get(1), end.get(1)) + 5,
                Math.max(start.get(0), end.get(0)) + 5
        );

        // Ensure the rectangle is within bitmap bounds
        rect.left = Math.max(0, rect.left);
        rect.top = Math.max(0, rect.top);
        rect.right = Math.min(original_map_bitmap.getWidth(), rect.right);
        rect.bottom = Math.min(original_map_bitmap.getHeight(), rect.bottom);

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // Copy pixels from original map bitmap to current bitmap
        canvas.drawBitmap(original_map_bitmap, rect, rect, paint);
    }

    private void findNearestNonObstaclePointAsync
            (List<Integer> point, Consumer<List<Integer>> callback) {
        executorService.submit(() -> {
            List<Integer> result = findNearestNonObstaclePoint(point);
            handler.post(() -> callback.accept(result));
        });
    }

    private List<Integer> findNearestNonObstaclePoint(List<Integer> point) {
        Queue<List<Integer>> queue = new LinkedList<>();
        Set<List<Integer>> visited = new HashSet<>();
        queue.offer(point);
        visited.add(point);

        int[][] directions = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        };

        while (!queue.isEmpty()) {
            List<Integer> current = queue.poll();
            if (!obstacles.contains(current) && inBounds(current)) {
                return current; // 找到最近的非障碍物点
            }
            for (int[] dir : directions) {
                int newY = current.get(0) + dir[0];
                int newX = current.get(1) + dir[1];
                List<Integer> neighbor = Arrays.asList(newY, newX);
                if (!visited.contains(neighbor) && inBounds(neighbor)) {
                    queue.offer(neighbor);
                    visited.add(neighbor);
                }
            }
        }
        return null; // No accessible point found
    }

    private boolean inBounds(List<Integer> point) {
        int y = point.get(0); // 假设坐标顺序为 (y, x)
        int x = point.get(1);

        if (bitmap == null) {
            // 如果 bitmap 尚未初始化，返回 false 或者进行适当的处理
            return false;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 检查点是否在地图范围内
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void drawPoint(Bitmap bitmap, int x, int y, int color) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        int radius = 10; // 圆点的半径，可以根据需要调整
        canvas.drawCircle(x, y, radius, paint);
    }

    private void clearOldPoint(Bitmap bitmap, List<Integer> point) {
        if (point != null && original_map_bitmap != null) {
            int x = point.get(1);
            int y = point.get(0);
            int radius = 10; // 与绘制点时使用的半径相同

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();

            // 定义需要恢复的区域
            Rect srcRect = new Rect(x - radius, y - radius, x + radius, y + radius);
            Rect destRect = new Rect(x - radius, y - radius, x + radius, y + radius);

            // 确保矩形在位图范围内
            srcRect.left = Math.max(0, srcRect.left);
            srcRect.top = Math.max(0, srcRect.top);
            srcRect.right = Math.min(original_map_bitmap.getWidth(), srcRect.right);
            srcRect.bottom = Math.min(original_map_bitmap.getHeight(), srcRect.bottom);

            destRect.left = Math.max(0, destRect.left);
            destRect.top = Math.max(0, destRect.top);
            destRect.right = Math.min(bitmap.getWidth(), destRect.right);
            destRect.bottom = Math.min(bitmap.getHeight(), destRect.bottom);

            // 从原始地图位图中复制相应区域的像素到当前位图
            canvas.drawBitmap(original_map_bitmap, srcRect, destRect, paint);
        }
    }

    private void drawPathOnCanvas(Bitmap bitmap, List<List<Integer>> path) {
        if (path == null || path.isEmpty()) return;

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);  // 设置路径的颜色
        paint.setStrokeWidth(5);  // 设置路径的线条宽度
        paint.setStyle(Paint.Style.STROKE);  // 设置绘制风格

        // Draw continuous lines between path points
        for (int i = 1; i < path.size(); i++) {
            List<Integer> start = path.get(i - 1);
            List<Integer> end = path.get(i);

            // 从 (x1, y1) 到 (x2, y2) 绘制线条
            canvas.drawLine(
                    start.get(1), start.get(0),  // (x1, y1)
                    end.get(1), end.get(0),      // (x2, y2)
                    paint
            );
        }
    }

    public void stopNavigation() {
        isNavigating = false;
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);  // 尝试取消任务
        }

        navigationTaskId++;
        if (handler != null) {
            handler.removeCallbacks(null);
        }
//        clearCanvas();
    }

    private void updateImageView(Bitmap bitmap) {
        if (bitmap == null || imageView_map == null) return;
        runOnUiThread(() -> {
            synchronized (this) {

                imageView_map.setImageBitmap(bitmap);
                imageView_map.setScaleType(ImageView.ScaleType.MATRIX);
                Matrix currentMatrix = new Matrix(imageView_map.getImageMatrix());
                imageView_map.setImageMatrix(currentMatrix);
                // Update overlay's transformation matrix.
                navigationOverlay.setTransformMatrix(new Matrix(currentMatrix));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocating();
        stopNavigation();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        synchronized (this) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }

            if (original_map_bitmap != null && !original_map_bitmap.isRecycled()) {
                original_map_bitmap.recycle();
                original_map_bitmap = null;
            }
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onAccelerometerData(float x, float y, float z, long timestamp) {
        if (StepDetection != null) {
            StepDetection.onAccelerometerData(x, y, z, timestamp);
        }
    }

    @Override
    public void onRotationVectorData(float[] rotationVector) {

    }

    @Override
    public void onOrientationData(float azimuthInDegrees, String direction) {
        if (StepDetection != null) {
            StepDetection.onOrientationData(azimuthInDegrees, direction);
        }
    }

    // Inner class AStar
    public class AStar {

        private final List<Integer> s_start;
        private final List<Integer> s_goal;
        private final String heuristic_type;
        private final Env env; // Environment class
        private final int[][] u_set; // Feasible input set
        private final Set<List<Integer>> obs; // Obstacles
        private final PriorityQueue<Node> OPEN; // Priority queue / OPEN set
        private final Map<List<Integer>, List<Integer>> PARENT; // Recorded parent
        private final Map<List<Integer>, Double> g; // Cost to come
        private final List<List<Integer>> neighborsCache = new ArrayList<>(8); //cache for neighbor

        public AStar(List<Integer> s_start, List<Integer> s_goal, String heuristic_type, Set<List<Integer>> obs) {
            this.s_start = s_start;
            this.s_goal = s_goal;
            this.heuristic_type = heuristic_type;

            this.env = new Env(obs); // Environment class; // Environment class
            this.u_set = env.getMotions();
            this.obs = obs;

            this.OPEN = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f_value));
            this.PARENT = new HashMap<>();
            this.g = new HashMap<>();
        }

        public List<List<Integer>> searching() {
            this.PARENT.put(this.s_start, this.s_start);
            this.g.put(this.s_start, 0.0);
            this.g.put(this.s_goal, Double.POSITIVE_INFINITY);
            this.OPEN.add(new Node(f_value(this.s_start), this.s_start));

            while (!OPEN.isEmpty()) {
                Node currentNode = OPEN.poll();
                List<Integer> s = currentNode.state;

                if (s.equals(this.s_goal)) { // Stop condition
                    break;
                }

                for (List<Integer> s_n : get_neighbor(s)) {
                    double new_cost = g.get(s) + cost(s, s_n);

                    g.putIfAbsent(s_n, Double.POSITIVE_INFINITY);

                    if (new_cost < g.get(s_n)) { // Conditions for updating cost
                        g.put(s_n, new_cost);
                        this.PARENT.put(s_n, s);
                        this.OPEN.add(new Node(f_value(s_n), s_n));
                    }
                }
            }

            return extract_path(PARENT);
        }

        private List<List<Integer>> get_neighbor(List<Integer> s) {
            neighborsCache.clear(); // 先清空缓存
            for (int[] u : u_set) {
                List<Integer> neighbor = Arrays.asList(s.get(0) + u[0], s.get(1) + u[1]);
                if (!obs.contains(neighbor) && env.inBounds(neighbor)) {
                    neighborsCache.add(neighbor);
                }
            }
            return neighborsCache;
        }

        private double cost(List<Integer> s_start, List<Integer> s_goal) {
            if (is_collision(s_start, s_goal)) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.hypot(s_goal.get(0) - s_start.get(0), s_goal.get(1) - s_start.get(1));
        }

        private boolean is_collision(List<Integer> s_start, List<Integer> s_end) {
            if (obs.contains(s_start) || obs.contains(s_end)) {
                return true;
            }

            if (!s_start.get(0).equals(s_end.get(0)) && !s_start.get(1).equals(s_end.get(1))) {
                List<Integer> s1, s2;
                if (s_end.get(0) - s_start.get(0) == s_start.get(1) - s_end.get(1)) {
                    s1 = Arrays.asList(Math.min(s_start.get(0), s_end.get(0)), Math.min(s_start.get(1), s_end.get(1)));
                    s2 = Arrays.asList(Math.max(s_start.get(0), s_end.get(0)), Math.max(s_start.get(1), s_end.get(1)));
                } else {
                    s1 = Arrays.asList(Math.min(s_start.get(0), s_end.get(0)), Math.max(s_start.get(1), s_end.get(1)));
                    s2 = Arrays.asList(Math.max(s_start.get(0), s_end.get(0)), Math.max(s_start.get(1), s_end.get(1)));
                }

                return obs.contains(s1) || obs.contains(s2);
            }

            return false;
        }

        private List<List<Integer>> extract_path(Map<List<Integer>, List<Integer>> PARENT) {
            List<List<Integer>> path = new ArrayList<>();
            path.add(this.s_goal);
            List<Integer> s = this.s_goal;

            while (!s.equals(this.s_start)) {
                s = PARENT.get(s);
                path.add(s);
            }

            Collections.reverse(path);
            return path;
        }

        private double f_value(List<Integer> s) {
            return g.get(s) + heuristic(s);
        }

        private double heuristic(List<Integer> s) {
            if (heuristic_type.equals("manhattan")) {
                return Math.abs(s_goal.get(0) - s.get(0)) + Math.abs(s_goal.get(1) - s.get(1));
            } else {
                return Math.hypot(s_goal.get(0) - s.get(0), s_goal.get(1) - s.get(1));
            }
        }

        private class Node {
            double f_value;
            List<Integer> state;

            Node(double f_value, List<Integer> state) {
                this.f_value = f_value;
                this.state = state;
            }
        }

        // Inner class Env
        public class Env {
            private final int[][] motions = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}, {-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
            private final int x_range;  // Size of background
            private final int y_range;
            private final Set<List<Integer>> obs;

            public Env(Set<List<Integer>> obs) {
                this.x_range = 1168;  // Size of background
                this.y_range = 928;
                this.obs = obs;
            }

            public int[][] getMotions() {
                return motions;
            }

            public boolean inBounds(List<Integer> s) {
                int x = s.get(0), y = s.get(1);
                return x >= 0 && x < x_range && y >= 0 && y < y_range;
            }
        }
    }
}