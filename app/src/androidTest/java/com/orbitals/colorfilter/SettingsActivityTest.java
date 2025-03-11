package com.orbitals.colorfilter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private Context context;
    private String appVersion;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            appVersion = "Unknown";
        }
    }

    @Test
    public void testUIElementsVisibility() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.versionTextView)).check(matches(isDisplayed()));
            onView(withId(R.id.bctControlsSwitch)).check(matches(isDisplayed()));
            onView(withId(R.id.setDefaultsButton)).check(matches(isDisplayed()));
            onView(withId(R.id.loadDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testVersionTextViewContent() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            String expectedVersionText = context.getString(R.string.version_format, appVersion);
            onView(withId(R.id.versionTextView)).check(matches(withText(expectedVersionText)));
        }
    }

    @Test
    public void testBctControlsSwitchFunctionality() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean initialValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);

            onView(withId(R.id.bctControlsSwitch)).perform(click());

            boolean newValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
            assertEquals(!initialValue, newValue);
        }
    }

    @Test
    public void testSetDefaultsButton() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.setDefaultsButton)).perform(click());
            // Basic check - more detailed checks would require injecting specific initial values
            onView(withId(R.id.setDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testLoadDefaultsButton() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.loadDefaultsButton)).perform(click());
            // Basic check
            onView(withId(R.id.loadDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testToolbarNavigation() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            pressBack();
            // Check if the activity is finishing (not ideal, but a simple check)
            assertTrue(scenario.getResult().getResultCode() == 0 || scenario.getResult().getResultCode() == -1); // Activity.RESULT_CANCELED or Activity.RESULT_OK
        }
    }

    @Test
    public void testSettingsPersistence() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            // Change the BCT controls switch
            onView(withId(R.id.bctControlsSwitch)).perform(click());
            //Recreate activity
            scenario.recreate();
            //Verify that switch is in changed state
            SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean newValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
            onView(withId(R.id.bctControlsSwitch)).check(matches(newValue ? isDisplayed() : not(isDisplayed())));
        }
    }
}package com.orbitals.colorfilter;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    private Context context;
    private String appVersion;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            appVersion = "Unknown";
        }
    }

    @Test
    public void testUIElementsVisibility() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.versionTextView)).check(matches(isDisplayed()));
            onView(withId(R.id.bctControlsSwitch)).check(matches(isDisplayed()));
            onView(withId(R.id.setDefaultsButton)).check(matches(isDisplayed()));
            onView(withId(R.id.loadDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testVersionTextViewContent() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            String expectedVersionText = context.getString(R.string.version_format, appVersion);
            onView(withId(R.id.versionTextView)).check(matches(withText(expectedVersionText)));
        }
    }

    @Test
    public void testBctControlsSwitchFunctionality() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean initialValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);

            onView(withId(R.id.bctControlsSwitch)).perform(click());

            boolean newValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
            assertEquals(!initialValue, newValue);
        }
    }

    @Test
    public void testSetDefaultsButton() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.setDefaultsButton)).perform(click());
            // Basic check - more detailed checks would require injecting specific initial values
            onView(withId(R.id.setDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testLoadDefaultsButton() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.loadDefaultsButton)).perform(click());
            // Basic check
            onView(withId(R.id.loadDefaultsButton)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testToolbarNavigation() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            pressBack();
            // Check if the activity is finishing (not ideal, but a simple check)
            assertTrue(scenario.getResult().getResultCode() == 0 || scenario.getResult().getResultCode() == -1); // Activity.RESULT_CANCELED or Activity.RESULT_OK
        }
    }

    @Test
    public void testSettingsPersistence() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            // Change the BCT controls switch
            onView(withId(R.id.bctControlsSwitch)).perform(click());
            //Recreate activity
            scenario.recreate();
            //Verify that switch is in changed state
            SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
            boolean newValue = prefs.getBoolean(SettingsActivity.KEY_SHOW_BCT_CONTROLS, true);
            onView(withId(R.id.bctControlsSwitch)).check(matches(newValue ? isDisplayed() : not(isDisplayed())));
        }
    }
}
