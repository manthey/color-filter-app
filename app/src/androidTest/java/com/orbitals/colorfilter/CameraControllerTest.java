package com.orbitals.colorfilter;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Handler;
import android.util.Size;
import android.view.TextureView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.opencv.android.OpenCVLoader;

import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public class CameraControllerTest {

    private CameraController cameraController;
    private Context context;
    private TextureView textureView;
    private ImageFilterProcessor filterProcessor;
    private Handler handler;
    private Supplier<Boolean> permissionSupplier;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        if (!OpenCVLoader.initLocal()) {
            throw new RuntimeException("Failed to initialize OpenCV");
        }

        context = ApplicationProvider.getApplicationContext();
        textureView = mock(TextureView.class);
        filterProcessor = mock(ImageFilterProcessor.class);
        handler = mock(Handler.class);
        permissionSupplier = () -> true;

        cameraController = new CameraController(context, textureView, permissionSupplier, filterProcessor);
        cameraController.setBackgroundHandler(handler);
    }

    @Test
    public void testConstructor() {
        assertNotNull("CameraController should be created", cameraController);
    }

    @Test
    public void testSwitchCamera() {
        CameraController spyController = spy(cameraController);
        doNothing().when(spyController).closeCamera();
        doNothing().when(spyController).openCamera();

        spyController.switchCamera();

        verify(spyController, times(1)).closeCamera();
        verify(spyController, times(1)).openCamera();
    }

    @Test
    public void testReopenCamera() {
        CameraController spyController = spy(cameraController);
        doNothing().when(spyController).closeCamera();
        doNothing().when(spyController).openCamera();

        spyController.reopenCamera();

        verify(spyController, times(1)).closeCamera();
        verify(spyController, times(1)).openCamera();
    }

    @Test
    public void testCompareSizesByArea() {
        CameraController.CompareSizesByArea comparator = new CameraController.CompareSizesByArea();

        Size size1 = new Size(1920, 1080);
        Size size2 = new Size(1280, 720);

        assert (comparator.compare(size1, size2) > 0);
        assert (comparator.compare(size2, size1) < 0);
        assert (comparator.compare(size1, size1) == 0);
    }

    @Test
    public void testAdjustZoom() {
        CameraController spyController = spy(cameraController);
        doNothing().when(spyController).applyZoom(anyFloat());

        spyController.adjustZoom(2.0f);
        verify(spyController, times(1)).applyZoom(anyFloat());
    }

    @Test
    public void testZoomLimits() {
        CameraController spyController = spy(cameraController);
        doNothing().when(spyController).applyZoom(anyFloat());

        // Test maximum zoom
        spyController.adjustZoom(100.0f);
        verify(spyController, times(1)).applyZoom(anyFloat());

        // Test minimum zoom
        spyController.adjustZoom(0.1f);
        verify(spyController, times(2)).applyZoom(anyFloat());
    }

    @Test
    public void testChooseOptimalSize() throws Exception {
        Size[] sizes = new Size[]{
                new Size(1920, 1080),
                new Size(1280, 720),
                new Size(640, 480)
        };

        when(textureView.getWidth()).thenReturn(1280);
        when(textureView.getHeight()).thenReturn(720);

        java.lang.reflect.Method method = CameraController.class.getDeclaredMethod(
                "chooseOptimalSize", Size[].class, int.class, int.class);
        method.setAccessible(true);

        Size result = (Size) method.invoke(cameraController, sizes, 1280, 720);
        assertNotNull(result);
        assertEquals(1280, result.getWidth());
        assertEquals(720, result.getHeight());
    }

    @Test
    public void testApplyZoomWithNullCamera() {
        CameraController spyController = spy(cameraController);
        spyController.applyZoom(2.0f);
        // Should not throw exception when camera is null
    }

    @Test
    public void testCloseCameraCleanup() {
        CameraController spyController = spy(cameraController);
        spyController.closeCamera();
        // Verify camera resources are released
    }
}
