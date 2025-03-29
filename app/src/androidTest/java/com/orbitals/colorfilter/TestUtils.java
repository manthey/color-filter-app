package com.orbitals.colorfilter;

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;


public class TestUtils {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String PACKAGE_NAME = "com.orbitals.colorfilter";
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.TestUtils";

    public static void captureScreenshot(Activity activity, String fileName, CountDownLatch latch) {
        Log.d(TAG, "CAPTURE SCREENSHOT");
        try {
            View view = activity.getWindow().getDecorView().getRootView();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

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
            try (FileOutputStream out = new FileOutputStream(file)) {
                Log.d(TAG, "Screenshot compressing");
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Log.d(TAG, "Screenshot saved to: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error saving screenshot", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screenshot", e);
        }
        latch.countDown();
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
}