package com.orbitals.colorfilter;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.regex.Pattern;

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
    public void testCameraAndFilterButtons() {
        // Wait for the app to fully load
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT);

        handlePermissionDialogs();

        UiObject2 cameraButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000);
        cameraButton.click();

        // Wait after clicking
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);

        UiObject2 filterButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000);
        filterButton.click();
        Log.d(TAG, "Clicked filter button");
    }

    private void handlePermissionDialogs() {
        // Handle permission dialogs that might appear
        for (int i = 0; i < 3; i++) { // Try a few times for multiple permission dialogs
            // Try by pattern matching "WHILE" (case insensitive)
            UiObject2 permissionButton = device.wait(
                    Until.findObject(By.text(Pattern.compile(".*WHILE.*", Pattern.CASE_INSENSITIVE))),
                    2000);

            if (permissionButton == null) {
                // If not found by text, try by resource ID containing "allow_foreground"
                permissionButton = device.wait(
                        Until.findObject(By.res(Pattern.compile(".*allow_foreground.*"))),
                        1000);
            }

            if (permissionButton != null) {
                permissionButton.click();
                Log.d(TAG, "Clicked permission button matching WHILE or allow_foreground");
                // Wait for dialog to disappear
                device.wait(Until.gone(By.text(Pattern.compile(".*WHILE.*", Pattern.CASE_INSENSITIVE))), 1000);
                continue;
            }

            // Try other common permission buttons
            String[] commonButtonTexts = {"ONLY THIS TIME", "ALLOW", "Allow", "YES", "OK"};
            boolean buttonClicked = false;

            for (String buttonText : commonButtonTexts) {
                UiObject2 otherButton = device.wait(
                        Until.findObject(By.text(buttonText)),
                        500);  // Short timeout for each

                if (otherButton != null) {
                    otherButton.click();
                    Log.d(TAG, "Clicked permission button: " + buttonText);
                    device.wait(Until.gone(By.text(buttonText)), 1000);
                    buttonClicked = true;
                    break;
                }
            }

            if (buttonClicked) {
                continue;
            }

            // If we've handled all dialogs or none found, break
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
        return Objects.requireNonNull(resolveInfo).activityInfo.packageName;
    }
}