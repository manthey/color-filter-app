package com.orbitals.colorfilter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
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
import androidx.exifinterface.media.ExifInterface;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "Color Filter";
    public static final int REQUEST_CAMERA_PERMISSION = 200;

    private HashMap<Integer, String> coarseHueMap;
    private HashMap<Integer, String> fineHueMap;

    ArrayList<TermMap> termMaps = new ArrayList<>();

    private ImageFilterProcessor filter;
    private CameraController cameraController;

    private TextureView textureView;

    private HandlerThread backgroundThread;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    private Bitmap loadedImage = null;
    private Bitmap processedImage = null;
    private boolean isImageMode = false;
    private final PointF lastTouch = new PointF();
    private final Matrix imageMatrix = new Matrix();
    private final RectF imageBounds = new RectF();
    private final float[] matrixValues = new float[9];

    // Pinch to zoom variables
    private float mLastTouchDistance = -1f;

    static {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "Unable to load OpenCV");
        } else {
            Log.d(TAG, "OpenCV loaded successfully");
        }
    }


    @SuppressLint("ClickableViewAccessibility")
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
        filter.setFilterSettings(0, 14, 0, 0, 1, ImageFilterProcessor.FilterMode.EXCLUDE, termMaps.get(0));
        loadSavedSettings();

        cameraController = new CameraController(this, textureView, this::checkCameraPermissions, filter);

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
                processedImage = null;
                cameraController.openCamera();
            } else {
                cameraController.switchCamera();
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
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null && data.getData() != null) {
                    Uri imageUri = data.getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
                        loadedImage = BitmapFactory.decodeStream(inputStream, null, options);
                        int orientation = getOrientation(imageUri);
                        if (orientation != 0) {
                            Matrix matrix = new Matrix();
                            matrix.postRotate(orientation);
                            loadedImage = Bitmap.createBitmap(loadedImage, 0, 0, loadedImage.getWidth(), loadedImage.getHeight(), matrix, true);
                        }
                        isImageMode = true;
                        setupImageMatrix();
                        cameraController.closeCamera();
                        displayLoadedImage();
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading image", e);
                        Toast.makeText(this, getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
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
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            // Pass the current filter settings to SettingsActivity
            intent.putExtra("filterMode", filter.getFilterMode().ordinal());
            intent.putExtra("hue", filter.getHue());
            intent.putExtra("hueWidth", filter.getHueWidth());
            intent.putExtra("satThreshold", filter.getSatThreshold());
            intent.putExtra("lumThreshold", filter.getLumThreshold());
            intent.putExtra("term", filter.getTerm());
            intent.putExtra("termMapId", filter.getTermMap() != null ? filter.getTermMap().getId() : null);
            settingsLauncher.launch(intent);
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
        settingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getBooleanExtra("defaultsLoaded", false)) {
                            loadSavedSettings();
                            updateControls();
                        } else if (data != null && data.getBooleanExtra("settingsChanged", false)) {
                            loadSavedSettings(false);
                            updateControls();
                        }
                    }
                }
        );

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
            findViewById(R.id.satLumControls).setVisibility(View.VISIBLE);
        } else {
            bctButton.setText(filter.getTermMap().getName());
            findViewById(R.id.hueControls).setVisibility(View.GONE);
            findViewById(R.id.bctControls).setVisibility(View.VISIBLE);
            bctSeekBar.setMax(filter.getTermMap().getTerms().size() - 1);
            findViewById(R.id.satLumControls).setVisibility(filter.getUseLumSatBCT() ? View.VISIBLE : View.GONE);
        }
        if (isImageMode) {
            displayLoadedImage();
        }
        updateSeekLabels();
    }

    @SuppressLint("DefaultLocale")
    private void updateSeekLabels() {
        TextView hueLabel = findViewById(R.id.hueLabel);
        hueLabel.setText(String.format("%s - %d - %s - %s", getString(R.string.hue), filter.getHue(), getColorName(filter.getHue(), coarseHueMap), getColorName(filter.getHue(), fineHueMap)));
        TextView hwLabel = findViewById(R.id.hueWidthLabel);
        hwLabel.setText(String.format("%s - %d", getString(R.string.hue_width), filter.getHueWidth()));
        TextView satLabel = findViewById(R.id.saturationLabel);
        satLabel.setText(String.format("%s - %d", getString(R.string.saturation), filter.getSatThreshold()));
        TextView lumLabel = findViewById(R.id.luminanceLabel);
        lumLabel.setText(String.format("%s - %d", getString(R.string.luminance), filter.getLumThreshold()));
        TextView bctLabel = findViewById(R.id.bctLabel);
        bctLabel.setText(String.format("%s - %s", getString(R.string.term), filter.getCurrentTerm()));
        if (isImageMode) {
            displayLoadedImage();
        }
    }

    private void setHueMaps() {
        coarseHueMap = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            coarseHueMap.put(i * 30, getResources().getStringArray(R.array.coarse_hue_map)[i]);
        }

        fineHueMap = new HashMap<>();
        for (int i = 0; i <= 24; i++) {
            fineHueMap.put(Math.max(0, i * 15 - 7), getResources().getStringArray(R.array.fine_hue_map)[i == 24 ? 0 : i]);
        }
    }

    private void loadTermMaps() {
        int NAME = 0;
        int ID = 1;
        int DESCRIPTION = 2;
        int REFERENCE = 3;
        int TERMS = 4;
        int IMAGE = 5;

        Resources resources = getResources();
        try (TypedArray termMapIds = resources.obtainTypedArray(R.array.term_map_ids)) {
            for (int i = 0; i < termMapIds.length(); i++) {
                int termMapId = termMapIds.getResourceId(i, 0);
                try (TypedArray termMapArray = resources.obtainTypedArray(termMapId)) {
                    String name = termMapArray.getString(NAME);
                    String id = termMapArray.getString(ID);
                    String description = termMapArray.getString(DESCRIPTION);
                    String reference = termMapArray.getString(REFERENCE);
                    int termsArrayId = termMapArray.getResourceId(TERMS, 0);
                    List<String> terms = Collections.unmodifiableList(Arrays.asList(resources.getStringArray(termsArrayId)));
                    int termMapResourceId = termMapArray.getResourceId(IMAGE, 0);
                    termMaps.add(new TermMap(name, id, description, reference, terms, resources, termMapResourceId));
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
                cameraController.adjustZoom(currentDistance / mLastTouchDistance);
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

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (!isImageMode) {
            cameraController.openCamera();
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

    private abstract static class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        startBackgroundThread();
        if (!isImageMode) {
            if (textureView.isAvailable()) {
                cameraController.openCamera();
            } else {
                textureView.setSurfaceTextureListener(this);
            }
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        cameraController.closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    protected void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background");
        backgroundThread.start();
        cameraController.setBackgroundHandler(new Handler(backgroundThread.getLooper()));
    }

    protected void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                cameraController.setBackgroundHandler(null);
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
        } else {
            cameraController.reopenCamera();
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
        displayLoadedImage(false);
    }

    private void displayLoadedImage(Boolean reuse) {
        if (loadedImage == null || !isImageMode) return;

        if (processedImage == null || !reuse) {
            Mat inputMat = new Mat();
            Utils.bitmapToMat(loadedImage, inputMat);
            Mat processedMat = filter.process(inputMat);

            processedImage = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(processedMat, processedImage);

            inputMat.release();
            processedMat.release();
        }

        Canvas canvas = textureView.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(Color.BLACK); // Clear the canvas
            canvas.drawBitmap(processedImage, imageMatrix, null); // Draw the image
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
                        float minScale = Math.min(textureView.getWidth() / loadedImage.getWidth(), textureView.getHeight() / loadedImage.getHeight());
                        float maxScale = Math.max(2.0f, Math.max(textureView.getWidth() / (float) loadedImage.getWidth(), textureView.getHeight() / (float) loadedImage.getHeight()) * 2);

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
                constrainImage();
                displayLoadedImage(true);
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
        float minScale = Math.min((float) textureView.getWidth() / loadedImage.getWidth(), (float) textureView.getHeight() / loadedImage.getHeight());
        imageMatrix.getValues(matrixValues);
        float currentScale = matrixValues[Matrix.MSCALE_X];
        if (currentScale < minScale) {
            imageMatrix.postScale(minScale / currentScale, minScale / currentScale, textureView.getWidth() / 2f, textureView.getHeight() / 2f);
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

    private int getOrientation(Uri photoUri) {
        try {
            InputStream stream = getContentResolver().openInputStream(photoUri);
            if (stream == null) {
                return 0;
            }
            ExifInterface ei = new ExifInterface(stream);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public Boolean checkCameraPermissions() {
        // Add permission for camera and let user grant the permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return true;
        }
        return false;
    }

    private void loadSavedSettings() {
        loadSavedSettings(true);
    }

    private void loadSavedSettings(Boolean includeDefaults) {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);

        if (includeDefaults) {
            int curTerm = filter.getTerm();
            int filterModeOrdinal = prefs.getInt(SettingsActivity.KEY_FILTER_MODE, ImageFilterProcessor.FilterMode.EXCLUDE.ordinal());
            filter.setFilterMode(ImageFilterProcessor.FilterMode.values()[filterModeOrdinal]);
            filter.setHue(prefs.getInt(SettingsActivity.KEY_HUE, filter.getHue()));
            filter.setHueWidth(prefs.getInt(SettingsActivity.KEY_HUE_WIDTH, filter.getHueWidth()));
            filter.setSatThreshold(prefs.getInt(SettingsActivity.KEY_SAT_THRESHOLD, filter.getSatThreshold()));
            filter.setLumThreshold(prefs.getInt(SettingsActivity.KEY_LUM_THRESHOLD, filter.getLumThreshold()));
            String termMapId = prefs.getString(SettingsActivity.KEY_TERM_MAP, filter.getTermMap() == null ? null : filter.getTermMap().getId());
            TermMap settingsTermMap = null;
            if (termMapId != null) {
                for (TermMap map : termMaps) {
                    if (map.getId().equals(termMapId)) {
                        settingsTermMap = map;
                        break;
                    }
                }
            }
            filter.setTermMap(settingsTermMap);
            filter.setTerm(prefs.getInt(SettingsActivity.KEY_TERM, curTerm));
        }
        filter.setUseLumSatBCT(prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, filter.getUseLumSatBCT()));
    }

}