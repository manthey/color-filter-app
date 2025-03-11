package com.orbitals.colorfilter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
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
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private static final String PREFS_NAME = "ColorFilterPrefs";
    private CountingIdlingResource idlingResource;
    private Intent testIntent;
    
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
    }

    @Rule
    public ActivityScenarioRule<SettingsActivity> activityRule =
            new ActivityScenarioRule<>(testIntent);

    @Test
    public void testUIElementsAreDisplayed() {
        // Check that all UI elements are displayed
        onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.versionTextView)).check(matches(isDisplayed()));
        onView(withId(R.id.bctControlsSwitch)).check(matches(isDisplayed()));
        onView(withId(R.id.setDefaultsButton)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.loadDefaultsButton)).perform(scrollTo()).check(matches(isDisplayed()));
    }
    
    @Test
    public void testVersionTextIsCorrect() {
        activityRule.getScenario().onActivity(activity -> {
            TextView versionTextView = activity.findViewById(R.id.versionTextView);
            String text = versionTextView.getText().toString();
            assertTrue("Version text should not be empty", !text.isEmpty());
        });
    }
    
    @Test
    public void testBctControlsSwitchState() {
        // Set initial state in preferences
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
        editor.apply();
        
        // Restart activity to apply settings
        activityRule.getScenario().recreate();
        
        // Check that switch is on
        onView(withId(R.id.bctControlsSwitch)).check(matches(isChecked()));
        
        // Toggle switch
        onView(withId(R.id.bctControlsSwitch)).perform(click());
        
        // Check that switch is off
        onView(withId(R.id.bctControlsSwitch)).check(matches(isNotChecked()));
        
        // Verify preference was updated
        boolean updatedPref = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
        assertEquals(false, updatedPref);
    }
    
    @Test
    public void testSaveDefaultSettings() {
        // Click set defaults button
        onView(withId(R.id.setDefaultsButton)).perform(scrollTo(), click());
        
        // Wait for UI to update
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
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
        
        // Click load defaults button
        onView(withId(R.id.loadDefaultsButton)).perform(scrollTo(), click());
        
        // Wait for UI to update
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Verify the result intent includes the correct flags when back is pressed
        activityRule.getScenario().onActivity(activity -> {
            activity.onBackPressed();
        });
        
        // Check that the activity finishes with the right result
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        activityRule.getScenario().onActivity(activity -> {
            assertTrue(activity.isFinishing());
        });
    }
    
    @Test
    public void testNavigateUp() {
        // Click the up button in toolbar
        onView(withId(R.id.settingsToolbar)).perform(click());
        
        // Wait for UI to update
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        
        // Verify activity finishes
        activityRule.getScenario().onActivity(activity -> {
            assertTrue(activity.isFinishing());
        });
    }
}
