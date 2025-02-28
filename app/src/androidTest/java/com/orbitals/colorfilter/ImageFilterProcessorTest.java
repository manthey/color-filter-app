package com.orbitals.colorfilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class ImageFilterProcessorTest {

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
}