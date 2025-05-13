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
        TypedArray termMapIds = resources.obtainTypedArray(R.array.term_map_ids);
        try {
            termMapId = termMapIds.getResourceId(0, 0);
        } finally {
            termMapIds.recycle();
        }

        String name;
        String id;
        String description;
        String reference;
        List<String> terms;
        int termMapResourceId;
        TypedArray termMapArray = resources.obtainTypedArray(termMapId);
        try {
            name = termMapArray.getString(0);
            id = termMapArray.getString(1);
            description = termMapArray.getString(2);
            reference = termMapArray.getString(3);
            int termsArrayId = termMapArray.getResourceId(4, 0);
            terms = List.of(resources.getStringArray(termsArrayId));
            int imagesArrayId = termMapArray.getResourceId(5, 0);
            TypedArray imagesArray = resources.obtainTypedArray(imagesArrayId);
            try {
                int imageArrayId = imagesArray.getResourceId(0, 0);
                TypedArray imageArray = resources.obtainTypedArray(imageArrayId);
                try {
                    termMapResourceId = imageArray.getResourceId(1, 0);
                } finally {
                    imageArray.recycle();
                }
            } finally {
                imagesArray.recycle();
            }
        } finally {
            termMapArray.recycle();
        }
        return new TermMap(name, id, description, reference, terms, resources, termMapResourceId);
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
        assertEquals(5, termMap.getBlur()); // Default blur value

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