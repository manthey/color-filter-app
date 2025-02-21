package com.orbitals.colorfilter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 1;

    // UI elements
    private SurfaceView surfaceView;
    private Button switchCameraBtn, filterButton, loadImageButton;
    private TextView filterButtonText;
    private SeekBar hueSeekBar, hueWidthSeekBar, saturationSeekBar, luminanceSeekBar;

    private SurfaceView cameraPreview;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Bitmap currentBitmap;

    // Variables for pinch zoom
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoom = 1f; // This is a simplified zoom control value

    // Filter parameters (default values)
    private int hue = 0, hueWidth = 30, satThreshold = 100, lumThreshold = 100;
    private boolean filterOn = false;
    private boolean includeMode = true;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded successfully");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain references to UI elements
        surfaceView = findViewById(R.id.texture_view);
        switchCameraBtn = findViewById(R.id.switchCameraBtn);
        filterButton = findViewById(R.id.filterButton);
        filterButtonText = findViewById(R.id.filterButton);
        loadImageButton = findViewById(R.id.loadImageButton);
        hueSeekBar = findViewById(R.id.hueSeekBar);
        hueWidthSeekBar = findViewById(R.id.hueWidthSeekBar);
        saturationSeekBar = findViewById(R.id.saturationSeekBar);
        luminanceSeekBar = findViewById(R.id.luminanceSeekBar);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

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
        surfaceView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;  // return true to indicate the touch events are handled
        });

        // Set up button to switch cameras
        switchCameraBtn.setOnClickListener(view -> switchCamera());
        filterButton.setOnClickListener(v -> {
            if (!filterOn) {
                filterOn = true;
                includeMode = true;
                filterButtonText.setText("Include");
            } else if (includeMode) {
                includeMode = false;
                filterButtonText.setText("Exclude");
            } else {
                filterOn = false;
                filterButtonText.setText("Off");
            }
            applyFilter(); // Re-apply filter on toggle
        });

        // Set up SeekBars to update filter parameters
        hueSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hue = progress;
                if (filterOn) {
                    applyFilter();
                }
            }
        });
        hueWidthSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hueWidth = progress;
                if (filterOn) {
                    applyFilter();
                }
            }
        });
        saturationSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                satThreshold = progress;
                if (filterOn) {
                    applyFilter();
                }
            }
        });
        luminanceSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lumThreshold = progress;
                if (filterOn) {
                    applyFilter();
                }
            }
        });
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startCamera(); // Permission already granted, start camera
        }
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

    private void applyFilter() {
        if (currentBitmap == null) {
            return; // Nothing to filter
        }

        new FilterTask().execute(currentBitmap); // Run filtering in background
    }
    
    /*

    // TextureView listener to know when the preview is available
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startCamera();
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
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Each time the texture updates, you might grab the image and run your filter processing.
            if (filterOn) {
                // You’d typically retrieve the camera frame as a Bitmap and convert it to OpenCV Mat.
                // For demonstration, suppose we capture the current frame:
                final android.graphics.Bitmap bmp = surfaceView.getBitmap();
                if (bmp != null) {
                    Mat mat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC4);
                    org.opencv.android.Utils.bitmapToMat(bmp, mat);
                    Mat processed = processFrame(mat);
                    // Convert the processed Mat back to Bitmap and display (for example, on an ImageView overlay)
                    // Alternatively, you can draw directly to the TextureView’s Canvas.
                    // This code is kept simple for illustration.
                    org.opencv.android.Utils.matToBitmap(processed, bmp);
                    Canvas canvas = surfaceView.lockCanvas();
                    if (canvas != null) {
                        canvas.drawBitmap(bmp, 0, 0, null);
                        surfaceView.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    };
    */

    // Open the chosen camera
    private void startCamera() {
        try {
            if (camera != null) {
                stopCameraPreview();
                camera.release();
                camera = null;
            }
            camera = Camera.open(currentCameraId);
            camera.setDisplayOrientation(90); // Adjust orientation as needed
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Error starting camera preview: " + e.getMessage());
            Toast.makeText(this, "Error starting camera.", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) { // Catch RuntimeException for camera issues
            Log.e(TAG, "Camera open failed: " + e.getMessage());
            Toast.makeText(this, "Camera open failed.", Toast.LENGTH_SHORT).show();
        }    
    }

    private void stopCameraPreview() {
        if (camera != null) {
            camera.stopPreview();
        }
    }

    private void switchCamera() {
        if (Camera.getNumberOfCameras() > 1) {
            currentCameraId = (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
            startCamera();
        } else {
            Toast.makeText(this, "Only one camera available.", Toast.LENGTH_SHORT).show();
        }
    }

    /*
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
    */

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
        // if (cameraDevice == null || captureSession == null || previewRequestBuilder == null) return;
        // updatePreview();
    }

     private class FilterTask extends AsyncTask<Bitmap, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            Bitmap originalBitmap = bitmaps[0];
            Mat input = new Mat();
            Utils.bitmapToMat(originalBitmap, input);
            Mat hsv = new Mat();
            Imgproc.cvtColor(input, hsv, Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV);

            // Define lower and upper bounds for the hue range.
            // Note: OpenCV’s Hue range is typically 0..180.
            // Convert the slider values accordingly.
            int lowerHue = (int)(hue / 2.0 - hueWidth / 2.0);
            int upperHue = (int)(hue / 2.0 + hueWidth / 2.0);
            int lowerHueLimit = Math.max(0, lowerHue);
            int upperHueLimit = Math.min(180, upperHue);
            // Lower saturation and value thresholds.
            Scalar lowerb = new Scalar(lowerHueLimit, satThreshold, lumThreshold);
            Scalar upperb = new Scalar(upperHueLimit, 255, 255);

            Mat mask = new Mat();
            Core.inRange(hsv, lowerb, upperb, mask);
            if (lowerHue < 0 || upperHue > 180) {
                if (lowerHue < 0) {
                    lowerHueLimit = lowerHue + 180;
                    upperHueLimit = 180;
                } else {
                    lowerHueLimit = 0;
                    upperHueLimit = upperHue - 180;
                }
                lowerb = new Scalar(lowerHueLimit, satThreshold, lumThreshold);
                upperb = new Scalar(upperHueLimit, 255, 255);
                Mat mask2 = new Mat();
                Core.inRange(hsv, lowerb, upperb, mask2);
                Core.bitwise_or(mask, mask2, mask);
                mask2.release();
            }

            Mat output = Mat.zeros(input.size(), input.type());
            if (!filterOn) {
                input.copyTo(output);
            } if (includeMode) {
                input.copyTo(output, mask);
            } else {
                Core.bitwise_not(mask, mask);
                input.copyTo(output, mask);
            }
            input.release();
            mask.release();
            Bitmap filtered = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(output, filtered);
            output.release(); 
            return filtered;
        }

        @Override
        protected void onPostExecute(Bitmap resultBitmap) {
            if (surfaceHolder.getSurface() != null) {
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas(null);
                    synchronized (surfaceHolder) {
                        canvas.drawBitmap(resultBitmap, 0, 0, null);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
        }
    }

    // --- SurfaceHolder.Callback methods ---
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return; // Surface not ready
        }
        stopCameraPreview();
        startCamera(); // Restart preview after surface changes
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            stopCameraPreview();
            camera.release();
            camera = null;
        }
    }


    // Handle camera permission results.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                // Handle permission denial gracefully.
            }
        }
    }
}
