package com.orbitals.colorfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class ImageFilterProcessorTest {

    private ImageFilterProcessor processor;
    private Mat testImage;

    @Before
    public void setup() {
        if (!OpenCVLoader.initLocal()) {
            throw new RuntimeException("Failed to initialize OpenCV");
        }

        processor = new ImageFilterProcessor();

        Bitmap testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(testBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawRect(0, 0, 100, 100, paint);
        testImage = new Mat();
        Utils.bitmapToMat(testBitmap, testImage);
    }

    @Test
    public void testIncludeFilter() {
        ImageFilterProcessor processor = new ImageFilterProcessor();
        processor.setFilterSettings(0, 14, 100, 100, ImageFilterProcessor.FilterMode.INCLUDE);

        // Create a test Mat
        Mat input = Mat.ones(100, 100, CvType.CV_8UC3);
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2HSV);
        Imgproc.rectangle(input, new Point(10, 10), new Point(20, 20), new Scalar(10, 200, 200), -1);

        Mat output = processor.process(input);

        // Validate the output
        assertNotNull(output);
        assertEquals(100, output.rows());
        assertEquals(100, output.cols());
        // Add more specific test cases
    }

    @Test
    public void testGettersAndSetters() {
        ImageFilterProcessor processor = new ImageFilterProcessor();
        processor.setHue(100);
        assertEquals(100, processor.getHue());

        processor.setHueWidth(20);
        assertEquals(20, processor.getHueWidth());

        processor.setSatThreshold(150);
        assertEquals(150, processor.getSatThreshold());

        processor.setLumThreshold(200);
        assertEquals(200, processor.getLumThreshold());
    }

    @Test
    public void testAllFilterModes() {
        processor.setFilterSettings(0, 14, 100, 100, ImageFilterProcessor.FilterMode.NONE);
        Mat result = processor.process(testImage.clone());
        assertNotNull(result);
        assertEquals(testImage.size(), result.size());

        processor.setFilterMode(ImageFilterProcessor.FilterMode.INCLUDE);
        result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterMode(ImageFilterProcessor.FilterMode.EXCLUDE);
        result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterMode(ImageFilterProcessor.FilterMode.BINARY);
        result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterMode(ImageFilterProcessor.FilterMode.SATURATION);
        result = processor.process(testImage.clone());
        assertNotNull(result);
    }

    @Test
    public void testHueRanges() {
        // Test extreme hue values
        processor.setFilterSettings(0, 14, 100, 100, ImageFilterProcessor.FilterMode.INCLUDE);
        Mat result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setHue(360);
        result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setHue(180);
        result = processor.process(testImage.clone());
        assertNotNull(result);
    }

    @Test
    public void testHueWidthWrapping() {
        // Test hue width wrapping around 360 degrees
        processor.setFilterSettings(350, 30, 100, 100, ImageFilterProcessor.FilterMode.INCLUDE);
        Mat result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterSettings(10, 30, 100, 100, ImageFilterProcessor.FilterMode.INCLUDE);
        result = processor.process(testImage.clone());
        assertNotNull(result);
    }

    @Test
    public void testThresholds() {
        // Test different saturation and luminance thresholds
        processor.setFilterSettings(0, 14, 0, 0, ImageFilterProcessor.FilterMode.INCLUDE);
        Mat result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterSettings(0, 14, 255, 255, ImageFilterProcessor.FilterMode.INCLUDE);
        result = processor.process(testImage.clone());
        assertNotNull(result);

        processor.setFilterSettings(0, 14, 128, 128, ImageFilterProcessor.FilterMode.INCLUDE);
        result = processor.process(testImage.clone());
        assertNotNull(result);
    }

    @Test
    public void testNullTermMap() {
        processor.setTermMap(null);
        assertEquals("", processor.getCurrentTerm());
    }

    @Test
    public void testImageDimensions() {
        // Test with different image dimensions
        Mat smallImage = new Mat(50, 50, CvType.CV_8UC3);
        Mat result = processor.process(smallImage);
        assertEquals(smallImage.size(), result.size());

        Mat largeImage = new Mat(1000, 1000, CvType.CV_8UC3);
        result = processor.process(largeImage);
        assertEquals(largeImage.size(), result.size());
    }
}