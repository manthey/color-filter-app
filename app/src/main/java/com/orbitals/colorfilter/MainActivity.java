package com.orbitals.colorfilter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.util.Size;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    // UI elements
    private TextureView textureView;
    private Button switchCameraBtn;
    private ToggleButton filterToggleBtn, includeExcludeToggle;
    private SeekBar hueSeekBar, hueWidthSeekBar, saturationSeekBar, luminanceSeekBar;

    // Camera2 API variables
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private String cameraId;
    private CameraManager cameraManager;
    private Size previewSize;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // Variables for pinch zoom
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoom = 1f; // This is a simplified zoom control value

    // Filter parameters (default values)
    private int hue = 0, hueWidth = 30, satThreshold = 100, lumThreshold = 100;
    private boolean filterOn = false;
    private boolean includeMode = true;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV!");
        } else {
            Log.d(TAG, "OpenCV loaded successfully");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain references to UI elements
        textureView = findViewById(R.id.texture_view);
        switchCameraBtn = findViewById(R.id.switchCameraBtn);
        filterToggleBtn = findViewById(R.id.filterToggleBtn);
        includeExcludeToggle = findViewById(R.id.includeExcludeToggle);
        hueSeekBar = findViewById(R.id.hueSeekBar);
        hueWidthSeekBar = findViewById(R.id.hueWidthSeekBar);
        saturationSeekBar = findViewById(R.id.saturationSeekBar);
        luminanceSeekBar = findViewById(R.id.luminanceSeekBar);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // Set initial camera. We pick the first camera id.
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // Set up TextureView listener
        textureView.setSurfaceTextureListener(textureListener);

        // Set up pinch zoom detector
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentZoom *= detector.getScaleFactor();
                currentZoom = Math.max(1f, Math.min(currentZoom, 5f)); // clamp zoom value to [1,5]
                updateZoom();
                return true;
            }
        });
        textureView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;  // return true to indicate the touch events are handled
        });

        // Set up button to switch cameras
        switchCameraBtn.setOnClickListener(view -> switchCamera());

        // Set up filter toggle controls
        filterToggleBtn.setOnCheckedChangeListener((buttonView, isChecked) -> filterOn = isChecked);
        includeExcludeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> includeMode = isChecked);

        // Set up SeekBars to update filter parameters
        hueSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hue = progress;
            }
        });
        hueWidthSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hueWidth = progress;
            }
        });
        saturationSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                satThreshold = progress;
            }
        });
        luminanceSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lumThreshold = progress;
            }
        });
    }

    // Simple SeekBarChangeListener base class so you only override what you need.
    private abstract class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    // TextureView listener to know when the preview is available
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Start background thread and open camera when texture is available
            startBackgroundThread();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            stopBackgroundThread();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Each time the texture updates, you might grab the image and run your filter processing.
            if (filterOn) {
                // You’d typically retrieve the camera frame as a Bitmap and convert it to OpenCV Mat.
                // For demonstration, suppose we capture the current frame:
                final android.graphics.Bitmap bmp = textureView.getBitmap();
                if (bmp != null) {
                    Mat mat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC4);
                    org.opencv.android.Utils.bitmapToMat(bmp, mat);
                    Mat processed = processFrame(mat);
                    // Convert the processed Mat back to Bitmap and display (for example, on an ImageView overlay)
                    // Alternatively, you can draw directly to the TextureView’s Canvas.
                    // This code is kept simple for illustration.
                    org.opencv.android.Utils.matToBitmap(processed, bmp);
                    Canvas canvas = textureView.lockCanvas();
                    if (canvas != null) {
                        canvas.drawBitmap(bmp, 0, 0, null);
                        textureView.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    };

    // Open the chosen camera
    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }
            // You might choose an appropriate previewSize here (omitted for brevity)
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Callback for CameraDevice state changes.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // Set up and start the camera preview.
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            // Configure buffer size if needed (example: previewSize.width x previewSize.height)
            // texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Configuration change");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        try {
            // Add zoom to the capture request if supported.
            // (A real implementation would query the camera's zoom capabilities and set the proper RECT)
            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, getZoomRect());
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Dummy function to illustrate zoom rectangle (you must calculate based on sensor active array size)
    private android.graphics.Rect getZoomRect() {
        // For a “real” app, query CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE.
        // This is a placeholder that returns a fixed region based on currentZoom.
        int left = 100;
        int top = 100;
        int right = 500;
        int bottom = 500;
        // Adjust crop by zoom factor.
        int dx = (int) ((right - left) * (currentZoom - 1) / 2);
        int dy = (int) ((bottom - top) * (currentZoom - 1) / 2);
        return new android.graphics.Rect(left + dx, top + dy, right - dx, bottom - dy);
    }

    private void updateZoom() {
        if (cameraDevice == null || captureSession == null || previewRequestBuilder == null) return;
        updatePreview();
    }

    // Switch camera, for example toggling between front and back.
    private void switchCamera() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            //  Simple approach: choose the next available camera
            for (String id : cameraIds) {
                if (!id.equals(cameraId)) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            openCamera();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Process the image frame using OpenCV.
    // When filterOn is true, we apply either an “include” or “exclude” filter.
    private Mat processFrame(Mat input) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // Define lower and upper bounds for the hue range.
        // Note: OpenCV’s Hue range is typically 0..180.
        // Convert the slider values accordingly.
        int lowerHue = (int) (hue / 2.0 - hueWidth / 2.0);
        int upperHue = (int) (hue / 2.0 + hueWidth / 2.0);
        // Lower saturation and value thresholds.
        Scalar lowerb = new Scalar(lowerHue, satThreshold, lumThreshold);
        Scalar upperb = new Scalar(upperHue, 255, 255);

        Mat mask = new Mat();
        Core.inRange(hsv, lowerb, upperb, mask);

        Mat output = Mat.zeros(input.size(), input.type());
        if (includeMode) {
            // For include mode: copy pixels where mask is nonzero.
            input.copyTo(output, mask);
        } else {
            // For exclude mode: invert mask and copy.
            Mat invertedMask = new Mat();
            Core.bitwise_not(mask, invertedMask);
            input.copyTo(output, invertedMask);
        }
        return output;
    }

    // Background thread management
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        stopBackgroundThread();
        super.onPause();
    }

    // Handle camera permission results.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                // Handle permission denial gracefully.
            }
        }
    }
}
