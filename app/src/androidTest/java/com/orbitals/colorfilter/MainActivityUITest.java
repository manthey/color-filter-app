package com.orbitals.colorfilter;

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
        handlePermissionDialogs();
    }

    @After
    public void cleanup() {
        Intents.release();
    }

    @Test
    public void testCameraAndFilterButtons() {
        UiObject2 cameraButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000);
        cameraButton.click();

        // Wait after clicking
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);

        for (int i = 0; i < 5; i++) {
            UiObject2 filterButton = device.wait(
                    Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000);
            filterButton.click();
            Log.d(TAG, "Clicked filter button");
        }
    }

    @Test
    public void testImageModeFeatures() {
        // 1. Set up intent response for the gallery picker
        // Create a sample bitmap to return as the selected image
        //noinspection unused
        Bitmap sampleBitmap = BitmapFactory.decodeResource(
                ApplicationProvider.getApplicationContext().getResources(),
                android.R.drawable.ic_menu_report_image); // Using a system resource as sample

        // Create a result intent with the sample image
        Intent resultData = new Intent();
        resultData.setData(Uri.parse("content://media/external/images/media/123")); // Dummy URI

        // Set up the intent result
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(
                Activity.RESULT_OK, resultData);

        // Stub the intent
        intending(hasAction(Intent.ACTION_PICK)).respondWith(result);

        // 2. Click the load image button using UiAutomator
        UiObject2 loadImageButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "loadImageButton")), 2000);
        loadImageButton.click();

        // 3. Verify the gallery intent was sent
        intended(hasAction(Intent.ACTION_PICK));

        // 4. Wait for image to load
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);

        // 5. Test pinch-to-zoom on the image
        UiObject2 textureView = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);

        textureView.pinchOpen(0.20f);

        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);

        // 6. Test filter button in image mode
        UiObject2 filterButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000);
        filterButton.click();

        // Wait for filter to apply
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);

        UiObject2 bctSeekBar = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "bctSeekBar")), 2000);
        int width = bctSeekBar.getVisibleBounds().width();
        bctSeekBar.drag(new android.graphics.Point(width / 2, 0), 30);

        // Wait for the change to apply
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);

        UiObject2 cameraButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000);
        cameraButton.click();

        // Wait to confirm we're back to camera mode
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);
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
                UiObject2 otherButton = device.wait(Until.findObject(By.text(buttonText)),500);
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