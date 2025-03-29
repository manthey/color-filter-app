package com.orbitals.colorfilter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages UI components and their interactions.
 * This class centralizes UI-related operations to improve testability.
 */
public class UIComponentManager {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.UIComponentManager";
    private final Activity activity;
    private final Map<Integer, View> viewCache = new HashMap<>();
    PopupMenu popupMenu = null;
    private final List<Button> hiddenButtons = new ArrayList<>();
    private boolean lastSampleMode = false;
    private boolean lastLightMode = false;

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

        void onLightChanged();
    }

    public UIComponentManager(Activity activity) {
        this.activity = activity;
        activity.getWindow().getDecorView().getRootView().getViewTreeObserver().addOnGlobalLayoutListener(
                this::adjustButtonVisibilityForScreenWidth);
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
        Button lightButton = getView(R.id.lightButton);
        Button overflowMenuButton = getView(R.id.overflowMenuButton);

        switchCameraButton.setOnClickListener(v -> listener.onCameraSwitch(false));
        filterButton.setOnClickListener(v -> listener.onFilterModeChanged());
        loadImageButton.setOnClickListener(v -> listener.onLoadImageRequested());
        bctButton.setOnClickListener(v -> listener.onTermMapChanged());
        settingsButton.setOnClickListener(v -> listener.onSettingsRequested());
        sampleModeButton.setOnClickListener(v -> listener.onSampleModeChanged());
        lightButton.setOnClickListener(v -> listener.onLightChanged());
        overflowMenuButton.setOnClickListener(this::showOverflowMenu);

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
            boolean sampleMode,
            boolean lightMode) {

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
        if (updateSeekBars || sampleMode != lastSampleMode) {
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
            hueSeekBar.setSelected(sampleMode);
            bctSeekBar.setSelected(sampleMode);
        }
        lastSampleMode = sampleMode;
        getView(R.id.sampleButton).setSelected(sampleMode);
        lastLightMode = lightMode;
        getView(R.id.lightButton).setSelected(lightMode);
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

    public void adjustButtonVisibilityForScreenWidth() {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;

        // Get all buttons in order of priority (least important first)
        List<Button> buttons = Arrays.asList(
                getView(R.id.settingsButton),      // Lowest priority
                getView(R.id.lightButton),
                getView(R.id.loadImageButton),
                getView(R.id.switchCameraButton),
                getView(R.id.bctButton),
                getView(R.id.sampleButton),
                getView(R.id.filterButton)
        );

        Button overflowMenuButton = getView(R.id.overflowMenuButton);
        int overflowButtonWidth = overflowMenuButton.getWidth();
        int totalButtonsWidth = overflowButtonWidth;

        boolean needsOverflowMenu = false;
        hiddenButtons.clear();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button button = buttons.get(i);
            int buttonWidth = button.getWidth();

            //Log.d(TAG, "Overflow Widths " + screenWidth + " " + buttonWidth + " " + overflowButtonWidth + " " + totalButtonsWidth + " " + needsOverflowMenu + " " + i);
            if ((totalButtonsWidth + buttonWidth <= screenWidth ||
                    (i == 0 && totalButtonsWidth + buttonWidth - overflowButtonWidth <= screenWidth)) &&
                    !needsOverflowMenu) {
                totalButtonsWidth += buttonWidth;
                button.setVisibility(View.VISIBLE);
            } else {
                needsOverflowMenu = true;
                hiddenButtons.add(button);
                button.setVisibility(View.GONE);
            }
        }
        overflowMenuButton.setVisibility(needsOverflowMenu ? View.VISIBLE : View.GONE);
    }

    private void showOverflowMenu(View anchor) {
        if (popupMenu != null) {
            popupMenu.dismiss();
        }
        popupMenu = new PopupMenu(activity, anchor);

        for (Button button : hiddenButtons) {
            int itemId = button.getId();
            MenuItem menuItem = popupMenu.getMenu().add(Menu.NONE, itemId, Menu.NONE, button.getContentDescription());
            Drawable icon = button.getCompoundDrawablesRelative()[0];
            if (itemId == R.id.sampleButton && lastSampleMode) {
                icon = AppCompatResources.getDrawable(button.getContext(), R.drawable.baseline_sample_selected_24);
            } else if (itemId == R.id.lightButton && lastLightMode) {
                icon = AppCompatResources.getDrawable(button.getContext(), R.drawable.baseline_flashlight_on_24);
            }
            if (icon != null) {
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                menuItem.setIcon(icon);
            }

            popupMenu.setOnMenuItemClickListener(item -> {
                View clickedButton = activity.findViewById(item.getItemId());
                if (clickedButton != null) {
                    clickedButton.performClick();
                }
                return true;
            });

            try {
                Field field = popupMenu.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuPopupHelper = field.get(popupMenu);
                if (menuPopupHelper != null) {
                    menuPopupHelper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class).invoke(menuPopupHelper, true);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set icons on popup menu");
            }
        }
        popupMenu.show();
    }

}
