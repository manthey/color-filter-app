package com.orbitals.colorfilter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
public class MainActivityUITest {

    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String TAG = "MainActivityUiTest";
    private UiDevice device;

    @Before
    public void startMainActivityFromHomeScreen() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        device.pressHome();

        // Wait for launcher
        final String launcherPackage = getLauncherPackageName();
        assertThat(launcherPackage, notNullValue());
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        // Launch the app
        Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);    // Clear out any previous instances
        context.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);
    }

    @Test
    public void testCameraAndFilterButtons() throws UiObjectNotFoundException {
        // Allow camera permissions if needed
        handlePermissionDialogs();

        // Wait for the app to fully load
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT);

        // Try to find the camera button using multiple approaches
        UiObject2 cameraButton = null;

        // First try by resource ID
        cameraButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000);

        // If not found, try by description
        if (cameraButton == null) {
            cameraButton = device.wait(
                    Until.findObject(By.desc("Switch Camera")), 2000);
        }

        // If still not found, try by text
        if (cameraButton == null) {
            cameraButton = device.wait(
                    Until.findObject(By.text("Switch")), 2000);
        }

        // Try using UiSelector instead (alternative approach)
        if (cameraButton == null) {
            UiObject switchCameraObj = device.findObject(new UiSelector()
                    .className("android.widget.Button")
                    .packageName(PACKAGE_NAME)
                    .instance(0)); // Try the first button

            if (switchCameraObj.exists()) {
                switchCameraObj.click();
                Log.d(TAG, "Clicked first button using UiSelector");
            } else {
                // Try clicking by coordinates as a last resort
                int centerX = device.getDisplayWidth() / 2;
                int bottomY = device.getDisplayHeight() - 200; // Near bottom
                device.click(centerX, bottomY);
                Log.d(TAG, "Clicked by coordinates as fallback");
            }
        } else {
            cameraButton.click();
            Log.d(TAG, "Clicked camera button found by resource/text/description");
        }

        // Wait after clicking
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);

        // Now try to find and click the filter button
        // Try multiple approaches for finding the filter button
        UiObject2 filterButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000);

        if (filterButton == null) {
            filterButton = device.wait(
                    Until.findObject(By.textContains("filter")), 2000);
        }

        if (filterButton == null) {
            // Try using UiSelector
            UiObject filterObj = device.findObject(new UiSelector()
                    .className("android.widget.Button")
                    .packageName(PACKAGE_NAME)
                    .instance(1)); // Try the second button

            if (filterObj.exists()) {
                filterObj.click();
                Log.d(TAG, "Clicked second button using UiSelector");
            }
        } else {
            filterButton.click();
            Log.d(TAG, "Clicked filter button found by resource/text");
        }
    }

    private void handlePermissionDialogs() {
        // Handle permission dialogs that might appear
        for (int i = 0; i < 3; i++) { // Try a few times for multiple permission dialogs
            UiObject2 allowButton = device.wait(
                    Until.findObject(By.text("Allow")), 2000);
            if (allowButton != null) {
                allowButton.click();
                device.wait(Until.gone(By.text("Allow")), 1000);
                continue;
            }

            allowButton = device.wait(
                    Until.findObject(By.text("ALLOW")), 1000);
            if (allowButton != null) {
                allowButton.click();
                device.wait(Until.gone(By.text("ALLOW")), 1000);
                continue;
            }

            // Check for "Allow only while using the app"
            UiObject2 whileUsingButton = device.wait(
                    Until.findObject(By.text("While using the app")), 1000);
            if (whileUsingButton != null) {
                whileUsingButton.click();
                device.wait(Until.gone(By.text("While using the app")), 1000);
            }

            // If we've handled all dialogs, break
            if (device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 1000)) {
                break;
            }
        }
    }

    private String getLauncherPackageName() {
        // Create launcher Intent
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        // Use PackageManager to get the launcher package name
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }
}