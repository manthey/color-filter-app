package com.orbitals.colorfilter;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class TermMap {
    private final String name;
    private final List<String> terms;
    private final byte[] map;
    private int blur = 9;

    public TermMap(String name, List<String> terms, Resources resources, int termMapResourceId) {
        this.name = name;
        this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
        map = new byte[4096 * 4096];

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, termMapResourceId, options);

        Mat tmpMat = new Mat();
        Utils.bitmapToMat(bitmap, tmpMat);
        Mat mat = new Mat();
        Imgproc.cvtColor(tmpMat, mat, Imgproc.COLOR_RGBA2GRAY);
        bitmap.recycle();
        int imageSize = 256;
        int bytesPerImage = imageSize * imageSize;
        for (int i = 0; i < 256; i++) {
            int row = i / 16;
            int col = i % 16;
            int x = col * imageSize;
            int y = row * imageSize;

            Rect rect = new Rect(x, y, imageSize, imageSize);
            Mat subMat = new Mat(mat, rect);
            byte[] subImageBytes = new byte[subMat.rows() * subMat.cols()];
            subMat.get(0, 0, subImageBytes);
            System.arraycopy(subImageBytes, 0, map, i * bytesPerImage, bytesPerImage);
        }
    }

    public String getName() {
        return name;
    }

    public List<String> getTerms() {
        return terms;
    }

    /** @noinspection unused*/
    public byte[] getMap() {
        return map != null ? Arrays.copyOf(map, map.length) : null;
    }

    /** @noinspection unused*/
    public int getBlur() {
        return blur;
    }
    /** @noinspection unused*/
    public void setBlur(int blur) {
        this.blur = blur;
    }

    public Mat createMask(Mat image, int targetValue) {
        byte[] rgbData = new byte[image.channels() * image.cols() * image.rows()];
        if (blur != 0) {
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(image, blurred, new Size(blur, blur), 0);
            // Imgproc.blur(image, blurred, new Size(blur, blur));
            blurred.get(0, 0, rgbData);
        } else {
            image.get(0, 0, rgbData);
        }
        byte[] maskData = new byte[image.cols() * image.rows()];

        int center = ((image.rows() / 2) * image.cols() + (image.cols() / 2)) * 3;
        for (int i = 0, j = 0; i < rgbData.length; i += 3, j++) {
            // Get RGB values (unsigned)
            int r = rgbData[i] & 0xFF;
            int g = rgbData[i + 1] & 0xFF;
            int b = rgbData[i + 2] & 0xFF;
            int index = (r << 16) | (g << 8) | b;
            if (i == center) {
                Log.e("TermMap", "Center " + " " + r + "," + g + "," + b + " " + map[index]);
            }
            maskData[j] = (byte) ((map[index] & 0xFF) == targetValue ? 255 : 0);
        }

        Mat mask = new Mat(image.rows(), image.cols(), CvType.CV_8UC1);
        mask.put(0, 0, maskData);
        return mask;
    }
}
