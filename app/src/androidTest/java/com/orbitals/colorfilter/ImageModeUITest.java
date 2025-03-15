package com.orbitals.colorfilter;

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;

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

@RunWith(AndroidJUnit4.class)
public class ImageModeUITest {
    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final String TAG = "ImageModeUITest";
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

        // Initialize Espresso Intents
        Intents.init();

        // Launch the app
        Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = context.getPackageManager()
                .getLaunchIntentForPackage(PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void cleanup() {
        Intents.release();
    }

    @Test
    public void testImageModeFeatures() {
        // Wait for the app to fully load
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT);

        // Handle any permission dialogs
        handlePermissionDialogs();

        // 1. Set up intent response for the gallery picker
        // Create a sample bitmap to return as the selected image
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
        // Get the texture view that displays the image
        UiObject2 textureView = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
        
        // Simulate a pinch-to-zoom gesture
        int centerX = textureView.getVisibleCenter().x;
        int centerY = textureView.getVisibleCenter().y;
        
        // Zoom in
        device.swipe(
                centerX - 100, centerX + 100,
                centerX - 50, centerX + 50,
                10);
        
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);
        
        // 6. Test filter button in image mode
        UiObject2 filterButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "filterButton")), 2000);
        filterButton.click();
        
        // Wait for filter to apply
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);
        
        // 7. Test adjusting hue/saturation in image mode
        UiObject2 hueSeekBar = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "hueSeekBar")), 2000);
        if (hueSeekBar != null) {
            // Drag the seekbar to change the hue
            int width = hueSeekBar.getVisibleBounds().width();
            hueSeekBar.drag(new android.graphics.Point(width / 2, 0), 30);
            
            // Wait for the change to apply
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 1000);
        }
        
        // 8. Return to camera mode
        UiObject2 cameraButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "switchCameraButton")), 2000);
        cameraButton.click();
        
        // Wait to confirm we're back to camera mode
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 2000);
    }

    private void handlePermissionDialogs() {
        // Handle permission dialogs that might appear
        for (int i = 0; i < 3; i++) {
            // Try common permission buttons
            String[] commonButtonTexts = {"WHILE USING THE APP", "ONLY THIS TIME", "ALLOW", "YES", "OK"};
            boolean buttonClicked = false;

            for (String buttonText : commonButtonTexts) {
                UiObject2 button = device.wait(
                        Until.findObject(By.text(buttonText)),
                        1000);

                if (button != null) {
                    button.click();
                    device.wait(Until.gone(By.text(buttonText)), 1000);
                    buttonClicked = true;
                    break;
                }
            }

            if (!buttonClicked) {
                // Try by resource ID containing "allow_foreground"
                UiObject2 permissionButton = device.wait(
                        Until.findObject(By.res(UiDevice.getInstance(
                                InstrumentationRegistry.getInstrumentation()).getCurrentPackageName(), 
                                "permission_allow_foreground_only_button")),
                        1000);
                
                if (permissionButton != null) {
                    permissionButton.click();
                    continue;
                }
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
