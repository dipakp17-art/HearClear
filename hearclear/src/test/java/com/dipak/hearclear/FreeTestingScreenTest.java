package com.dipak.hearclear;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPopupMenu;
import static org.junit.Assert.*;

/** Regression coverage for the unrestricted, offline test release. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FreeTestingScreenTest {
    @Test public void accessRemainsAllowedWithoutSessionLeaseOrServer() {
        assertFalse(BuildConfig.ACCOUNTS_ENABLED);
        var app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("account",0).edit().putString("token", "old-unusable-session").commit();
        AccountClient.INSTANCE.clearLease();
        assertTrue(AccountClient.INSTANCE.canListen());
        assertFalse(AccountClient.INSTANCE.configured());
        AccountClient.INSTANCE.refresh(app); // must be a local no-op, not a network request
        assertTrue(AccountClient.INSTANCE.canListen());
    }
    @Test public void mainMenuOnlyOffersSharingAndInformation() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            Activity a = controller.get();
            assertEquals(View.GONE, a.findViewById(R.id.accountStatus).getVisibility());
            a.findViewById(R.id.menuButton).performClick();
            PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
            assertNotNull(popup);
            assertEquals(2, popup.getMenu().size());
            assertEquals("Share app", popup.getMenu().getItem(0).getTitle().toString());
            assertEquals("App information", popup.getMenu().getItem(1).getTitle().toString());
        }
    }
    @Test public void manifestDisablesAccountScreensAndHasNoInternetPermission() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        PackageManager manager = app.getPackageManager();
        PackageInfo info = manager.getPackageInfo(app.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals(Manifest.permission.INTERNET, permission);
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS;
        assertFalse(manager.getActivityInfo(new ComponentName(app, AccountActivity.class), flags).enabled);
        assertFalse(manager.getActivityInfo(new ComponentName(app, PaymentActivity.class), flags).enabled);
    }
    @Test public void informationDescribesFreeOfflineTesting() {
        try (var controller = Robolectric.buildActivity(AppInfoActivity.class).setup().visible()) {
            StringBuilder text = new StringBuilder();
            collect(controller.get().findViewById(android.R.id.content), text);
            assertTrue(text.toString().contains("No sign-in, payment or 48-hour limit"));
            assertTrue(text.toString().contains("laptop can be switched off"));
        }
    }
    private void collect(View view, StringBuilder result) {
        if (view instanceof TextView) result.append(((TextView)view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) collect(group.getChildAt(i),result);
        }
    }
}
