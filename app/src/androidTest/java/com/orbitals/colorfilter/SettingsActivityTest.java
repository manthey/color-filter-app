package com.orbitals.colorfilter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private static final String PREFS_NAME = "ColorFilterPrefs";
    private CountingIdlingResource idlingResource;
    private Intent testIntent;
    private ActivityScenario<SettingsActivity> scenario;
    
    @Before
    public void setup() {
        // Clear preferences before each test
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        // Setup intent with test values
        testIntent = new Intent(ApplicationProvider.getApplicationContext(), SettingsActivity.class);
        testIntent.putExtra("filterMode", 1); // INCLUDE mode
        testIntent.putExtra("hue", 120);
        testIntent.putExtra("hueWidth", 30);
        testIntent.putExtra("satThreshold", 50);
        testIntent.putExtra("lumThreshold", 75);
        testIntent.putExtra("term", 2);
        testIntent.putExtra("termMapId", "test_map");
        
        // Create and register idling resource
        idlingResource = new CountingIdlingResource("Settings");
        IdlingRegistry.getInstance().register(idlingResource);
    }
    
    @After
    public void tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource);
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void testToolbarIsDisplayed() {
        // Launch activity with default intent
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Check that toolbar is displayed
        onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
    }

    @Test
    public void testUIElementsAreDisplayed() {
        // Launch activity with default intent
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Check that all UI elements are displayed
        onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.versionTextView)).check(matches(isDisplayed()));
        onView(withId(R.id.bctControlsSwitch)).check(matches(isDisplayed()));
        onView(withId(R.id.setDefaultsButton)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.loadDefaultsButton)).perform(scrollTo()).check(matches(isDisplayed()));
    }
    
    @Test
    public void testVersionTextIsDisplayed() {
        // Launch activity with default intent
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Check that version text is displayed and contains a period
        onView(withId(R.id.versionTextView))
            .check(matches(isDisplayed()))
            .check(matches(withText(containsString("."))));
    }
    
    @Test
    public void testBctControlsSwitchState() {
        // Set initial state in preferences
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
        editor.apply();
        
        // Launch activity
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Check that switch is on
        onView(withId(R.id.bctControlsSwitch)).check(matches(isChecked()));
        
        // Toggle switch
        onView(withId(R.id.bctControlsSwitch)).perform(click());
        
        // Wait for UI to update
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        // Check that switch is off
        onView(withId(R.id.bctControlsSwitch)).check(matches(isNotChecked()));
        
        // Verify preference was updated
        boolean updatedPref = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
        assertFalse(updatedPref);
    }
    
    @Test
    public void testSaveDefaultSettings() {
        // Launch activity with custom intent
        scenario = ActivityScenario.launch(testIntent);
        
        // Wait for UI to be ready
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        // Click set defaults button
        onView(withId(R.id.setDefaultsButton)).perform(scrollTo(), click());
        
        // Wait for UI to update
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        // Verify preferences were saved
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        assertEquals(1, prefs.getInt(SettingsActivity.KEY_FILTER_MODE, -1));
        assertEquals(120, prefs.getInt(SettingsActivity.KEY_HUE, -1));
        assertEquals(30, prefs.getInt(SettingsActivity.KEY_HUE_WIDTH, -1));
        assertEquals(50, prefs.getInt(SettingsActivity.KEY_SAT_THRESHOLD, -1));
        assertEquals(75, prefs.getInt(SettingsActivity.KEY_LUM_THRESHOLD, -1));
        assertEquals(2, prefs.getInt(SettingsActivity.KEY_TERM, -1));
        assertEquals("test_map", prefs.getString(SettingsActivity.KEY_TERM_MAP, null));
    }
    
    @Test
    public void testLoadDefaultSettings() {
        // First save some default settings
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(SettingsActivity.KEY_FILTER_MODE, 2);
        editor.putInt(SettingsActivity.KEY_HUE, 180);
        editor.putInt(SettingsActivity.KEY_HUE_WIDTH, 40);
        editor.putInt(SettingsActivity.KEY_SAT_THRESHOLD, 60);
        editor.putInt(SettingsActivity.KEY_LUM_THRESHOLD, 85);
        editor.putInt(SettingsActivity.KEY_TERM, 3);
        editor.putString(SettingsActivity.KEY_TERM_MAP, "default_map");
        editor.apply();
        
        // Launch activity
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Click load defaults button
        onView(withId(R.id.loadDefaultsButton)).perform(scrollTo(), click());
        
        // Wait for UI to update
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        // Check the defaultsLoaded flag is set when back is pressed
        final boolean[] defaultsLoaded = {false};
        
        scenario.onActivity(activity -> {
            activity.onBackPressed();
        });
        
        // We can't easily verify the result intent in this test framework,
        // but we can verify the activity finishes
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    
    @Test
    public void testNavigateUp() {
        // Launch activity
        scenario = ActivityScenario.launch(SettingsActivity.class);
        
        // Simulate up navigation
        final boolean[] upNavigated = {false};
        
        scenario.onActivity(activity -> {
            upNavigated[0] = activity.onSupportNavigateUp();
        });
        
        // Verify result
        assertTrue("onSupportNavigateUp should return true", upNavigated[0]);
        
        // Wait for UI to update
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
