package com.orbitals.colorfilter;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import android.graphics.ColorSpace;

public class TermMap {
    /**
     * @noinspection SpellCheckingInspection
     */
    private static final String TAG = "com.orbitals.colorfilter.TermMap";

    private final String name;
    private final String id;
    private final String description;
    private final String reference;
    private final List<String> terms;
    private final byte[] map;
    private int blur = 5;
    private static boolean matchedColorSpace = false;

    /**
     * Create a TermMap.
     *
     * @param name              The display name of the TermMap.
     * @param id                A unique identifier for the TermMap.  This should probably be the
     *                          locale combined with the name (e.g., en_US_BCT20).
     * @param terms             A list of terms.  The internal resource should use values in the
     *                          range of [0, number of terms).
     * @param resources         The resources to load from.
     * @param termMapResourceId The resource id to load.  This should either be a greyscale
     *                          lossless image or a palette lossless image.
     */
    public TermMap(String name, String id, String description, String reference, List<String> terms, Resources resources, int termMapResourceId) {
        this.name = name;
        this.id = id;
        this.description = description;
        this.reference = reference;
        this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
        map = new byte[4096 * 4096];

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, termMapResourceId, options);

        Mat tmpMat = new Mat();
        Utils.bitmapToMat(bitmap, tmpMat);
        Mat mat = new Mat();
        Imgproc.cvtColor(tmpMat, mat, Imgproc.COLOR_RGBA2GRAY);
        bitmap.recycle();
        int imageSize = 256;
        int bytesPerImage = imageSize * imageSize;
        for (int i = 0; i < 256; i++) {
            int row = i / 16;
            int col = i % 16;
            int x = col * imageSize;
            int y = row * imageSize;

            Rect rect = new Rect(x, y, imageSize, imageSize);
            Mat subMat = new Mat(mat, rect);
            byte[] subImageBytes = new byte[subMat.rows() * subMat.cols()];
            subMat.get(0, 0, subImageBytes);
            System.arraycopy(subImageBytes, 0, map, i * bytesPerImage, bytesPerImage);
        }
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    /**
     * @noinspection unused
     */
    public String getDescription() {
        return description;
    }

    /**
     * @noinspection unused
     */
    public String getReference() {
        return reference;
    }

    public List<String> getTerms() {
        return terms;
    }

    /**
     * @noinspection unused
     */
    public byte[] getMap() {
        return map != null ? Arrays.copyOf(map, map.length) : null;
    }

    /**
     * Get the blur value used to reduce variations.
     *
     * @return The current blur value.
     * @noinspection unused
     */
    public int getBlur() {
        return blur;
    }

    /**
     * Set the blur value used to reduce variations.
     *
     * @param blur Either 0 or an odd number.  If negative, the blur is applied BEFORE the color
     *             terms.  If positive, it is applied AFTER the color terms.
     * @noinspection unused
     */
    public void setBlur(int blur) {
        this.blur = blur;
    }

    /**
     * Given an input image in RGB color space, create a mask image that is single channel and has
     * either 0 or 255 at each pixel.
     *
     * @param image The input RGB image.
     * @param term  The term value to match.
     * @return An output mask image.
     */
    public Mat createMask(Mat image, int term) {
        Mat mappedImage = createMap(image);
        if (blur > 1) {
            Imgproc.medianBlur(mappedImage, mappedImage, blur);
        }
        Mat mask = new Mat();
        Core.compare(mappedImage, new Scalar(term), mask, Core.CMP_EQ);
        return mask;
    }

    /**
     * Given an input image in RGB color space, create a image that is single channel and has the
     * value of the color term at each pixel.
     *
     * @param image The input RGB image.
     * @return An output mask image.
     */
    public Mat createMap(Mat image) {
        int width = image.cols();
        int height = image.rows();
        byte[] rgbData = new byte[image.channels() * width * height];
        if (blur < -1) {
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(image, blurred, new Size(-blur, -blur), 0);
            /* We could try other filters, but they operate on channels independently, so the results
             * aren't what we desire:
             *  Imgproc.blur(image, blurred, new Size(-blur, -blur));
             *  Imgproc.medianBlur(image, blurred, -blur); */
            blurred.get(0, 0, rgbData);
        } else {
            image.get(0, 0, rgbData);
        }
        byte[] mapData = new byte[width * height];

        int center = ((height / 2) * width + (width / 2));
        IntStream.range(0, height).parallel().forEach(y -> {
            int maxJ = (y + 1) * width;
            for (int i = y * width * 3, j = y * width; j < maxJ; i += 3, j++) {
                // Get RGB values (unsigned)
                int r = rgbData[i] & 0xFF;
                int g = rgbData[i + 1] & 0xFF;
                int b = rgbData[i + 2] & 0xFF;
                int index = (r << 16) | (g << 8) | b;
                if (j == center) {
                    Log.d(TAG, "Center " + " " + r + "," + g + "," + b + " " + map[index]);
                }
                mapData[j] = map[index];
            }
        });

        Mat mappedImage = new Mat(height, width, CvType.CV_8UC1);
        mappedImage.put(0, 0, mapData);
        return mappedImage;
    }

    public static ArrayList<TermMap> loadTermMaps(Resources resources, ColorSpace colorSpace) {
        ArrayList<TermMap> termMaps = new ArrayList<>();

        int NAME = 0;
        int ID = 1;
        int DESCRIPTION = 2;
        int REFERENCE = 3;
        int TERMS = 4;
        int IMAGES = 5;

        try (TypedArray termMapIds = resources.obtainTypedArray(R.array.term_map_ids)) {
            for (int i = 0; i < termMapIds.length(); i++) {
                int termMapId = termMapIds.getResourceId(i, 0);
                try (TypedArray termMapArray = resources.obtainTypedArray(termMapId)) {
                    String name = termMapArray.getString(NAME);
                    String id = termMapArray.getString(ID);
                    String description = termMapArray.getString(DESCRIPTION);
                    String reference = termMapArray.getString(REFERENCE);
                    int termsArrayId = termMapArray.getResourceId(TERMS, 0);
                    List<String> terms = Collections.unmodifiableList(Arrays.asList(resources.getStringArray(termsArrayId)));

                    int imagesArrayId = termMapArray.getResourceId(IMAGES, 0);
                    int termMapResourceId = 0;

                    // Get the string array instead of typed array
                    String[] imagesArray = resources.getStringArray(imagesArrayId);
                    for (String entry : imagesArray) {
                        String[] parts = entry.split(",", 2);
                        String colorSpaceName = parts[0];
                        String resourceRef = parts[1];
                        @SuppressLint("DiscouragedApi")
                        int imageId = resources.getIdentifier(
                                resourceRef.substring(1), // Remove the @ symbol
                                null,
                                resources.getResourcePackageName(termMapId));
                        if (termMapResourceId == 0) {
                            termMapResourceId = imageId;
                        }
                        if (colorSpaceName != null) {
                             if (colorSpaceName.equals("SRGB")) {
                                termMapResourceId = imageId;
                            }
                            if (colorSpace.equals(ColorSpace.get(ColorSpace.Named.valueOf(colorSpaceName)))) {
                                termMapResourceId = imageId;
                                matchedColorSpace = true;
                                break;
                            }
                        }
                    }
                    Log.d(TAG, "TermMap Resource " + termMapResourceId);
                    termMaps.add(new TermMap(name, id, description, reference, terms, resources, termMapResourceId));
                }
            }
        }
        return termMaps;
    }

    public static boolean getMatchedColorSpace() {
        return matchedColorSpace;
    }
}
