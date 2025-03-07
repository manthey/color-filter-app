package com.orbitals.colorfilter;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Handler;
import android.util.Size;
import android.view.TextureView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public class CameraControllerTest {

    private CameraController cameraController;

    @Before
    public void setup() {
        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            throw new RuntimeException("Failed to initialize OpenCV");
        }

        // Get application context
        Context context = ApplicationProvider.getApplicationContext();

        // Create real mocks (not using @Mock annotation)
        TextureView textureView = mock(TextureView.class);
        ImageFilterProcessor filterProcessor = mock(ImageFilterProcessor.class);
        Handler handler = mock(Handler.class);
         Supplier<Boolean> permissionSupplier = () -> true;

        // Initialize controller
        cameraController = new CameraController(context, textureView, permissionSupplier, filterProcessor);
        cameraController.setBackgroundHandler(handler);
    }

    @Test
    public void testConstructor() {
        assertNotNull("CameraController should be created", cameraController);
    }

    @Test
    public void testSwitchCamera() {
        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);

        // Use doNothing() for void methods
        doNothing().when(spyController).closeCamera();
        doNothing().when(spyController).openCamera();

        // Test camera switching
        spyController.switchCamera();

        // Verify that closeCamera and openCamera were called
        verify(spyController, times(1)).closeCamera();
        verify(spyController, times(1)).openCamera();
    }

    @Test
    public void testReopenCamera() {
        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);

        // Use doNothing() for void methods
        doNothing().when(spyController).closeCamera();
        doNothing().when(spyController).openCamera();

        // Test camera reopening
        spyController.reopenCamera();

        // Verify that closeCamera and openCamera were called
        verify(spyController, times(1)).closeCamera();
        verify(spyController, times(1)).openCamera();
    }

    @Test
    public void testCompareSizesByArea() {
        // Create an instance of the comparator
        CameraController.CompareSizesByArea comparator = new CameraController.CompareSizesByArea();

        // Test comparison
        Size size1 = new Size(1920, 1080);
        Size size2 = new Size(1280, 720);

        // Larger area should return positive value
        assert(comparator.compare(size1, size2) > 0);

        // Smaller area should return negative value
        assert(comparator.compare(size2, size1) < 0);

        // Equal areas should return 0
        //noinspection EqualsWithItself
        assert(comparator.compare(size1, size1) == 0);
    }
}
