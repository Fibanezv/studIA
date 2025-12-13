package com.app.estudiaapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.app.estudiaapp.QuizActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QuizActivityUiTest {

    @Rule
    public ActivityScenarioRule<QuizActivity> rule =
            new ActivityScenarioRule<>(QuizActivity.class);

    @Test
    public void activityLaunches() {
        // Placeholder: verify activity starts without crash
        // Real tests will check "Pregunta X" title, progress bar, decorative image
        rule.getScenario().onActivity(activity -> {
            // Activity launched successfully
        });
    }
}
