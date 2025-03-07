package com.orbitals.colorfilter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.view.TextureView;

import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    private TextureViewIdlingResource idlingResource;

    @Before
    public void setUp() {
        // Access the TextureView from MainActivity
        activityRule.getScenario().onActivity(activity -> {
            TextureView textureView = activity.findViewById(R.id.textureView);
            idlingResource = new TextureViewIdlingResource(textureView);
            Espresso.registerIdlingResources(idlingResource);
        });
    }

    @After
    public void tearDown() {
        // Unregister the Idling Resource after the test
        if (idlingResource != null) {
            Espresso.unregisterIdlingResources(idlingResource);
        }
    }

    @Test
    public void testMainActivityIsDisplayed() {
        onView(withId(R.id.textureView)).check(matches(isDisplayed()));
    }
}
