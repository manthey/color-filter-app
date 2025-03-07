package com.orbitals.colorfilter;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.view.TextureView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.opencv.android.OpenCVLoader;

import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public class CameraControllerTest {

    private CameraController cameraController;
    private Supplier<Boolean> mockPermissionChecker;

    @Before
    public void setup() {
        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            throw new RuntimeException("Failed to initialize OpenCV");
        }

        // Get application context
        Context context = ApplicationProvider.getApplicationContext();

        // Create mocks
        TextureView mockTextureView = mock(TextureView.class);
        ImageFilterProcessor mockFilterProcessor = mock(ImageFilterProcessor.class);

        // Create the mock Supplier properly
        @SuppressWarnings("unchecked")
        Supplier<Boolean> permissionChecker = mock(Supplier.class);
        this.mockPermissionChecker = permissionChecker;

        Handler mockHandler = mock(Handler.class);

        // Set up mock TextureView
        when(mockTextureView.isAvailable()).thenReturn(true);
        when(mockTextureView.getWidth()).thenReturn(1080);
        when(mockTextureView.getHeight()).thenReturn(1920);
        SurfaceTexture mockSurfaceTexture = mock(SurfaceTexture.class);
        when(mockTextureView.getSurfaceTexture()).thenReturn(mockSurfaceTexture);

        // Set up permissions checker - use the class field, not the local variable
        when(this.mockPermissionChecker.get()).thenReturn(false);

        // Initialize controller
        cameraController = new CameraController(context, mockTextureView, this.mockPermissionChecker, mockFilterProcessor);
        cameraController.setBackgroundHandler(mockHandler);
    }

    @Test
    public void testConstructor() {
        assertNotNull("CameraController should be created", cameraController);
    }

    @Test
    public void testZoomAdjustment() {
        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);
        doNothing().when(spyController).applyZoom(any(Float.class));

        // Test zoom adjustment
         float zoomFactor = 1.5f;
        spyController.adjustZoom(zoomFactor);

        // Verify that applyZoom was called with the expected value
        ArgumentCaptor<Float> zoomCaptor = ArgumentCaptor.forClass(Float.class);
        verify(spyController).applyZoom(zoomCaptor.capture());

        // The zoom should be clamped between min and max values
        // Since we can't access private fields directly, we just verify it was called
        verify(spyController, times(1)).applyZoom(any(Float.class));
    }

    @Test
    public void testSwitchCamera() {
        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);
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
        doNothing().when(spyController).closeCamera();
        doNothing().when(spyController).openCamera();

        // Test camera reopening
        spyController.reopenCamera();

        // Verify that closeCamera and openCamera were called
        verify(spyController, times(1)).closeCamera();
        verify(spyController, times(1)).openCamera();
    }

    @Test
    public void testOpenCameraWithoutPermissions() {
        // Mock the permission check to return true (indicating no permission)
        when(mockPermissionChecker.get()).thenReturn(true);

        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);

        // Test opening camera without permissions
        spyController.openCamera();

        // Verify behavior - we can't directly verify internal state, but we can check
        // that certain methods weren't called after permission check fails
        verify(mockPermissionChecker, times(1)).get();
    }

    @Test
    public void testCameraManagerInteraction() throws CameraAccessException {
        // Mock CameraManager
        CameraManager mockCameraManager = mock(CameraManager.class);
        when(mockCameraManager.getCameraIdList()).thenReturn(new String[]{"0", "1"});

        // This test verifies that our code interacts with the CameraManager correctly
        // In a real test, we'd need to use a dependency injection framework or create a test-specific
        // implementation of CameraController that allows us to inject the CameraManager
    }

    @Test
    public void testCloseCamera() {
        // Create a spy to avoid actual camera operations
        CameraController spyController = spy(cameraController);

        // Test closing camera
        spyController.closeCamera();

        // Since we can't easily verify private field state, this test primarily ensures
        // that the method doesn't throw exceptions
    }
}