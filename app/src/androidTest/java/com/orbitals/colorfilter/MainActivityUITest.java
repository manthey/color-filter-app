package com.orbitals.colorfilter;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityUITest {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    private static final int LAUNCH_TIMEOUT = 5000;
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.MainActivityUITest";
    private UiDevice device;

    @Before
    public void startMainActivityFromHomeScreen() {
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
    }

    @After
    public void cleanup() {
        Intents.release();
    }

    @Test
    public void testCameraAndFilterButtons() {
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000).click();
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);
        for (int i = 0; i < 5; i++) {
            device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
            Log.d(TAG, "Clicked filter button");
        }
        clickSeek("bctSeekBar", 0.5);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("BCT11")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("HSV")), 2000);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "hueSeekBar")), 2000);
        clickSeek("hueSeekBar", 0.3);
        clickSeek("hueWidthSeekBar", 0.3);
        clickSeek("saturationSeekBar", 0.3);
        clickSeek("luminanceSeekBar", 0.3);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("BCT20")), 2000);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("BCT11")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("HSV")), 2000);
        UiObject2 textureView = device.wait(Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
        textureView.pinchOpen(0.20f);
        textureView.drag(new Point(0, 140));
    }

    @Test
    public void testImageModeFeatures() {
        TestUtils.loadImage(device, com.orbitals.colorfilter.test.R.drawable.test_image_mode);
        UiObject2 textureView = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
        textureView.pinchOpen(0.20f);
        textureView.drag(new Point(100, 0));
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctSeekBar")), 2000);
        clickSeek("bctSeekBar", 0.5);
        textureView = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
        textureView.pinchOpen(0.20f);
        textureView.drag(new Point(0, 140));
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000);
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "sampleButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("BCT11")), 2000).click();
        try {
            device.setOrientationLeft();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctButton").text("HSV")), 2000).click();
        TestUtils.sleep(0.5);
        try {
            device.setOrientationNatural();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set orientation");
        }
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000).click();
    }

    @Test
    public void testToggleFlashlight() {
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Flashlight")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Flashlight")), 2000).click();
    }

    @Test
    public void testSettingsFeatures() {
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Settings")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "setDefaultsButton")), 2000).click();
        device.pressBack();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Settings")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctControlsSwitch")), 2000).click();
        device.pressBack();
        UiObject2 textureView = device.wait(Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
        textureView.drag(new Point(0, 140));
        textureView.drag(new Point(0, 280));
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "overflowMenuButton")), 2000).click();
        device.wait(Until.findObject(By.text("Settings")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "bctControlsSwitch")), 2000).click();
        device.wait(Until.findObject(By.res(PACKAGE_NAME, "loadDefaultsButton")), 2000).click();
        device.pressBack();
    }

    private void clickSeek(String resourceId, double position) {
        TestUtils.clickSeek(device, resourceId, position);
    }
}
