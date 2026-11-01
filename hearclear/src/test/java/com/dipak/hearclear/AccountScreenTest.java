package com.dipak.hearclear;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

/** Exercise actual Activity/widget wiring, not just the form validator. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AccountScreenTest {
    private List<View> views(Activity activity) {
        List<View> result = new ArrayList<>();
        walk(activity.findViewById(android.R.id.content), result);
        return result;
    }
    private void walk(View view, List<View> result) {
        result.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) walk(group.getChildAt(i), result);
        }
    }
    private Button button(Activity a, String label) {
        Button found = null;
        for (View view : views(a)) if (view instanceof Button && view.isShown() && ((Button)view).getText().toString().equals(label)) found = (Button)view;
        assertNotNull("Visible button: " + label, found);
        return found;
    }
    private List<EditText> fields(Activity a) {
        List<EditText> result = new ArrayList<>();
        for (View view : views(a)) if (view instanceof EditText && view.isShown()) result.add((EditText)view);
        return result;
    }
    @Test public void signedOutScreenHidesPaymentAndOnlyShowsSignInFields() {
        try (var controller = Robolectric.buildActivity(AccountActivity.class).setup().visible()) {
            Activity a = controller.get();
            assertEquals(2, fields(a).size());
            for (View view : views(a)) if (view instanceof Button && ((Button)view).getText().toString().equals("Payment & activation")) assertFalse(view.isShown());
            button(a,"Moved to a new phone?").performClick();
            assertEquals(3, fields(a).size());
            button(a,"Sign in").performClick();
            assertEquals(2, fields(a).size());
        }
    }
    @Test public void shortPasswordShowsInlineErrorWithoutSendingCredentials() {
        try (var controller = Robolectric.buildActivity(AccountActivity.class).setup().visible()) {
            Activity a = controller.get();
            List<EditText> fields = fields(a);
            fields.get(0).setText("deepak"); fields.get(1).setText("short");
            button(a,"Sign in").performClick();
            assertNotNull(fields.get(1).getError());
            assertTrue(button(a,"Sign in").isEnabled());
        }
    }
    @Test public void registrationRequiresConsentAndPrivacyHasOwnPage() {
        try (var controller = Robolectric.buildActivity(AccountActivity.class).setup().visible()) {
            Activity a = controller.get();
            button(a,"Create a new account").performClick();
            List<EditText> fields = fields(a);
            fields.get(0).setText("deepak"); fields.get(1).setText("valid-password-123");
            button(a,"Create account · start trial").performClick();
            boolean notice = false;
            for (View view : views(a)) if (view instanceof android.widget.TextView && ((android.widget.TextView)view).getText().toString().contains("Read and accept")) notice = true;
            assertTrue(notice);
            button(a,"Read account data notice").performClick();
            Intent intent = shadowOf(a).getNextStartedActivity();
            assertEquals(AppInfoActivity.class.getName(), intent.getComponent().getClassName());
            assertTrue(intent.getBooleanExtra("privacy",false));
        }
    }
    @Test public void mainStartCanOpenAccountBeforeHeadphonesOrMicPermission() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            Activity a = controller.get();
            View start = a.findViewById(R.id.startButton);
            assertTrue(start.isEnabled()); start.performClick();
            Intent intent = shadowOf(a).getNextStartedActivity();
            assertEquals(AccountActivity.class.getName(), intent.getComponent().getClassName());
        }
    }
}
