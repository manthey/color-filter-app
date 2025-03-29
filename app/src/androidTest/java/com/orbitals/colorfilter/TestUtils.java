package com.orbitals.colorfilter;

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class TestUtils {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.TestUtils";

    public static String getLauncherPackageName() {
        // Create launcher Intent
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        // Use PackageManager to get the launcher package name
        PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return Objects.requireNonNull(resolveInfo).activityInfo.packageName;
    }

    public static void handlePermissionDialogs(UiDevice device) {
        // Handle permission dialogs that might appear.  Increase counts if there could be multiple permissions
        for (int i = 0; i < 1; i++) {
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
                UiObject2 otherButton = device.wait(Until.findObject(By.text(buttonText)), 500);
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

    public static void captureScreenshot(UiDevice device, Activity activity, String fileName, CountDownLatch latch) {
        Log.d(TAG, "CAPTURE SCREENSHOT");
        try {
            boolean success = false;
            File screenshotsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "test_screenshots");
            if (!screenshotsDir.exists()) {
                boolean created = screenshotsDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create screenshots directory.");
                    latch.countDown();
                    return;
                }
            }
            Log.d(TAG, "Screenshots directory path: " + screenshotsDir.getAbsolutePath());

            File file = new File(screenshotsDir, fileName);
            Log.d(TAG, "Screenshot saving to: " + file.getAbsolutePath());
            if (device != null) {
                if (device.takeScreenshot(file)) {
                    success = true;
                }
            }
            if (!success) {
                View view = activity.getWindow().getDecorView().getRootView();
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    Log.d(TAG, "Screenshot compressing");
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Log.d(TAG, "Screenshot saved to: " + file.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Error saving screenshot", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screenshot", e);
        }
        latch.countDown();
    }


    public static void captureScreenshot(UiDevice device, Activity activity, String fileName) {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            activity.runOnUiThread(() -> TestUtils.captureScreenshot(device, activity, fileName, latch));
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "Screenshot was not captured in time");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to load image or capture screenshot", e);
        }
    }

    public static void clickSeek(UiDevice device, String resourceId, double position) {
        UiObject2 seekBar = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, resourceId)), 2000);
        Rect bounds = seekBar.getVisibleBounds();
        Point target = new android.graphics.Point((int) (bounds.left + bounds.width() * position), bounds.centerY());
        seekBar.drag(target, 10000);
    }

    public static void sleep(double seconds) {
        try {
            Thread.sleep((int)(seconds * 1000));
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to load image or capture screenshot", e);
        }
    }

    public static void loadImage(UiDevice device, Uri dummyUri) {
        // Create a result intent with the sample image
        Intent resultData = new Intent();
        resultData.setData(dummyUri);
        // Set up the intent result
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(
                Activity.RESULT_OK, resultData);

        intending(hasAction(Intent.ACTION_PICK)).

                respondWith(result);

        UiObject2 loadImageButton = device.wait(
                Until.findObject(By.res(PACKAGE_NAME, "loadImageButton")), 2000);
        loadImageButton.click();

        intended(hasAction(Intent.ACTION_PICK));

        device.wait(Until.findObject(By.res(PACKAGE_NAME, "textureView")), 2000);
    }

    public static void loadImage(UiDevice device, int resourceId) {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        TestUtils.loadImage(device, Uri.parse("android.resource://" + testContext.getPackageName() + "/" + resourceId));

    }
}