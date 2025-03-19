package com.orbitals.colorfilter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class CameraController {
    private final TextureView textureView;
    private final Context context;
    private final Supplier<Boolean> checkCameraPermissions;
    private final FilterProcessor filter;

    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.CameraController";
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private String cameraId;
    private boolean isFrontCamera = false;  // Flag for front/back camera
    private Handler backgroundHandler;
    private float mScaleFactor = 1.0f;

    private float mMinZoom;
    private float mMaxZoom;
    private Matrix matrix;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private HandlerThread backgroundThread;

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    public interface FilterUpdateCallback {
        void onFilterUpdated();
    }

    private final FilterUpdateCallback updateCallback;

    public CameraController(
            Context context, TextureView textureView, Supplier<Boolean> checkCameraPermissions,
            FilterProcessor filter, FilterUpdateCallback updateCallback) {
        this.context = context;
        this.textureView = textureView;
        this.checkCameraPermissions = checkCameraPermissions;
        this.filter = filter;
        this.updateCallback = updateCallback;
    }

    public void adjustZoom(float factor) {
        mScaleFactor *= factor;
        mScaleFactor = Math.max(mMinZoom, Math.min(mScaleFactor, mMaxZoom));
        applyZoom(mScaleFactor);
    }

    public void applyZoom(float scale) {
        if (cameraDevice == null || cameraCaptureSession == null) {
            return;
        }
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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

    public void setBackgroundHandler(Handler backgroundHandler) {
        this.backgroundHandler = backgroundHandler;
    }

    public void switchCamera() {
        isFrontCamera = !isFrontCamera;
        closeCamera();
        openCamera();
    }

    public void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            int numCameras = manager.getCameraIdList().length;
            if (numCameras < 1) {
                Log.d(TAG, "openCamera no cameras");
                return;
            }
            cameraId = isFrontCamera && numCameras >= 2 ? manager.getCameraIdList()[1] : manager.getCameraIdList()[0]; // Select camera

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), textureView.getWidth(), textureView.getHeight());

            if (checkCameraPermissions.get()) {
                return;
            }

            // Get min/max zoom
            mMinZoom = 1.0f; // Minimum zoom is no zoom
            //noinspection DataFlowIssue
            mMaxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

            cameraOpenCloseLock.acquire(); //Acquire before opening the camera.
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "CameraAccessPermission issue");
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
            Log.d(TAG, "openCamera opened");
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

    }

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

    protected void createCameraPreview() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Set up ImageReader for processing frames
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(),
                    ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            List<OutputConfiguration> outputConfigs = new ArrayList<>();
            OutputConfiguration imageOutputConfig = new OutputConfiguration(imageReader.getSurface());
            outputConfigs.add(imageOutputConfig);
            SessionConfiguration config = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.getMainExecutor(), // Use the main thread for callbacks
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, context.getString(R.string.configuration_change), Toast.LENGTH_SHORT).show();
                        }
                    });
            cameraDevice.createCaptureSession(config);

        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException", e);
        }
    }

    private int getCorrectRotation() {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            //noinspection DataFlowIssue
            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
            int deviceRotation = display.getRotation();
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

                if (filter.getSampleMode()) {
                    Mat centerChunk = Utilities.centerOfImage(context, textureView, filter, rgbMat, matrix);
                    if (filter.sampleRegion(centerChunk) && updateCallback != null) {
                        ((Activity) context).runOnUiThread(updateCallback::onFilterUpdated);
                    }
                    centerChunk.release();
                }
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
                    matrix = new Matrix();
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
                    Utilities.drawSamplingCircle(context, filter, canvas);
                    textureView.unlockCanvasAndPost(canvas);
                }
                rgbMat.release();
                processedMat.release();
            }
        }
    };

    protected void updatePreview() {
        if (cameraDevice == null) {
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

    public void closeCamera() {
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

    public void reopenCamera() {
        // Close and reopen camera to handle the new orientation
        closeCamera();
        openCamera();

        // Update preview size for new orientation
        if (textureView.isAvailable()) {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
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

    protected void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        setBackgroundHandler(new Handler(backgroundThread.getLooper()));
    }

    protected void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                setBackgroundHandler(null);
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException", e);
            }
        }
    }


}
