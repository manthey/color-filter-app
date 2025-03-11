package com.orbitals.colorfilter;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {

    @Rule
    public ActivityScenarioRule<SettingsActivity> activityRule =
            new ActivityScenarioRule<>(SettingsActivity.class);

    @Test
    public void testToolbarIsDisplayed() {
        onView(withId(R.id.settingsToolbar)).check(matches(isDisplayed()));
    }
}
