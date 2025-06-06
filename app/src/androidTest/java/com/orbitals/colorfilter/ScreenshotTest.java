package com.orbitals.colorfilter;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScreenshotTest {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.ScreenshotTest";
    private static final int LAUNCH_TIMEOUT = 5000;

    private MainActivity mainActivity;
    private UiDevice device;

    @Before
    public void setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("pm grant " + PACKAGE_NAME + " android.permission.CAMERA");
        }
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        device.pressHome();

        // Wait for launcher
        final String launcherPackage = TestUtils.getLauncherPackageName();
        assertNotNull(launcherPackage);
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // Initialize Espresso Intents
        Intents.init();
        // Launch the app
        Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(PACKAGE_NAME);
        assert intent != null;
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT);
        TestUtils.handlePermissionDialogs(device);

        // Get the MainActivity
        Intent maIntent = new Intent(context, MainActivity.class);
        maIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mainActivity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(maIntent);
    }

    @After
    public void tearDown() {
        if (mainActivity != null) {
            mainActivity.finish();
        }
        Intents.release();
    }

    @Test
    public void testVideoStreamScreenshot() {
        TestUtils.sleep(2.0);
        TestUtils.captureScreenshot(device, mainActivity, "video_stream.png");
    }

    @Test
    public void testImageModeScreenshot() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_mode);
        TestUtils.captureScreenshot(device, mainActivity, "image_mode_screenshot.png");
    }

    @Test
    public void testSettingsPage() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_mode);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Settings")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "setDefaultsButton")), 2000).click();
        TestUtils.captureScreenshot(device, mainActivity, "settings_page_screenshot.png");
    }

    @Test
    public void testExcludeModePortrait() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_peppers);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctSeekBar")), 2000);
        TestUtils.clickSeek(device, "bctSeekBar", 4.4 / 19.0);
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "exclude_mode_portrait.png");
    }

    @Test
    public void testIncludeModePortrait() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_peppers);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctSeekBar")), 2000);
        TestUtils.clickSeek(device, "bctSeekBar", 4.4 / 19.0);
        for (int i = 0; i < 4; i++) {
            device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        }
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "include_mode_portrait.png");
    }

    @Test
    public void testOffModeSamplingPortrait() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_bananas);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        for (int i = 0; i < 3; i++) {
            device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        }
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "sampling_portrait.png");
    }

    @Test
    public void testHSVIncludeModeSamplingPortrait() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_bananas);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "hsv_portrait.png");
    }

    @Test
    public void testOffModeSamplingLandscape() {
        try {
            device.setOrientationLeft();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_flower);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        for (int i = 0; i < 3; i++) {
            device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        }
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "sampling_landscape.png");
        try {
            device.setOrientationNatural();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
    }

    @Test
    public void testExcludeModeSamplingLandscape() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_flower);
        TestUtils.sleep(0.5);
        try {
            device.setOrientationLeft();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "sampling_exclude_mode_landscape.png");
        try {
            device.setOrientationNatural();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
    }

    @Test
    public void testBinaryModeLandscape() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_vegetation);
        TestUtils.sleep(0.5);
        try {
            device.setOrientationLeft();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        TestUtils.clickSeek(device, "bctSeekBar", 4.4 / 19.0);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "sampling_binary_mode_landscape.png");
        try {
            device.setOrientationNatural();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
    }

    @Test
    public void testSaturationModeLandscape() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_vegetation);
        TestUtils.sleep(0.5);
        try {
            device.setOrientationLeft();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        TestUtils.clickSeek(device, "bctSeekBar", 4.4 / 19.0);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        TestUtils.sleep(0.5);
        TestUtils.captureScreenshot(device, mainActivity, "sampling_saturation_mode_landscape.png");
        try {
            device.setOrientationNatural();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
    }
}
