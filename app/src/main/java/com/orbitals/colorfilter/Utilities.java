package com.orbitals.colorfilter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.util.Log;
import android.view.Display;
import android.view.TextureView;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;

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

    /*
     * Taken from https://github.com/opencv/opencv/blob/4.x/modules/java/generator/android-21/java/org/opencv/android/JavaCamera2View.java
     */
    public static Mat rgba(Image mImage) {
        Mat mRgba = new Mat();
        Image.Plane[] planes = mImage.getPlanes();
        if (planes == null || planes.length < 3) {
            throw new IllegalStateException("Image does not have the expected 3 planes.");
        }
        int w = mImage.getWidth();
        int h = mImage.getHeight();
        int chromaPixelStride = planes[1].getPixelStride();
        if (chromaPixelStride != 1 && chromaPixelStride != 2) {
            throw new UnsupportedOperationException("Unexpected chroma pixel stride: " + chromaPixelStride);
        }
        
        //noinspection IfStatementWithIdenticalBranches
        if (chromaPixelStride == 2) { // Chroma channels are interleaved
            assert (planes[0].getPixelStride() == 1);
            assert (planes[2].getPixelStride() == 2);
            ByteBuffer y_plane = planes[0].getBuffer();
            int y_plane_step = planes[0].getRowStride();
            ByteBuffer uv_plane1 = planes[1].getBuffer();
            int uv_plane1_step = planes[1].getRowStride();
            ByteBuffer uv_plane2 = planes[2].getBuffer();
            int uv_plane2_step = planes[2].getRowStride();
            Mat y_mat = new Mat(h, w, CvType.CV_8UC1, y_plane, y_plane_step);
            Mat uv_mat1 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane1, uv_plane1_step);
            Mat uv_mat2 = new Mat(h / 2, w / 2, CvType.CV_8UC2, uv_plane2, uv_plane2_step);
            //noinspection SpellCheckingInspection
            long addr_diff = uv_mat2.dataAddr() - uv_mat1.dataAddr();
            if (addr_diff > 0) {
                if ((addr_diff != 1)) {
                    throw new AssertionError();
                }
                Imgproc.cvtColorTwoPlane(y_mat, uv_mat1, mRgba, Imgproc.COLOR_YUV2RGBA_NV12);
            } else {
                assert (addr_diff == -1);
                Imgproc.cvtColorTwoPlane(y_mat, uv_mat2, mRgba, Imgproc.COLOR_YUV2RGBA_NV21);
            }
            return mRgba;
        } else //noinspection ExtractMethodRecommender
        { // Chroma channels are not interleaved
            byte[] yuv_bytes = new byte[w * (h + h / 2)];
            ByteBuffer y_plane = planes[0].getBuffer();
            ByteBuffer u_plane = planes[1].getBuffer();
            ByteBuffer v_plane = planes[2].getBuffer();

            int yuv_bytes_offset = 0;

            int y_plane_step = planes[0].getRowStride();
            if (y_plane_step == w) {
                y_plane.get(yuv_bytes, 0, w * h);
                yuv_bytes_offset = w * h;
            } else {
                int padding = y_plane_step - w;
                for (int i = 0; i < h; i++) {
                    y_plane.get(yuv_bytes, yuv_bytes_offset, w);
                    yuv_bytes_offset += w;
                    if (i < h - 1) {
                        y_plane.position(y_plane.position() + padding);
                    }
                }
                assert (yuv_bytes_offset == w * h);
            }

            int chromaRowStride = planes[1].getRowStride();
            int chromaRowPadding = chromaRowStride - w / 2;

            if (chromaRowPadding == 0) {
                // When the row stride of the chroma channels equals their width, we can copy
                // the entire channels in one go
                u_plane.get(yuv_bytes, yuv_bytes_offset, w * h / 4);
                yuv_bytes_offset += w * h / 4;
                v_plane.get(yuv_bytes, yuv_bytes_offset, w * h / 4);
            } else {
                // When not equal, we need to copy the channels row by row
                for (int i = 0; i < h / 2; i++) {
                    u_plane.get(yuv_bytes, yuv_bytes_offset, w / 2);
                    yuv_bytes_offset += w / 2;
                    if (i < h / 2 - 1) {
                        u_plane.position(u_plane.position() + chromaRowPadding);
                    }
                }
                for (int i = 0; i < h / 2; i++) {
                    v_plane.get(yuv_bytes, yuv_bytes_offset, w / 2);
                    yuv_bytes_offset += w / 2;
                    if (i < h / 2 - 1) {
                        v_plane.position(v_plane.position() + chromaRowPadding);
                    }
                }
            }

            Mat yuv_mat = new Mat(h + h / 2, w, CvType.CV_8UC1);
            yuv_mat.put(0, 0, yuv_bytes);
            Imgproc.cvtColor(yuv_mat, mRgba, Imgproc.COLOR_YUV2RGBA_I420, 4);
            return mRgba;
        }
    }

    public static ColorSpace checkColorSpace(Context context) {
        Display display = context.getDisplay();
        ColorSpace displayColorSpace = display.getPreferredWideGamutColorSpace();
        if (displayColorSpace == null) {
            displayColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        }
        Log.d(TAG, "Display color space: " + displayColorSpace.getName());
        return displayColorSpace;
    }

}
