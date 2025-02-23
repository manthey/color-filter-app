package com.orbitals.colorfilter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "Color Filter";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    public enum FilterMode {
        NONE,
        INCLUDE,
        EXCLUDE,
        BINARY
    }

    // UI elements
    private TextureView textureView;
    private Button switchCameraButton, filterButton, loadImageButton;
    private TextView filterButtonText;
    private SeekBar hueSeekBar, hueWidthSeekBar, saturationSeekBar, luminanceSeekBar;


    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private String cameraId;
    private boolean isFrontCamera = false;  // Flag for front/back camera
    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    // Pinch to zoom variables
    private float mScaleFactor = 1.0f;
    private float mMinZoom;
    private float mMaxZoom;
    private float mLastTouchDistance = -1f;

    // Filter parameters (default values)
    private int hue = 0, hueWidth = 30, satThreshold = 100, lumThreshold = 100;
    private FilterMode filterMode = FilterMode.NONE;

    static {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded successfully");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);

        switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        filterButton = findViewById(R.id.filterButton);
        filterButtonText = findViewById(R.id.filterButton);
        filterButton.setOnClickListener(v -> {
            switch (filterMode) {
                case NONE:
                    filterMode = FilterMode.INCLUDE;
                    filterButtonText.setText("Include");
                    break;
                case INCLUDE:
                    filterMode = FilterMode.EXCLUDE;
                    filterButtonText.setText("Exclude");
                    break;
                case EXCLUDE:
                    filterMode = FilterMode.BINARY;
                    filterButtonText.setText("Binary");
                    break;
                case BINARY:
                    filterMode = FilterMode.NONE;
                    filterButtonText.setText("Off");
                    break;
            }
        });
        loadImageButton = findViewById(R.id.loadImageButton);
        hueSeekBar = findViewById(R.id.hueSeekBar);
        hueWidthSeekBar = findViewById(R.id.hueWidthSeekBar);
        saturationSeekBar = findViewById(R.id.saturationSeekBar);
        luminanceSeekBar = findViewById(R.id.luminanceSeekBar);
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

        // Pinch-to-zoom setup (add touch listener)
        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handlePinchToZoom(event);
            }
        });
    }

    private boolean handlePinchToZoom(MotionEvent event) {
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if (event.getPointerCount() == 2) {
            // Two-finger touch (pinch)
            float currentDistance = getDistance(event);

            if (mLastTouchDistance != -1f) {
                // Calculate scale factor
                mScaleFactor *= currentDistance / mLastTouchDistance;
                mScaleFactor = Math.max(mMinZoom, Math.min(mScaleFactor, mMaxZoom));

                // Apply zoom to camera
                applyZoom(mScaleFactor);
            }
            mLastTouchDistance = currentDistance;
            return true; // Consume the event
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Reset last touch distance when fingers are lifted
            mLastTouchDistance = -1f;
        }

        return false; // Don't consume single-finger events
    }

    private float getDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void applyZoom(float scale) {
        if (cameraDevice == null || cameraCaptureSession == null) {
            return;
        }
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            //Get the max zoom
            if (mMaxZoom == 0) // Only set once.
                mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

            Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int deltaX = (int) ((m.width() - m.width() / scale) / 2);
            int deltaY = (int) ((m.height() - m.height() / scale) / 2);
            Rect zoomRect = new Rect(deltaX, deltaY, m.width() - deltaX, m.height() - deltaY);

            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallback, backgroundHandler);


        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to apply zoom", e);
        }
    }

    private void switchCamera() {
        isFrontCamera = !isFrontCamera;
        closeCamera();
        openCamera();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "is camera open");
        try {
            cameraId = isFrontCamera ? manager.getCameraIdList()[1] : manager.getCameraIdList()[0]; // Select camera

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), textureView.getWidth(), textureView.getHeight());

            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            // Get min/max zoom
            mMinZoom = 1.0f; // Minimum zoom is no zoom
            mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

            cameraOpenCloseLock.acquire(); //Acquire before opening the camera.
            manager.openCamera(cameraId, stateCallback, null);
            Log.d(TAG, "openCamera opened");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraOpenCloseLock.release(); //Release on opening.
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null; 
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            Log.e(TAG, String.format("Preview %d %d %d %d", width, height, option.getWidth(), option.getHeight()));
            if (option.getHeight() * width == option.getWidth() * height &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    protected void createCameraPreview() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Set up ImageReader for processing frames
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(),
                    ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private abstract class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    return;
                }

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);

                // Convert JPEG bytes to Bitmap
                Bitmap inputBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

                // Convert Bitmap to Mat for processing
                Mat rgbMat = new Mat();
                Utils.bitmapToMat(inputBmp, rgbMat);

                // Process the image
                Mat processedMat = processImage(rgbMat);

                // Convert back to Bitmap for display
                Bitmap bmp = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(processedMat, bmp);

                // Display the bitmap on the TextureView
                Canvas canvas = textureView.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, 0, 0, null);
                    textureView.unlockCanvasAndPost(canvas);
                }

                rgbMat.release(); // Release resources
                processedMat.release();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    private Mat processImage(Mat input) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGBA2RGB);
        Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGB2HSV);
        //Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGBA2RGB);
        //Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

        // Define lower and upper bounds for the hue range.
        // Note: OpenCV’s Hue range is typically 0..180.
        // Convert the slider values accordingly.
        int lowerHue = (int) (hue / 2.0 - hueWidth / 2.0);
        int upperHue = (int) (hue / 2.0 + hueWidth / 2.0);
        int lowerHueLimit = Math.max(0, lowerHue);
        int upperHueLimit = Math.min(180, upperHue);
        // Lower saturation and value thresholds.
        Scalar lowerBound = new Scalar(lowerHueLimit, satThreshold, lumThreshold);
        Scalar upperBound = new Scalar(upperHueLimit, 255, 255);

        Mat mask = new Mat();
        Core.inRange(hsv, lowerBound, upperBound, mask);
        if (lowerHue < 0 || upperHue > 180) {
            if (lowerHue < 0) {
                lowerHueLimit = lowerHue + 180;
                upperHueLimit = 180;
            } else {
                lowerHueLimit = 0;
                upperHueLimit = upperHue - 180;
            }
            lowerBound = new Scalar(lowerHueLimit, satThreshold, lumThreshold);
            upperBound = new Scalar(upperHueLimit, 255, 255);
            Mat mask2 = new Mat();
            Core.inRange(hsv, lowerBound, upperBound, mask2);
            Core.bitwise_or(mask, mask2, mask);
            mask2.release();
        }

        Mat output = Mat.zeros(input.size(), input.type());
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
        }
        mask.release();
        return output;
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }

        // Apply zoom to the capture request
        applyZoom(mScaleFactor);

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == imageDimension) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, imageDimension.getHeight(), imageDimension.getWidth()); //Swapped for rotation
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / imageDimension.getHeight(),
                    (float) viewWidth / imageDimension.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);

        }
        textureView.setTransform(matrix);

    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != cameraCaptureSession) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(this);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    protected void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
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

    //Empty capture callback (needed for applyZoom)
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Permission is required for basic function", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

}

// TODO:
// - camera scale
// - camera orientation
// - camera zoom
// - focus
// - load image
// - icon
// - remember settings
