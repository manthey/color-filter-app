package com.orbitals.colorfilter;

import android.view.TextureView;
import androidx.test.espresso.IdlingResource;

public class TextureViewIdlingResource implements IdlingResource {

    private final TextureView textureView;
    private ResourceCallback callback;

    public TextureViewIdlingResource(TextureView textureView) {
        this.textureView = textureView;
    }

    @Override
    public String getName() {
        return TextureViewIdlingResource.class.getName();
    }

    @Override
    public boolean isIdleNow() {
        // Check if the TextureView is available
        boolean isIdle = textureView.isAvailable();
        if (isIdle && callback != null) {
            callback.onTransitionToIdle();
        }
        return isIdle;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.callback = callback;
    }
}