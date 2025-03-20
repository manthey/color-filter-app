package com.orbitals.colorfilter;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.exifinterface.media.ExifInterface;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.InputStream;

public class ImageController {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.ImageController";

    private final Context context;
    private final TextureView textureView;
    private final FilterProcessor filter;

    private Bitmap loadedImage = null;
    private final Matrix imageMatrix = new Matrix();
    private final RectF imageBounds = new RectF();
    private final float[] matrixValues = new float[9];
    private Bitmap processedImage = null;


    public interface FilterUpdateCallback {
        void onFilterUpdated();
    }

    private final FilterUpdateCallback updateCallback;

    public ImageController(Context context, TextureView textureView, FilterProcessor filter, FilterUpdateCallback updateCallback) {
        this.context = context;
        this.textureView = textureView;
        this.filter = filter;
        this.updateCallback = updateCallback;
    }

    public Bitmap handleImagePickerResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.getData() != null) {
                Uri imageUri = data.getData();
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
                    loadedImage = BitmapFactory.decodeStream(inputStream, null, options);
                    int orientation = getOrientation(imageUri);
                    if (orientation != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(orientation);
                        loadedImage = Bitmap.createBitmap(loadedImage, 0, 0, loadedImage.getWidth(), loadedImage.getHeight(), matrix, true);
                    }
                    setupImageMatrix();
                    displayLoadedImage();
                    return loadedImage;
                } catch (Exception e) {
                    Log.e(TAG, "Error loading image", e);
                    Toast.makeText(context, context.getString(R.string.image_load_failed), Toast.LENGTH_SHORT).show();
                }
            }
        }
        return null;
    }

    public int getOrientation(Uri photoUri) {
        try {
            InputStream stream = context.getContentResolver().openInputStream(photoUri);
            if (stream == null) {
                return 0;
            }
            ExifInterface ei = new ExifInterface(stream);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private void setupImageMatrix() {
        float viewWidth = textureView.getWidth();
        float viewHeight = textureView.getHeight();
        float imageWidth = loadedImage.getWidth();
        float imageHeight = loadedImage.getHeight();

        // Calculate scale to fit screen while maintaining aspect ratio
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        // Center the image
        float dx = (viewWidth - imageWidth * scale) / 2;
        float dy = (viewHeight - imageHeight * scale) / 2;

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);

        // Set bounds for pan limits
        imageBounds.set(0, 0, imageWidth, imageHeight);
        imageMatrix.mapRect(imageBounds);
    }

    public void displayLoadedImage() {
        displayLoadedImage(false);
    }

    private void displayLoadedImage(Boolean reuse) {
        if (loadedImage == null) {
            return;
        }

        if (processedImage == null || !reuse || filter.getSampleMode()) {
            Mat inputMat = new Mat();
            Utils.bitmapToMat(loadedImage, inputMat);
            if (filter.getSampleMode()) {
                Mat centerChunk = Utilities.centerOfImage(context, textureView, filter, inputMat, imageMatrix);
                if (filter.sampleRegion(centerChunk) && updateCallback != null) {
                    ((Activity) context).runOnUiThread(updateCallback::onFilterUpdated);
                    reuse = false;
                }
                centerChunk.release();
            }
            if ((processedImage == null || !reuse)) {
                Mat processedMat = filter.process(inputMat);

                processedImage = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(processedMat, processedImage);

                inputMat.release();
                processedMat.release();
            }
        }

        Canvas canvas = textureView.lockCanvas();
        if (canvas != null) {
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(processedImage, imageMatrix, null);
            Utilities.drawSamplingCircle(context, filter, canvas);
            textureView.unlockCanvasAndPost(canvas);
        }
    }

    private void constrainImage() {
        RectF rect = new RectF(0, 0, loadedImage.getWidth(), loadedImage.getHeight());
        imageMatrix.mapRect(rect);

        float dx = 0, dy = 0;
        float minScale = Math.min((float) textureView.getWidth() / loadedImage.getWidth(), (float) textureView.getHeight() / loadedImage.getHeight());
        imageMatrix.getValues(matrixValues);
        float currentScale = matrixValues[Matrix.MSCALE_X];
        if (currentScale < minScale) {
            imageMatrix.postScale(minScale / currentScale, minScale / currentScale, textureView.getWidth() / 2f, textureView.getHeight() / 2f);
            imageMatrix.mapRect(rect);
        }

        // Constrain horizontal movement
        if (rect.width() <= textureView.getWidth()) {
            dx = (textureView.getWidth() - rect.width()) / 2 - rect.left;
        } else {
            if (rect.left > 0) dx = -rect.left;
            if (rect.right < textureView.getWidth()) dx = textureView.getWidth() - rect.right;
        }

        // Constrain vertical movement
        if (rect.height() <= textureView.getHeight()) {
            dy = (textureView.getHeight() - rect.height()) / 2 - rect.top;
        } else {
            if (rect.top > 0) dy = -rect.top;
            if (rect.bottom < textureView.getHeight()) dy = textureView.getHeight() - rect.bottom;
        }
        imageMatrix.postTranslate(dx, dy);
    }

    public void clearImage() {
        loadedImage = null;
        processedImage = null;
    }

    public void onConfigurationChanged() {
        textureView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                textureView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                setupImageMatrix();
                displayLoadedImage();
            }
        });
    }

    public void scaleMatrix(float scale, float centerX, float centerY) {
        imageMatrix.getValues(matrixValues);
        float currentScale = matrixValues[Matrix.MSCALE_X];

        float minScale = Math.min(textureView.getWidth() / loadedImage.getWidth(), textureView.getHeight() / loadedImage.getHeight());
        float maxScale = Math.max(2.0f, Math.max(textureView.getWidth() / (float) loadedImage.getWidth(), textureView.getHeight() / (float) loadedImage.getHeight()) * 2);
        float maxDensityScale = context.getResources().getDisplayMetrics().density * 2;
        maxScale = Math.max(maxScale, maxDensityScale);

        float newScale = Math.min(Math.max(currentScale * scale, minScale), maxScale);
        scale = newScale / currentScale;

        imageMatrix.postScale(scale, scale, centerX, centerY);
        constrainImage();
        displayLoadedImage(true);
    }

    public void translateMatrix(float dx, float dy) {
        imageMatrix.postTranslate(dx, dy);
        constrainImage();
        displayLoadedImage(true);
    }
}
