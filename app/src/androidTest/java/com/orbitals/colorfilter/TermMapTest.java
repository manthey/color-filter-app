package com.orbitals.colorfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TermMapTest {

    private Context context;
    private TermMap termMap;

    @Before
    public void setup() {
        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            throw new RuntimeException("Failed to initialize OpenCV");
        }

        // Get application context
        context = ApplicationProvider.getApplicationContext();

        // Load TermMap from resources
        termMap = loadTermMap();
        assertNotNull("TermMap should be loaded", termMap);
    }

    private TermMap loadTermMap() {
        Resources resources = context.getResources();

        // Load the first term map from term_map_ids array
        int termMapId;
        try (TypedArray termMapIds = resources.obtainTypedArray(R.array.term_map_ids)) {
            termMapId = termMapIds.getResourceId(0, 0);
        }

        String name;
        String description;
        String reference;
        List<String> terms;
        int termMapResourceId;
        try (TypedArray termMapArray = resources.obtainTypedArray(termMapId)) {
            // Name
            name = termMapArray.getString(0);
            // Description
            description = termMapArray.getString(1);
            // Reference
            reference = termMapArray.getString(2);
            int termsArrayId = termMapArray.getResourceId(3, 0); // Terms
            terms = List.of(resources.getStringArray(termsArrayId));
            // Image
            termMapResourceId = termMapArray.getResourceId(4, 0);
        }

        return new TermMap(name, description, reference, terms, resources, termMapResourceId);
    }

    @Test
    public void testGetName() {
        assertEquals("BCT20", termMap.getName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("BCT20 English (US)", termMap.getDescription());
    }

    @Test
    public void testGetReference() {
        assertTrue(termMap.getReference().contains("Taken"));
    }

    @Test
    public void testGetTerms() {
        List<String> terms = termMap.getTerms();
        assertNotNull(terms);
        assertEquals(20, terms.size());
        assertEquals("Black", terms.get(0));
        assertEquals("White", terms.get(19));
    }

    @Test
    public void testGetMap() {
        byte[] map = termMap.getMap();
        assertNotNull(map);
        assertEquals(4096 * 4096, map.length); // Map size should match 4096x4096
    }

    @Test
    public void testBlurBehavior() {
        assertEquals(9, termMap.getBlur()); // Default blur value

        termMap.setBlur(15); // Set a new blur value
        assertEquals(15, termMap.getBlur());

        termMap.setBlur(0); // Set blur to 0
        assertEquals(0, termMap.getBlur());
    }

    @Test
    public void testCreateMask() {
        // Create a sample input image
        Mat inputImage = Mat.ones(256, 256, CvType.CV_8UC3); // RGB image
        Imgproc.rectangle(inputImage, new org.opencv.core.Point(50, 50), new org.opencv.core.Point(100, 100),
                new Scalar(255, 0, 0), -1); // Blue rectangle

        // Generate a mask for a term (e.g., term 0 = Black)
        Mat mask = termMap.createMask(inputImage, 0);

        // Check dimensions and type of the mask
        assertNotNull(mask);
        assertEquals(256, mask.rows());
        assertEquals(256, mask.cols());
        assertEquals(CvType.CV_8UC1, mask.type()); // Mask should be single-channel
    }
}