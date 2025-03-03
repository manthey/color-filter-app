package com.orbitals.colorfilter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "Color Filter";
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private HashMap<Integer, String> coarseHueMap;
    private HashMap<Integer, String> fineHueMap;

    ArrayList<TermMap> termMaps = new ArrayList<>();

    private ImageFilterProcessor filter;

    // UI elements
    private TextureView textureView;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private String cameraId;
    private boolean isFrontCamera = false;  // Flag for front/back camera
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private ActivityResultLauncher<Intent> pickImageLauncher;

    private Bitmap loadedImage = null;
    private boolean isImageMode = false;
    private final PointF lastTouch = new PointF();
    private final Matrix imageMatrix = new Matrix();
    private final RectF imageBounds = new RectF();
    private final float[] matrixValues = new float[9];

    // Pinch to zoom variables
    private float mScaleFactor = 1.0f;
    private float mMinZoom;
    private float mMaxZoom;
    private float mLastTouchDistance = -1f;

    static {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded successfully");
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Button switchCameraButton, filterButton, loadImageButton, bctButton;
        SeekBar hueSeekBar, hueWidthSeekBar, saturationSeekBar, luminanceSeekBar, bctSeekBar;

        super.onCreate(savedInstanceState);
        setHueMaps();
        loadTermMaps();
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);

        filter = new ImageFilterProcessor();
        filter.setFilterSettings(
                0, 14, 0, 0, 1,
                ImageFilterProcessor.FilterMode.EXCLUDE, termMaps.get(0));

        hueSeekBar = findViewById(R.id.hueSeekBar);
        hueWidthSeekBar = findViewById(R.id.hueWidthSeekBar);
        saturationSeekBar = findViewById(R.id.saturationSeekBar);
        luminanceSeekBar = findViewById(R.id.luminanceSeekBar);
        bctSeekBar = findViewById(R.id.bctSeekBar);
        hueSeekBar.setProgress(filter.getHue());
        hueWidthSeekBar.setProgress(filter.getHueWidth());
        saturationSeekBar.setProgress(filter.getSatThreshold());
        luminanceSeekBar.setProgress(filter.getLumThreshold());
        bctSeekBar.setProgress(filter.getTerm());

        switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(v -> {
            if (isImageMode) {
                isImageMode = false;
                loadedImage = null;
                openCamera();
            } else {
                switchCamera();
            }
        });
        filterButton = findViewById(R.id.filterButton);
        filterButton.setOnClickListener(v -> {
            switch (filter.getFilterMode()) {
                case NONE:
                    filter.setFilterMode(ImageFilterProcessor.FilterMode.INCLUDE);
                    break;
                case INCLUDE:
                    filter.setFilterMode(ImageFilterProcessor.FilterMode.EXCLUDE);
                    break;
                case EXCLUDE:
                    filter.setFilterMode(ImageFilterProcessor.FilterMode.BINARY);
                    break;
                case BINARY:
                    filter.setFilterMode(ImageFilterProcessor.FilterMode.SATURATION);
                    break;
                case SATURATION:
                    filter.setFilterMode(ImageFilterProcessor.FilterMode.NONE);
                    break;
            }
            updateControls();
        });
        loadImageButton = findViewById(R.id.loadImageButton);
        loadImageButton.setOnClickListener(v -> {
            Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(gallery);
        });
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            Uri imageUri = data.getData();
                            try {
                                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                                loadedImage = BitmapFactory.decodeStream(inputStream);
                                isImageMode = true;
                                setupImageMatrix();
                                closeCamera();
                                displayLoadedImage();
                            } catch (Exception e) {
                                Log.e(TAG, "Error loading image", e);
                                Toast.makeText(this, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
        bctButton = findViewById(R.id.bctButton);
        bctButton.setOnClickListener(v -> {
            if (filter.getTermMap() == null) {
                filter.setTermMap(termMaps.get(0));
            } else {
                int currentIdx = termMaps.size();
                for (int i = 0; i < termMaps.size(); i += 1) {
                    if (termMaps.get(i).getName().equals(filter.getTermMap().getName())) {
                        currentIdx = i;
                        break;
                    }
                }
                if (currentIdx + 1 >= termMaps.size()) {
                    filter.setTermMap(null);
                } else {
                    filter.setTermMap(termMaps.get(currentIdx + 1));
                }
            }
            updateControls();
        });

        updateControls();
        // Set up SeekBars to update filter parameters
        hueSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                filter.setHue(progress - (progress % 2));
                updateSeekLabels();
            }
        });
        hueWidthSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                filter.setHueWidth(progress - progress % 2);
                updateSeekLabels();
            }
        });
        saturationSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                filter.setSatThreshold(progress);
                updateSeekLabels();
            }
        });
        luminanceSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                filter.setLumThreshold(progress);
                updateSeekLabels();
            }
        });
        bctSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                filter.setTerm(progress);
                updateSeekLabels();
            }
        });

        // Pinch-to-zoom setup (add touch listener)
        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            if (isImageMode) {
                return handleImageTouch(event);
            } else {
                return handlePinchToZoom(event);
            }
        });
    }

    private void updateControls() {
        Button bctButton = findViewById(R.id.bctButton);
        SeekBar bctSeekBar = findViewById(R.id.bctSeekBar);
        Button filterButton = findViewById(R.id.filterButton);
        switch (filter.getFilterMode()) {
            case INCLUDE:
                filterButton.setText(getString(R.string.filter_button_include));
                break;
            case EXCLUDE:
                filterButton.setText(getString(R.string.filter_button_exclude));
                break;
            case BINARY:
                filterButton.setText(getString(R.string.filter_button_binary));
                break;
            case SATURATION:
                filterButton.setText(getString(R.string.filter_button_saturation));
                break;
            case NONE:
                filterButton.setText(getString(R.string.filter_button_off));
                break;
        }
        if (filter.getTermMap() == null) {
            bctButton.setText(getString(R.string.term_button_hsv));
            findViewById(R.id.bctControls).setVisibility(View.GONE);
            findViewById(R.id.hueControls).setVisibility(View.VISIBLE);
        } else {
            bctButton.setText(filter.getTermMap().getName());
            findViewById(R.id.hueControls).setVisibility(View.GONE);
            findViewById(R.id.bctControls).setVisibility(View.VISIBLE);
            bctSeekBar.setMax(filter.getTermMap().getTerms().size() - 1);
        }
        if (isImageMode) {
            displayLoadedImage();
        }
        updateSeekLabels();
    }

    @SuppressLint("DefaultLocale")
    private void updateSeekLabels() {
        TextView hueLabel = findViewById(R.id.hueLabel);
        hueLabel.setText(String.format("%s - %d - %s - %s",
                getString(R.string.hue), filter.getHue(),
                getColorName(filter.getHue(), coarseHueMap),
                getColorName(filter.getHue(), fineHueMap)));
        TextView hwLabel = findViewById(R.id.hueWidthLabel);
        hwLabel.setText(String.format("%s - %d",
                getString(R.string.hue_width), filter.getHueWidth()));
        TextView satLabel = findViewById(R.id.saturationLabel);
        satLabel.setText(String.format("%s - %d",
                getString(R.string.saturation), filter.getSatThreshold()));
        TextView lumLabel = findViewById(R.id.luminanceLabel);
        lumLabel.setText(String.format("%s - %d",
                getString(R.string.luminance), filter.getLumThreshold()));
        TextView bctLabel = findViewById(R.id.bctLabel);
        bctLabel.setText(String.format("%s - %s",
                getString(R.string.term), filter.getCurrentTerm()));
        if (isImageMode) {
            displayLoadedImage();
        }
    }

    private void setHueMaps() {
        coarseHueMap = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            coarseHueMap.put(
                    i * 30, getResources().getStringArray(R.array.coarse_hue_map)[i]);
        }

        fineHueMap = new HashMap<>();
        for (int i = 0; i <= 24; i++) {
            fineHueMap.put(
                    Math.max(0, i * 15 - 7),
                    getResources().getStringArray(R.array.fine_hue_map)[i == 24 ? 0 : i]);
        }
    }

    private void loadTermMaps() {
        int NAME = 0;
        int DESCRIPTION = 1;
        int REFERENCE = 2;
        int TERMS = 3;
        int IMAGE = 4;

        Resources resources = getResources();
        try (TypedArray termMapIds = resources.obtainTypedArray(R.array.term_map_ids)) {
            for (int i = 0; i < termMapIds.length(); i++) {
                int termMapId = termMapIds.getResourceId(i, 0);
                try (TypedArray termMapArray = resources.obtainTypedArray(termMapId)) {
                    String name = termMapArray.getString(NAME);
                    String description = termMapArray.getString(DESCRIPTION);
                    String reference = termMapArray.getString(REFERENCE);

                    int termsArrayId = termMapArray.getResourceId(TERMS, 0);
                    List<String> terms = Collections.unmodifiableList(Arrays.asList(resources.getStringArray(termsArrayId)));

                    String imageName = termMapArray.getString(IMAGE);
                    int termMapResourceId = resources.getIdentifier(imageName, "raw", resources.getResourcePackageName(R.string.app_name));
                    termMaps.add(new TermMap(name, description, reference, terms, resources, termMapResourceId));
                }
            }
        }
    }

    private String getColorName(int hue, HashMap<Integer, String> hueMap) {
        int closestHue = 0;
        int minDistance = 360;

        for (Integer keyHue : hueMap.keySet()) {
            int distance = hue - keyHue;
            if (distance >= 0 && distance < minDistance) {
                minDistance = distance;
                closestHue = keyHue;
            }
        }
        return hueMap.get(closestHue);
    }

    private boolean handlePinchToZoom(MotionEvent event) {
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if (event.getPointerCount() == 2) {
            // Two-finger touch (pinch)
            float currentDistance = getDistance(event);

            if (mLastTouchDistance != -1f) {
                mScaleFactor *= currentDistance / mLastTouchDistance;
                mScaleFactor = Math.max(mMinZoom, Math.min(mScaleFactor, mMaxZoom));
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
            // Get the max zoom ratio
            Float maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            if (maxZoom == null) {
                Log.e(TAG, "Maximum zoom ratio not available.");
                return;
            }
            // Limit the scale to the maximum zoom ratio
            scale = Math.max(mMinZoom, Math.min(scale, maxZoom));
            // Set the zoom ratio in the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, scale);
            // Trigger AF when zooming
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

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
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (!isImageMode) {
            openCamera();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
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
            //noinspection DataFlowIssue
            mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

            cameraOpenCloseLock.acquire(); //Acquire before opening the camera.
            manager.openCamera(cameraId, stateCallback, null);
            Log.d(TAG, "openCamera opened");
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release(); //Release on opening.
            Log.d(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
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
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (!bigEnough.isEmpty()) {
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
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //noinspection ArraysAsListWithZeroOrOneArgument
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
                    Toast.makeText(MainActivity.this, getString(R.string.configuration_change), Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        }
    }

    private abstract static class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private int getCorrectRotation() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            //noinspection DataFlowIssue
            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees;
            switch (deviceRotation) {
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
                default:
                    degrees = 0;
                    break;
            }
            return (sensorOrientation - degrees + 360) % 360;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera characteristics", e);
            return 0;
        }
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireLatestImage()) {
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
                Mat processedMat = filter.process(rgbMat);

                // Convert back to Bitmap for display
                Bitmap bmp = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(processedMat, bmp);

                // Display the bitmap on the TextureView
                Canvas canvas = textureView.lockCanvas();
                if (canvas != null) {
                    float viewWidth = textureView.getWidth();
                    float viewHeight = textureView.getHeight();
                    float bmpWidth = bmp.getWidth();
                    float bmpHeight = bmp.getHeight();
                    Matrix matrix = new Matrix();
                    int rotation = getCorrectRotation();
                    if (isFrontCamera) {
                        matrix.postScale(1, -1, bmpWidth / 2, bmpHeight / 2);
                    }
                    matrix.postRotate(rotation, bmpWidth / 2, bmpHeight / 2);
                    float scale = Math.max(viewWidth / bmpWidth, viewHeight / bmpHeight);
                    if (rotation == 90 || rotation == 270) {
                        scale = Math.max(viewWidth / bmpHeight, viewHeight / bmpWidth);
                    }
                    float dx = (viewWidth - bmpWidth * scale) / 2;
                    float dy = (viewHeight - bmpHeight * scale) / 2;
                    matrix.postScale(scale, scale);
                    matrix.postTranslate(dx, dy);
                    canvas.drawBitmap(bmp, matrix, null);
                    textureView.unlockCanvasAndPost(canvas);
                }

                rgbMat.release(); // Release resources
                processedMat.release();
            }
        }
    };


    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }

        // Apply zoom to the capture request
        applyZoom(mScaleFactor);

        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        }
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
        if (!isImageMode) {
            if (textureView.isAvailable()) {
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(this);
            }
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
                Log.e(TAG, "InterruptedException", e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (isImageMode) {
            textureView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    textureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    setupImageMatrix();
                    displayLoadedImage();
                }
            });
            return;
        }
        // Close and reopen camera to handle the new orientation
        closeCamera();
        openCamera();

        // Update preview size for new orientation
        if (textureView.isAvailable()) {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        textureView.getWidth(), textureView.getHeight());
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to get camera characteristics", e);
            }
        }
    }

    private void setupImageMatrix() {
        float viewWidth = textureView.getWidth();
        float viewHeight = textureView.getHeight();
        float imageWidth = loadedImage.getWidth();
        float imageHeight = loadedImage.getHeight();

        // Calculate scale to fit screen while maintaining aspect ratio
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        // Center the image
        float dx = (viewWidth - imageWidth * scale) / 2;
        float dy = (viewHeight - imageHeight * scale) / 2;

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);

        // Set bounds for pan limits
        imageBounds.set(0, 0, imageWidth, imageHeight);
        imageMatrix.mapRect(imageBounds);
    }

    private void displayLoadedImage() {
        if (loadedImage == null || !isImageMode) return;

        Mat inputMat = new Mat();
        Utils.bitmapToMat(loadedImage, inputMat);
        Mat processedMat = filter.process(inputMat);

        Bitmap processedBitmap = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(processedMat, processedBitmap);

        inputMat.release();
        processedMat.release();

        Canvas canvas = textureView.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(Color.BLACK); // Clear the canvas
            canvas.drawBitmap(processedBitmap, imageMatrix, null); // Draw the image
            textureView.unlockCanvasAndPost(canvas);
        }
    }

    private boolean handleImageTouch(MotionEvent event) {
        if (!isImageMode) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 2) {
                    // Handle zoom
                    float newDistance = getDistance(event);
                    if (mLastTouchDistance > 0) {
                        float scale = newDistance / mLastTouchDistance;

                        // Get current scale
                        imageMatrix.getValues(matrixValues);
                        float currentScale = matrixValues[Matrix.MSCALE_X];

                        // Calculate new scale within limits
                        float minScale = Math.min(
                                textureView.getWidth() / loadedImage.getWidth(),
                                textureView.getHeight() / loadedImage.getHeight()
                        );
                        float maxScale = Math.max(2.0f,
                                Math.max(
                                        textureView.getWidth() / (float) loadedImage.getWidth(),
                                        textureView.getHeight() / (float) loadedImage.getHeight()
                                ) * 2
                        );

                        float newScale = Math.min(Math.max(currentScale * scale, minScale), maxScale);
                        scale = newScale / currentScale;

                        // Scale around center point between fingers
                        float centerX = (event.getX(0) + event.getX(1)) / 2;
                        float centerY = (event.getY(0) + event.getY(1)) / 2;
                        imageMatrix.postScale(scale, scale, centerX, centerY);
                    }
                    mLastTouchDistance = newDistance;
                } else if (event.getPointerCount() == 1) {
                    // Handle pan
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;

                    imageMatrix.postTranslate(dx, dy);
                    lastTouch.set(event.getX(), event.getY());
                }

                // Enforce bounds
                constrainImage();
                displayLoadedImage();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mLastTouchDistance = -1;
                return true;
        }
        return false;
    }

    private void constrainImage() {
        RectF rect = new RectF(0, 0, loadedImage.getWidth(), loadedImage.getHeight());
        imageMatrix.mapRect(rect);

        float dx = 0, dy = 0;
        float minScale = Math.min(
                (float) textureView.getWidth() / loadedImage.getWidth(),
                (float) textureView.getHeight() / loadedImage.getHeight());
        imageMatrix.getValues(matrixValues);
        float currentScale = matrixValues[Matrix.MSCALE_X];
        if (currentScale < minScale) {
            imageMatrix.postScale(
                    minScale / currentScale, minScale / currentScale,
                    textureView.getWidth() / 2f, textureView.getHeight() / 2f);
            imageMatrix.mapRect(rect);
        }

        // Constrain horizontal movement
        if (rect.width() <= textureView.getWidth()) {
            dx = (textureView.getWidth() - rect.width()) / 2 - rect.left;
        } else {
            if (rect.left > 0) dx = -rect.left;
            if (rect.right < textureView.getWidth()) dx = textureView.getWidth() - rect.right;
        }

        // Constrain vertical movement
        if (rect.height() <= textureView.getHeight()) {
            dy = (textureView.getHeight() - rect.height()) / 2 - rect.top;
        } else {
            if (rect.top > 0) dy = -rect.top;
            if (rect.bottom < textureView.getHeight()) dy = textureView.getHeight() - rect.bottom;
        }
        imageMatrix.postTranslate(dx, dy);
    }
}

// TODO:
// - full camera zoom (multiple lenses)
// - remember settings
// - color swatches
// - better landscape mode
// - handle videos
// - pick a point and set hue to that value
// - print value at crosshair
// - bct
// - tests
// - deployment
// ? selective focus
