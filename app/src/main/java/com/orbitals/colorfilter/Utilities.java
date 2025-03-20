package com.orbitals.colorfilter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.TextureView;

import org.opencv.core.Mat;

public class Utilities {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.Utilities";

    public static Mat centerOfImage(Context context, TextureView textureView, FilterProcessor filter, Mat input, Matrix imageMatrix) {
        float density = context.getResources().getDisplayMetrics().density;
        int sampleSizePx = (int) (filter.getSampleSize() * density);

        float viewCenterX = textureView.getWidth() / 2f;
        float viewCenterY = textureView.getHeight() / 2f;

        Matrix invertedMatrix = new Matrix();
        imageMatrix.invert(invertedMatrix);
        float[] ul = new float[]{viewCenterX - sampleSizePx / 2f, viewCenterY - sampleSizePx / 2f};
        float[] lr = new float[]{viewCenterX + sampleSizePx / 2f, viewCenterY + sampleSizePx / 2f};
        invertedMatrix.mapPoints(ul);
        invertedMatrix.mapPoints(lr);
        int x0 = Math.max(0, Math.min(input.cols(), (int) Math.min(ul[0], lr[0])));
        int y0 = Math.max(0, Math.min(input.rows(), (int) Math.min(ul[1], lr[1])));
        int x1 = Math.max(0, Math.min(input.cols(), (int) Math.max(ul[0], lr[0])));
        int y1 = Math.max(0, Math.min(input.rows(), (int) Math.max(ul[1], lr[1])));

        Log.d(TAG, "centerOfImage " + input.cols() + " " + input.rows() + " " + density + " " + sampleSizePx + " " + viewCenterX + " " + viewCenterY + " " + x0 + " " + y0 + " " + x1 + " " + y1);
        org.opencv.core.Rect roi = new org.opencv.core.Rect(x0, y0, x1 - x0, y1 - y0);
        return input.submat(roi);
    }

    public static void drawSamplingCircle(Context context, FilterProcessor filter, Canvas canvas) {
        if (!filter.getSampleMode()) {
            return;
        }

        // Convert dp to pixels
        float density = context.getResources().getDisplayMetrics().density;
        float strokeWidth = 2 * density; // 2dp stroke width
        float circleDiameter = filter.getSampleSize() * density; // dp diameter for circle

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        Paint whitePaint = new Paint();
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.STROKE);
        whitePaint.setStrokeWidth(strokeWidth);
        whitePaint.setAntiAlias(true);
        Paint blackPaint = new Paint();
        blackPaint.setColor(Color.BLACK);
        blackPaint.setStyle(Paint.Style.STROKE);
        blackPaint.setStrokeWidth(strokeWidth);
        blackPaint.setAntiAlias(true);
        canvas.drawCircle(width * 0.5f, height * 0.5f, (circleDiameter - strokeWidth) / 2, whitePaint);
        canvas.drawCircle(width * 0.5f, height * 0.5f, (circleDiameter + strokeWidth) / 2, blackPaint);
    }

}
