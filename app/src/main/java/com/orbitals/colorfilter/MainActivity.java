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
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.opencv.android.OpenCVLoader;

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

    private FilterProcessor filter;
    private CameraController cameraController;
    private ImageController imageController;
    private UIComponentManager uiManager;

    private TextureView textureView;

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> settingsLauncher;

    private boolean isImageMode = false;
    private final PointF lastTouch = new PointF();

    // Pinch to zoom and swipe variables
    private float mLastTouchDistance = -1f;
    private Float swipeStartX = null;
    private Float swipeStartY = null;
    private static final float SWIPE_THRESHOLD = 100; // Minimum distance for swipe

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
        super.onCreate(savedInstanceState);
        setHueMaps();
        loadTermMaps();
        setContentView(R.layout.activity_main);
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);
        uiManager = new UIComponentManager(this);

        filter = new FilterProcessor();
        // defaults
        filter.setFilterSettings(0, 14, 100, 100, 1, FilterProcessor.FilterMode.EXCLUDE, termMaps.get(0));
        filter.setUseLumSatBCT(false);
        loadSavedSettings();

        cameraController = new CameraController(this, textureView, this::checkCameraPermissions, filter, this::updateControls);
        imageController = new ImageController(this, textureView, filter, this::updateControls);

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Bitmap newImage = imageController.handleImagePickerResult(result);
            if (newImage != null) {
                isImageMode = true;
                cameraController.closeCamera();
            }
        });

        updateControls();

        textureView = uiManager.getView(R.id.textureView);

        uiManager.initializeControls(
                new UIComponentManager.FilterControlListener() {
                    @Override
                    public void onFilterModeChanged() {
                        switch (filter.getFilterMode()) {
                            case NONE:
                                filter.setFilterMode(FilterProcessor.FilterMode.INCLUDE);
                                break;
                            case INCLUDE:
                                filter.setFilterMode(FilterProcessor.FilterMode.EXCLUDE);
                                break;
                            case EXCLUDE:
                                filter.setFilterMode(FilterProcessor.FilterMode.BINARY);
                                break;
                            case BINARY:
                                filter.setFilterMode(FilterProcessor.FilterMode.SATURATION);
                                break;
                            case SATURATION:
                                filter.setFilterMode(FilterProcessor.FilterMode.NONE);
                                break;
                        }
                        updateControls();
                    }

                    @Override
                    public void onTermMapChanged() {
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
                    }

                    @Override
                    public void onHueChanged(int value) {
                        filter.setHue(value);
                        updateSeekLabels();
                    }

                    @Override
                    public void onHueWidthChanged(int value) {
                        filter.setHueWidth(value);
                        updateSeekLabels();
                    }

                    @Override
                    public void onSaturationChanged(int value) {
                        filter.setSatThreshold(value);
                        updateSeekLabels();
                    }

                    @Override
                    public void onLuminanceChanged(int value) {
                        filter.setLumThreshold(value);
                        updateSeekLabels();
                    }

                    @Override
                    public void onTermChanged(int value) {
                        filter.setTerm(value);
                        updateSeekLabels();
                    }

                    @Override
                    public void onCameraSwitch(boolean isImageModeRequested) {
                        if (isImageMode) {
                            isImageMode = false;
                            imageController.clearImage();
                            cameraController.openCamera();
                        } else {
                            cameraController.switchCamera();
                        }
                    }

                    @Override
                    public void onLoadImageRequested() {
                        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        pickImageLauncher.launch(gallery);
                    }

                    @Override
                    public void onSettingsRequested() {
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        // Pass the current filter settings to SettingsActivity
                        intent.putExtra("filterMode", filter.getFilterMode().ordinal());
                        intent.putExtra("hue", filter.getHue());
                        intent.putExtra("hueWidth", filter.getHueWidth());
                        intent.putExtra("satThreshold", filter.getSatThreshold());
                        intent.putExtra("lumThreshold", filter.getLumThreshold());
                        intent.putExtra("term", filter.getTerm());
                        intent.putExtra("termMapId", filter.getTermMap() != null ? filter.getTermMap().getId() : null);
                        intent.putExtra("sampleMode", filter.getSampleMode());
                        settingsLauncher.launch(intent);
                    }

                    @Override
                    public void onSampleModeChanged() {
                        filter.setSampleMode(!filter.getSampleMode());
                        updateControls();
                    }

                    @Override
                    public void onLightChanged() {
                        cameraController.setLightMode(!cameraController.getLightMode());
                        updateControls();
                    }
                },
                filter.getHue(),
                filter.getHueWidth(),
                filter.getSatThreshold(),
                filter.getLumThreshold(),
                filter.getTerm()
        );

        // Pinch-to-zoom setup (add touch listener)
        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            if (isImageMode) {
                return handleImageTouch(event);
            } else {
                return handleCameraTouch(event);
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

    private void updateSeekLabels() {
        updateControls(false);
    }

    public void updateControls() {
        updateControls(true);
    }

    private void updateControls(boolean updateSeekBars) {
        uiManager.updateUI(
                filter.getFilterMode(),
                filter.getHue(),
                filter.getHueWidth(),
                filter.getSatThreshold(),
                filter.getLumThreshold(),
                filter.getTermMap(),
                filter.getUseLumSatBCT(),
                coarseHueMap,
                fineHueMap,
                filter.getCurrentTerm(),
                filter.getTerm(),
                updateSeekBars,
                filter.getSampleMode(),
                cameraController.getLightMode()
        );

        if (isImageMode) {
            imageController.displayLoadedImage();
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

    private boolean handleCameraTouch(MotionEvent event) {
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        switch (event.getPointerCount()) {
            case 1:
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        swipeStartX = event.getX();
                        swipeStartY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (swipeStartY != null && swipeStartX != null) {
                            float swipeDistanceX = event.getX() - swipeStartX;
                            float swipeDistanceY = event.getY() - swipeStartY;
                            if (Math.abs(swipeDistanceY) > SWIPE_THRESHOLD &&
                                    Math.abs(swipeDistanceY) > Math.abs(swipeDistanceX) * 2) {
                                // Detected a vertical swipe
                                cameraController.switchCamera();
                                return true;
                            }
                        }
                        break;
                }
                break;
            case 2:
                swipeStartY = null;
                // Two-finger touch (pinch / spread)
                float currentDistance = getDistance(event);
                if (mLastTouchDistance != -1f) {
                    cameraController.adjustZoom(currentDistance / mLastTouchDistance);
                }
                mLastTouchDistance = currentDistance;
                return true; // Consume the event
            default:
                swipeStartY = null;
                mLastTouchDistance = -1f;
                break;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            mLastTouchDistance = -1f;
            swipeStartY = null;
        }

        return false;
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        cameraController.startBackgroundThread();
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
        cameraController.stopBackgroundThread();
        super.onPause();
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
            imageController.onConfigurationChanged();
        } else {
            cameraController.reopenCamera();
        }
        uiManager.adjustButtonVisibilityForScreenWidth();
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
                        float centerX = (event.getX(0) + event.getX(1)) / 2;
                        float centerY = (event.getY(0) + event.getY(1)) / 2;
                        imageController.scaleMatrix(newDistance / mLastTouchDistance, centerX, centerY);
                    }
                    mLastTouchDistance = newDistance;
                    lastTouch.set(-1, -1);
                } else if (event.getPointerCount() == 1) {
                    if (lastTouch.x >= 0 && lastTouch.y >= 0) {
                        // Handle pan
                        float dx = event.getX() - lastTouch.x;
                        float dy = event.getY() - lastTouch.y;
                        imageController.translateMatrix(dx, dy);

                    }
                    lastTouch.set(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mLastTouchDistance = -1;
                lastTouch.set(-1, -1);
                return true;
        }
        return false;
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
            int filterModeOrdinal = prefs.getInt(SettingsActivity.KEY_FILTER_MODE, FilterProcessor.FilterMode.EXCLUDE.ordinal());
            filter.setFilterMode(FilterProcessor.FilterMode.values()[filterModeOrdinal]);
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
            filter.setSampleMode(prefs.getBoolean(SettingsActivity.KEY_SAMPLE_MODE, filter.getSampleMode()));
        }
        filter.setUseLumSatBCT(prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, filter.getUseLumSatBCT()));
    }
}