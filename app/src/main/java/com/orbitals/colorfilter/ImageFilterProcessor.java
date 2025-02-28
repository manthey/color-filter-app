package com.orbitals.colorfilter;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;
import java.util.ArrayList;

public class ImageFilterProcessor {

    public enum FilterMode {
        NONE,
        INCLUDE,
        EXCLUDE,
        BINARY,
        SATURATION
    }

    private int hue = 0;
    private int hueWidth = 14;
    private int satThreshold = 100;
    private int lumThreshold = 100;
    private FilterMode filterMode = FilterMode.NONE;

    public void setFilterSettings(int hue, int hueWidth, int satThreshold, int lumThreshold, FilterMode filterMode) {
        this.hue = hue;
        this.hueWidth = hueWidth;
        this.satThreshold = satThreshold;
        this.lumThreshold = lumThreshold;
        this.filterMode = filterMode;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }
    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }
    public int getHue() {
        return hue;
    }
    public void setHue(int hue) {
        this.hue = hue;
    }
    public int getHueWidth() {
        return hueWidth;
    }
    public void setHueWidth(int hueWidth){
        this.hueWidth = hueWidth;
    }
    public int getSatThreshold() {
        return satThreshold;
    }
    public void setSatThreshold(int satThreshold) {
        this.satThreshold = satThreshold;
    }
    public int getLumThreshold() {
        return lumThreshold;
    }
    public void setLumThreshold(int lumThreshold) {
        this.lumThreshold = lumThreshold;
    }

    public Mat process(Mat input) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGB2HSV);

        // Define lower and upper bounds for the hue range
        int lowerHue = (int) (hue / 2.0 - hueWidth / 2.0);
        int upperHue = (int) (hue / 2.0 + hueWidth / 2.0);
        Scalar lowerBound = new Scalar(Math.max(0, lowerHue), satThreshold, lumThreshold);
        Scalar upperBound = new Scalar(Math.min(180, upperHue), 255, 255);

        Mat mask = new Mat();
        Core.inRange(hsv, lowerBound, upperBound, mask);

        // Handle hue wrapping
        if (lowerHue < 0 || upperHue > 180) {
            Mat mask2 = new Mat();
            if (lowerHue < 0) {
                Core.inRange(hsv, new Scalar(lowerHue + 180, satThreshold, lumThreshold), new Scalar(180, 255, 255), mask2);
            } else {
                Core.inRange(hsv, new Scalar(0, satThreshold, lumThreshold), new Scalar(upperHue - 180, 255, 255), mask2);
            }
            Core.bitwise_or(mask, mask2, mask);
            mask2.release();
        }

        Mat output = Mat.zeros(input.size(), input.type());
        switch (filterMode) {
            case NONE:
                input.copyTo(output);
                break;
            case INCLUDE:
                input.copyTo(output, mask);
                break;
            case EXCLUDE:
                Core.bitwise_not(mask, mask);
                output.setTo(new Scalar(255, 255, 255));
                input.copyTo(output, mask);
                break;
            case BINARY:
                Mat ones = Mat.zeros(input.size(), input.type());
                ones.setTo(new Scalar(255, 255, 255));
                ones.copyTo(output, mask);
                ones.release();
                break;
            case SATURATION:
                List<Mat> channels = new ArrayList<>();
                Core.split(hsv, channels);
                channels.set(0, channels.get(1).clone());
                channels.set(2, channels.get(1).clone());
                Mat sss = new Mat();
                Core.merge(channels, sss);
                sss.copyTo(output, mask);
                sss.release();
                break;
        }
        mask.release();
        hsv.release();
        return output;
    }
}
