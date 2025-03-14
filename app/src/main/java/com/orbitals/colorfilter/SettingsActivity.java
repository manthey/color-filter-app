package com.orbitals.colorfilter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {
    /** @noinspection SpellCheckingInspection*/
    private static final String TAG = "com.orbitals.colorfilter.SettingsActivity";
    public static final String PREFS_NAME = "ColorFilterPrefs";
    public static final String KEY_FILTER_MODE = "filter_mode";
    public static final String KEY_TERM_MAP = "term_map";
    public static final String KEY_HUE = "hue";
    public static final String KEY_HUE_WIDTH = "hue_width";
    public static final String KEY_SAT_THRESHOLD = "sat_threshold";
    public static final String KEY_LUM_THRESHOLD = "lum_threshold";
    public static final String KEY_TERM = "term";
    public static final String KEY_SHOW_BCT_CONTROLS = "show_bct_controls";

    private Button setDefaultsButton;
    private boolean showBctControls = false;

    private int currentFilterMode;
    private int currentHue;
    private int currentHueWidth;
    private int currentSatThreshold;
    private int currentLumThreshold;
    private int currentTerm;
    private String currentTermMapId;

    private boolean settingsChanged = false;
    private boolean defaultsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Intent intent = getIntent();
        currentFilterMode = intent.getIntExtra("filterMode", 0);
        currentHue = intent.getIntExtra("hue", 0);
        currentHueWidth = intent.getIntExtra("hueWidth", 14);
        currentSatThreshold = intent.getIntExtra("satThreshold", 0);
        currentLumThreshold = intent.getIntExtra("lumThreshold", 0);
        currentTerm = intent.getIntExtra("term", 0);
        currentTermMapId = intent.getStringExtra("termMapId");

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.settings_title);
        }

        TextView versionTextView = findViewById(R.id.versionTextView);
        String versionName = "";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Error getting package info: " + e.getMessage());
        }
        versionTextView.setText(getString(R.string.version_format, versionName));

        SwitchCompat bctControlsSwitch = findViewById(R.id.bctControlsSwitch);
        loadSettings();
        bctControlsSwitch.setChecked(showBctControls);
        bctControlsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showBctControls = isChecked;
            saveSettings();
            settingsChanged = true;
        });

        setDefaultsButton = findViewById(R.id.setDefaultsButton);
        updateSetDefaultsButton();
        setDefaultsButton.setOnClickListener(v -> {
            saveDefaultSettings();
            setDefaultsButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.baseline_check_24, 0);
            Toast.makeText(SettingsActivity.this,
                    getString(R.string.defaults_saved), Toast.LENGTH_SHORT).show();
        });

        Button loadDefaultsButton = findViewById(R.id.loadDefaultsButton);
        loadDefaultsButton.setOnClickListener(v -> {
            loadDefaultSettings();
            defaultsLoaded = true;
            loadDefaultsButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.baseline_check_24, 0);
            Toast.makeText(SettingsActivity.this,
                    getString(R.string.defaults_loaded), Toast.LENGTH_SHORT).show();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Your existing onBackPressed logic
                Intent resultIntent = new Intent();
                resultIntent.putExtra("settingsChanged", settingsChanged);
                resultIntent.putExtra("defaultsLoaded", defaultsLoaded);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        showBctControls = prefs.getBoolean(KEY_SHOW_BCT_CONTROLS, false);
    }

    private void loadDefaultSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int filterModeOrdinal = prefs.getInt(KEY_FILTER_MODE, ImageFilterProcessor.FilterMode.EXCLUDE.ordinal());
        int hue = prefs.getInt(KEY_HUE, 0);
        int hueWidth = prefs.getInt(KEY_HUE_WIDTH, 14);
        int satThreshold = prefs.getInt(KEY_SAT_THRESHOLD, 0);
        int lumThreshold = prefs.getInt(KEY_LUM_THRESHOLD, 0);
        int term = prefs.getInt(KEY_TERM, 1);
        String termMapId = prefs.getString(KEY_TERM_MAP, null);

        currentFilterMode = filterModeOrdinal;
        currentHue = hue;
        currentHueWidth = hueWidth;
        currentSatThreshold = satThreshold;
        currentLumThreshold = lumThreshold;
        currentTerm = term;
        currentTermMapId = termMapId;
    }

    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_SHOW_BCT_CONTROLS, showBctControls);
        editor.apply();
    }

    private void saveDefaultSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(KEY_FILTER_MODE, currentFilterMode);
        editor.putInt(KEY_HUE, currentHue);
        editor.putInt(KEY_HUE_WIDTH, currentHueWidth);
        editor.putInt(KEY_SAT_THRESHOLD, currentSatThreshold);
        editor.putInt(KEY_LUM_THRESHOLD, currentLumThreshold);
        editor.putInt(KEY_TERM, currentTerm);
        editor.putString(KEY_TERM_MAP, currentTermMapId);

        editor.apply();
    }

    private void updateSetDefaultsButton() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean isDefault = prefs.getInt(KEY_FILTER_MODE, -1) == currentFilterMode &&
                prefs.getInt(KEY_HUE, -1) == currentHue &&
                prefs.getInt(KEY_HUE_WIDTH, -1) == currentHueWidth &&
                prefs.getInt(KEY_SAT_THRESHOLD, -1) == currentSatThreshold &&
                prefs.getInt(KEY_LUM_THRESHOLD, -1) == currentLumThreshold &&
                prefs.getInt(KEY_TERM, -1) == currentTerm;

        String savedTermMapId = prefs.getString(KEY_TERM_MAP, null);
        boolean termMapMatches = (savedTermMapId == null && currentTermMapId == null) ||
                (currentTermMapId != null && Objects.equals(currentTermMapId, savedTermMapId));

        isDefault = isDefault && termMapMatches;

        if (isDefault) {
            setDefaultsButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.baseline_check_24, 0);
        } else {
            setDefaultsButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
