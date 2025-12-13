package com.app.estudiaapp;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.app.estudiaapp.SignInActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SignInFlowTest {

    @Rule
    public ActivityScenarioRule<SignInActivity> rule =
            new ActivityScenarioRule<>(SignInActivity.class);

    @Test
    public void signInForm_acceptsInput() {
        onView(withId(R.id.email_input)).perform(replaceText("test@example.com"));
        onView(withId(R.id.password_input)).perform(replaceText("password123"));
        onView(withId(R.id.login_button)).perform(click());
        // Navigation/assertions can be added with IdlingResource and next screen checks
    }
}
