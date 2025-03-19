package com.orbitals.colorfilter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages UI components and their interactions.
 * This class centralizes UI-related operations to improve testability.
 */
public class UIComponentManager {
    private final Activity activity;
    private final Map<Integer, View> viewCache = new HashMap<>();

    // Listener interface for filter control events
    public interface FilterControlListener {
        void onFilterModeChanged();

        void onTermMapChanged();

        void onHueChanged(int value);

        void onHueWidthChanged(int value);

        void onSaturationChanged(int value);

        void onLuminanceChanged(int value);

        void onTermChanged(int value);

        void onCameraSwitch(boolean isImageMode);

        void onLoadImageRequested();

        void onSettingsRequested();

        void onSampleModeChanged();
    }

    public UIComponentManager(Activity activity) {
        this.activity = activity;
    }

    /**
     * Get a cached view by ID.
     *
     * @param id The resource ID of the view
     * @return The view
     */
    @SuppressWarnings("unchecked")
    public <T extends View> T getView(int id) {
        if (!viewCache.containsKey(id)) {
            viewCache.put(id, activity.findViewById(id));
        }
        return (T) viewCache.get(id);
    }

    /**
     * Initialize all UI components and set up their listeners.
     *
     * @param listener            The listener to handle UI events
     * @param initialHue          The initial hue value
     * @param initialHueWidth     The initial hue width value
     * @param initialSatThreshold The initial saturation threshold
     * @param initialLumThreshold The initial luminance threshold
     * @param initialTerm         The initial term value
     */
    public void initializeControls(
            FilterControlListener listener,
            int initialHue,
            int initialHueWidth,
            int initialSatThreshold,
            int initialLumThreshold,
            int initialTerm) {

        // Initialize all seekbars with their initial values
        SeekBar hueSeekBar = getView(R.id.hueSeekBar);
        SeekBar hueWidthSeekBar = getView(R.id.hueWidthSeekBar);
        SeekBar saturationSeekBar = getView(R.id.saturationSeekBar);
        SeekBar luminanceSeekBar = getView(R.id.luminanceSeekBar);
        SeekBar bctSeekBar = getView(R.id.bctSeekBar);

        hueSeekBar.setProgress(initialHue);
        hueWidthSeekBar.setProgress(initialHueWidth);
        saturationSeekBar.setProgress(initialSatThreshold);
        luminanceSeekBar.setProgress(initialLumThreshold);
        bctSeekBar.setProgress(initialTerm);

        // Set up button listeners
        Button switchCameraButton = getView(R.id.switchCameraButton);
        Button filterButton = getView(R.id.filterButton);
        Button loadImageButton = getView(R.id.loadImageButton);
        Button bctButton = getView(R.id.bctButton);
        Button settingsButton = getView(R.id.settingsButton);
        Button sampleModeButton = getView(R.id.sampleButton);

        switchCameraButton.setOnClickListener(v -> listener.onCameraSwitch(false));
        filterButton.setOnClickListener(v -> listener.onFilterModeChanged());
        loadImageButton.setOnClickListener(v -> listener.onLoadImageRequested());
        bctButton.setOnClickListener(v -> listener.onTermMapChanged());
        settingsButton.setOnClickListener(v -> listener.onSettingsRequested());
        sampleModeButton.setOnClickListener(v -> listener.onSampleModeChanged());

        // Set up seekbar listeners
        hueSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Ensure hue is even number (as in original code)
                    int adjustedProgress = progress - (progress % 2);
                    listener.onHueChanged(adjustedProgress);
                }
            }
        });

        hueWidthSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Ensure hueWidth is even number (as in original code)
                    int adjustedProgress = progress - (progress % 2);
                    listener.onHueWidthChanged(adjustedProgress);
                }
            }
        });

        saturationSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    listener.onSaturationChanged(progress);
                }
            }
        });

        luminanceSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    listener.onLuminanceChanged(progress);
                }
            }
        });

        bctSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    listener.onTermChanged(progress);
                }
            }
        });
    }

    /**
     * Update the UI to reflect the current filter settings.
     *
     * @param filterMode   The current filter mode
     * @param hue          The current hue value
     * @param hueWidth     The current hue width value
     * @param satThreshold The current saturation threshold
     * @param lumThreshold The current luminance threshold
     * @param termMap      The current term map or null if not using term maps
     * @param useLumSatBCT Whether to show luminance and saturation controls with BCT
     * @param coarseHueMap The coarse hue name mapping
     * @param fineHueMap   The fine hue name mapping
     */
    public void updateUI(
            FilterProcessor.FilterMode filterMode,
            int hue,
            int hueWidth,
            int satThreshold,
            int lumThreshold,
            TermMap termMap,
            boolean useLumSatBCT,
            HashMap<Integer, String> coarseHueMap,
            HashMap<Integer, String> fineHueMap,
            String currentTerm,
            int term,
            boolean updateSeekBars,
            boolean sampleMode) {

        Button filterButton = getView(R.id.filterButton);
        switch (filterMode) {
            case INCLUDE:
                filterButton.setText(activity.getString(R.string.filter_button_include));
                break;
            case EXCLUDE:
                filterButton.setText(activity.getString(R.string.filter_button_exclude));
                break;
            case BINARY:
                filterButton.setText(activity.getString(R.string.filter_button_binary));
                break;
            case SATURATION:
                filterButton.setText(activity.getString(R.string.filter_button_saturation));
                break;
            case NONE:
                filterButton.setText(activity.getString(R.string.filter_button_off));
                break;
        }

        // Update term map button and controls visibility
        Button bctButton = getView(R.id.bctButton);
        View hueControls = getView(R.id.hueControls);
        View bctControls = getView(R.id.bctControls);
        View satLumControls = getView(R.id.satLumControls);

        if (termMap == null) {
            bctButton.setText(activity.getString(R.string.term_button_hsv));
            bctControls.setVisibility(View.GONE);
            hueControls.setVisibility(View.VISIBLE);
            satLumControls.setVisibility(View.VISIBLE);
        } else {
            bctButton.setText(termMap.getName());
            hueControls.setVisibility(View.GONE);
            bctControls.setVisibility(View.VISIBLE);
            ((SeekBar) getView(R.id.bctSeekBar)).setMax(termMap.getTerms().size() - 1);
            satLumControls.setVisibility(useLumSatBCT ? View.VISIBLE : View.GONE);
        }

        updateSeekLabels(hue, hueWidth, satThreshold, lumThreshold, currentTerm, coarseHueMap, fineHueMap);
        if (updateSeekBars) {
            SeekBar hueSeekBar = getView(R.id.hueSeekBar);
            SeekBar hueWidthSeekBar = getView(R.id.hueWidthSeekBar);
            SeekBar saturationSeekBar = getView(R.id.saturationSeekBar);
            SeekBar luminanceSeekBar = getView(R.id.luminanceSeekBar);
            SeekBar bctSeekBar = getView(R.id.bctSeekBar);

            hueSeekBar.setProgress(hue);
            hueWidthSeekBar.setProgress(hueWidth);
            saturationSeekBar.setProgress(satThreshold);
            luminanceSeekBar.setProgress(lumThreshold);
            bctSeekBar.setProgress(term);

        }
        Button sampleButton = getView(R.id.sampleButton);
        sampleButton.setSelected(sampleMode);
    }

    /**
     * Update the text labels for all seekbars.
     */
    @SuppressLint("DefaultLocale")
    private void updateSeekLabels(
            int hue,
            int hueWidth,
            int satThreshold,
            int lumThreshold,
            String currentTerm,
            HashMap<Integer, String> coarseHueMap,
            HashMap<Integer, String> fineHueMap) {

        TextView hueLabel = getView(R.id.hueLabel);
        String colorName = getColorName(hue, coarseHueMap);
        String fineColorName = getColorName(hue, fineHueMap);
        hueLabel.setText(String.format("%s - %d - %s - %s",
                activity.getString(R.string.hue), hue, colorName, fineColorName));

        TextView hwLabel = getView(R.id.hueWidthLabel);
        hwLabel.setText(String.format("%s - %d",
                activity.getString(R.string.hue_width), hueWidth));

        TextView satLabel = getView(R.id.saturationLabel);
        satLabel.setText(String.format("%s - %d",
                activity.getString(R.string.saturation), satThreshold));

        TextView lumLabel = getView(R.id.luminanceLabel);
        lumLabel.setText(String.format("%s - %d",
                activity.getString(R.string.luminance), lumThreshold));

        TextView bctLabel = getView(R.id.bctLabel);
        bctLabel.setText(String.format("%s - %s",
                activity.getString(R.string.term), currentTerm));
    }

    /**
     * Get the color name for a given hue value.
     */
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

    /**
     * Simple implementation of SeekBar.OnSeekBarChangeListener that only requires
     * overriding onProgressChanged.
     */
    public abstract static class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // No implementation needed
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // No implementation needed
        }
    }
}
