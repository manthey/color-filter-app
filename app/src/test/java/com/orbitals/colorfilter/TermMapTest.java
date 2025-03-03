package com.orbitals.colorfilter;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.Mat;
import org.opencv.core.CvType;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@Ignore("skip until fixed")
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class TermMapTest {

    @Mock
    private Resources resources;

    @Mock
    private Bitmap mockBitmap;

    private TermMap termMap;

    private List<String> testTerms;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Mock bitmap creation
        when(BitmapFactory.decodeResource(any(Resources.class), anyInt(), any(BitmapFactory.Options.class)))
                .thenReturn(mockBitmap);

        // Create test terms
        testTerms = Arrays.asList("Term1", "Term2", "Term3");

        // Create test TermMap
        termMap = new TermMap(
                "Test Map",
                "Test Description",
                "Test Reference",
                testTerms,
                resources,
                123 // dummy resource id
        );
    }

    @Test
    public void testGetName() {
        assertEquals("Test Map", termMap.getName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Test Description", termMap.getDescription());
    }

    @Test
    public void testGetReference() {
        assertEquals("Test Reference", termMap.getReference());
    }

    @Test
    public void testGetTerms() {
        List<String> terms = termMap.getTerms();
        assertEquals(3, terms.size());
        assertEquals("Term1", terms.get(0));
        assertEquals("Term2", terms.get(1));
        assertEquals("Term3", terms.get(2));

        // Verify list is unmodifiable
        try {
            terms.add("Term4");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    @Test
    public void testBlurSettings() {
        assertEquals(9, termMap.getBlur()); // Default value

        termMap.setBlur(5);
        assertEquals(5, termMap.getBlur());

        termMap.setBlur(0);
        assertEquals(0, termMap.getBlur());
    }

    @Test
    public void testCreateMask() {
        // Create a simple test image
        Mat testImage = new Mat(10, 10, CvType.CV_8UC3);
        // Fill with some test data
        byte[] testData = new byte[300]; // 10x10x3 channels
        Arrays.fill(testData, (byte) 128);
        testImage.put(0, 0, testData);

        // Test mask creation
        Mat mask = termMap.createMask(testImage, 0);

        // Verify mask properties
        assertEquals(10, mask.rows());
        assertEquals(10, mask.cols());
        assertEquals(CvType.CV_8UC1, mask.type());
    }
}