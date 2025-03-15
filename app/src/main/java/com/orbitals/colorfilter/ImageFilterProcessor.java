package com.orbitals.colorfilter;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class ImageFilterProcessor {

    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.ImageFilterProcessor";

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
    private int term = 0;
    private boolean useLumSatBCT = true;
    private boolean sampleMode = false;
    private TermMap termMap;
    private FilterMode filterMode = FilterMode.NONE;

    private int sampleSize = 40;  // in dp

    /**
     * Set several settings at once.
     *
     * @param hue          Hue value from 0 to 360.
     * @param hueWidth     Hue width from 0 to 180.  This is the width on either side of the hue value.
     * @param satThreshold Saturation threshold from 0 to 255.
     * @param lumThreshold Luminance threshold from 0 to 255.
     * @param filterMode   One of the FilterMode enum values.
     */
    public void setFilterSettings(int hue, int hueWidth, int satThreshold, int lumThreshold, FilterMode filterMode) {
        this.hue = hue;
        this.hueWidth = hueWidth;
        this.satThreshold = satThreshold;
        this.lumThreshold = lumThreshold;
        this.filterMode = filterMode;
    }

    /**
     * Set several settings at once.
     *
     * @param hue          Hue value from 0 to 360.
     * @param hueWidth     Hue width from 0 to 180.  This is the width on either side of the hue value.
     * @param satThreshold Saturation threshold from 0 to 255.
     * @param lumThreshold Luminance threshold from 0 to 255.
     * @param term         Term number from the term map.  0-based.
     * @param filterMode   One of the FilterMode enum values.
     * @param termMap      Either null for no TermMap or a TermMap.
     */
    public void setFilterSettings(int hue, int hueWidth, int satThreshold, int lumThreshold, int term, FilterMode filterMode, TermMap termMap) {
        setFilterSettings(hue, hueWidth, satThreshold, lumThreshold, filterMode);
        this.term = term;
        this.termMap = termMap;
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

    public void setHueWidth(int hueWidth) {
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

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public TermMap getTermMap() {
        return termMap;
    }

    /**
     * Set the current TermMap.  If the specified TermMap is not null, the term value will be
     * constrained to the range [0, number of terms in new TermMap).
     *
     * @param termMap Either null or a TermMap.
     */
    public void setTermMap(TermMap termMap) {
        this.termMap = termMap;
        if (termMap != null && term >= termMap.getTerms().size()) {
            term = termMap.getTerms().size() - 1;
        }
    }

    /**
     * Get the current term as a string.
     *
     * @return The current term.  An empty string if none.
     */
    public String getCurrentTerm() {
        if (termMap == null || term >= termMap.getTerms().size()) {
            return "";
        }
        return termMap.getTerms().get(term);
    }

    public void setUseLumSatBCT(boolean useLumSatBCT) {
        this.useLumSatBCT = useLumSatBCT;
    }

    public boolean getUseLumSatBCT() {
        return useLumSatBCT;
    }

    public void setSampleMode(boolean sampleMode) {
        this.sampleMode = sampleMode;
    }

    public boolean getSampleMode() {
        return sampleMode;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    /** @noinspection unused*/
    public void SetSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    /**
     * Process an input matrix image, filtering it based on the current filter mode and other
     * parameters.
     *
     * @param input An image matrix in RGBA format.  Modified to RGB.
     * @return An image matrix in RGB format with the image applied.
     */
    public Mat process(Mat input) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGB2HSV);

        // Define lower and upper bounds for the hue range
        int lowerHue = (int) (hue / 2.0 - hueWidth / 2.0);
        int upperHue = (int) (hue / 2.0 + hueWidth / 2.0);
        if (termMap != null) {
            lowerHue = 0;
            upperHue = 360 / 2;
        }
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
        if (termMap != null) {
            Mat termMask = termMap.createMask(input, term);
            if (useLumSatBCT) {
                Core.bitwise_and(mask, termMask, mask);
            } else {
                termMask.copyTo(mask);
            }
            termMask.release();
        }
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

    public boolean sampleRegion(Mat input) {
        int width = input.cols();
        int height = input.rows();
        if (width < 1 || height < 1) {
            return false;
        }
        int rad = Math.max(width, height) / 2;
        int rad2 = rad * rad;
        int cx = width / 2;
        int cy = height / 2;
        if (termMap != null) {
            Imgproc.cvtColor(input, input, Imgproc.COLOR_RGBA2RGB);
            Mat terms = termMap.createMap(input);
            Map<Byte, Integer> termCounts = new HashMap<>();
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    if ((j - cy) * (j - cy) + (i - cx) * (i - cx) > rad2) {
                        continue;
                    }
                    byte val = (byte)terms.get(j, i)[0];
                     //noinspection DataFlowIssue
                    termCounts.put(val, termCounts.getOrDefault(val, 0) + 1);
                }
            }
            int modalTerm = -1;
            int maxCount = 0;
            for (Map.Entry<Byte, Integer> entry : termCounts.entrySet()) {
                 if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    modalTerm = entry.getKey();
                }
            }
            Log.d(TAG, "Modal term " + modalTerm);
            if (modalTerm != term) {
                term = modalTerm;
                return true;
            }
        } else {
            Imgproc.cvtColor(input, input, Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2HSV);
            // now find the most common hue angle
            double sumCos = 0;
            double sumSin = 0;
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    if ((j - cy) * (j - cy) + (i - cx) * (i - cx) > rad2) {
                        continue;
                    }
                    double val = input.get(j, i)[0] * Math.PI / 90;
                    sumCos += Math.cos(val);
                    sumSin += Math.sin(val);
                }
            }
            int commonHue = (int) (Math.atan2(sumSin, sumCos) * 90 / Math.PI) * 2;
            if (commonHue < 0) {
                commonHue += 360;
            }
            Log.d(TAG, "Common hue " + commonHue);
            if (commonHue != hue) {
                hue = commonHue;
                return true;
            }
        }
        return false;
    }
}
