package com.example.locate;

import android.content.Context;
import android.graphics.Color;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.androidplot.ui.HorizontalPositioning;
import com.androidplot.ui.VerticalPositioning;
import com.androidplot.util.PixelUtils;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.FastLineAndPointRenderer;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.PanZoom;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

import org.json.JSONObject;

import java.io.InputStream;

/**
 * Created on 2024/6/5 16:47
 * Author: ZST
 */
public class PlotMap {
    private double width;
    private double height;
    private Context context;
    private XYPlot tracePlot;
    private LineAndPointFormatter pathPoint = null;  // 用于格式化绘制的红色线条和点的格式化器
    private SimpleXYSeries gtXYSeries = null;  // 数据序列，存储ground truth
    private LineAndPointFormatter currentPoint = null;  // 用于格式化绘制的蓝色线条和点的格式化器
    private SimpleXYSeries traABSeries = null;  // 数据序列，存储轨迹
    private Redrawer redrawer;

    public PlotMap(Context context, XYPlot plot) {
        this.context = context;
        this.tracePlot = plot;
        configurePlot();
//        PanZoom.attach(tracePlot);  // 放大or移动图（背景地图无法跟随移动）
    }

    private void configurePlot() {
        loadFloorInfo();
        double aspectRatio = width / height;
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) tracePlot.getLayoutParams();
        params.dimensionRatio = "H," + aspectRatio + ":1";
        tracePlot.setLayoutParams(params);

        tracePlot.setBackgroundPaint(null);
        tracePlot.getGraph().setBackgroundPaint(null);
        tracePlot.getGraph().setGridBackgroundPaint(null);

        // 设置图例的位置和大小
        tracePlot.getLegend().setVisible(true);
        tracePlot.getLegend().setWidth(0.2f); // 设为总宽度的20%
        tracePlot.getLegend().setHeight(0.1f); // 设为总高度的10%
        tracePlot.getLegend().position(
                50, HorizontalPositioning.ABSOLUTE_FROM_LEFT,
                50, VerticalPositioning.ABSOLUTE_FROM_BOTTOM);  // 距离图的下边界xx像素
        tracePlot.getLegend().getTextPaint().setTextSize(PixelUtils.dpToPix(8)); // 设置图例文字
        tracePlot.getLegend().getTextPaint().setColor(Color.BLACK);

        gtXYSeries = new SimpleXYSeries("path");
        pathPoint = new FastLineAndPointRenderer.Formatter(null, null, null);
        Paint vertexPaint = new Paint();
        vertexPaint.setStrokeWidth(3);
        vertexPaint.setColor(Color.argb(150, 255, 0, 0));
        pathPoint.setVertexPaint(vertexPaint);
        tracePlot.addSeries(gtXYSeries, pathPoint);

//        gtXYSeries.addLast(0, 0);  // 添加初始点到gtXYSeries
//        gtXYSeries.addLast(width, height);

        traABSeries = new SimpleXYSeries("current");
        currentPoint = new FastLineAndPointRenderer.Formatter(null, null, null);
        Paint vertexPaint2 = new Paint();
        vertexPaint2.setStrokeWidth(8);
        vertexPaint2.setColor(Color.BLUE);
        currentPoint.setVertexPaint(vertexPaint2);
        tracePlot.addSeries(traABSeries, currentPoint);

        tracePlot.setRangeBoundaries(0, height, BoundaryMode.FIXED);
        tracePlot.setDomainBoundaries(0, width, BoundaryMode.FIXED);

        redrawer = new Redrawer(tracePlot, 200, true);
    }

    private void loadFloorInfo() {
        try {
            InputStream is = context.getAssets().open("floor_info.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            JSONObject obj = new JSONObject(json);
            JSONObject mapInfo = obj.getJSONObject("map_info");
            width = mapInfo.getDouble("width");
            height = mapInfo.getDouble("height");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addPointToMap(double x, double y) {
        traABSeries.clear();
        traABSeries.addLast(x, y);
    }

    public void addPointToMap2(double x, double y) {
        gtXYSeries.addLast(x, y);
    }

    public void cleanup() {
        tracePlot.clear();
        redrawer.finish();
    }
}
