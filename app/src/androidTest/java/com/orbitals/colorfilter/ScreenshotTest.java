package com.orbitals.colorfilter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ScreenshotTest {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.ScreenshotTest";

    private MainActivity mainActivity;
    //private UiDevice device;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        //device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start the MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mainActivity = (MainActivity) InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    @After
    public void tearDown() {
        if (mainActivity != null) {
            mainActivity.finish();
        }
    }

    @Test
    public void testSimulatedVideoStreamScreenshot() {
        Log.d(TAG, "Starting screenshot test");
        CountDownLatch latch = new CountDownLatch(1);
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try {
            InputStream inputStream = testContext.getResources().openRawResource(com.orbitals.colorfilter.test.R.drawable.test_camera_image);
            Bitmap testBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            Thread.sleep(500);

            mainActivity.runOnUiThread(() -> {
                    TestUtils.captureScreenshot(mainActivity, "simulated_video_stream.png", latch);

            });
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "Screenshot was not captured in time");
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to load image or capture screenshot", e);
        }
    }

    /*
    @Test
    public void testImageModeScreenshot() {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        TestUtils.loadImage(device, Uri.parse("android.resource://" + testContext.getPackageName() + "/" + com.orbitals.colorfilter.test.R.drawable.test_image_mode));
        mainActivity.runOnUiThread(() -> {
            // Uri imageUri = Uri.parse("android.resource://" + testContext.getPackageName() + "/drawable/test_image_mode");
            TestUtils.captureScreenshot(textureView, "image_mode_screenshot.png");
        });
    }
    */
}