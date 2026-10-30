package com.openbubbles.wear.patch;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.ContextWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {32}, application = Application.class)
public class EmbeddedAlertPolicyTest {
    @Test public void hidingBackendStatusLeavesIncomingMessageAlertsEligible() {
        Context context = RuntimeEnvironment.getApplication();
        setPhoneLocked(context, true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(PatchSettings.READY_NOTIFICATION_CHANNEL,
                "Background connection", NotificationManager.IMPORTANCE_NONE));
        manager.createNotificationChannel(new NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_HIGH));
        assertTrue(PatchSettings.isReadyNotificationHidden(context));
        assertTrue(manager.areNotificationsEnabled());
        assertTrue(EmbeddedHooks.shouldForwardNotification(context,
                new Notification.Builder(context, "messages").build(), false));
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel("messages").getImportance());
    }

    @Test public void blockedNativeChannelAndSilentPostsCannotAlertTheWatch() {
        Context context = RuntimeEnvironment.getApplication();
        setPhoneLocked(context, true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("blocked", "Blocked", NotificationManager.IMPORTANCE_NONE));
        manager.createNotificationChannel(new NotificationChannel("allowed", "Allowed", NotificationManager.IMPORTANCE_DEFAULT));
        Notification blocked = new Notification.Builder(context, "blocked").build();
        Notification allowed = new Notification.Builder(context, "allowed").build();
        assertFalse(EmbeddedHooks.shouldForwardNotification(context, blocked, false));
        assertFalse(EmbeddedHooks.shouldForwardNotification(context, allowed, true));
        assertFalse(EmbeddedHooks.shouldForwardNotification(context, null, false));
        assertTrue(EmbeddedHooks.shouldForwardNotification(context, allowed, false));
    }

    @Test public void unlockedPhoneSuppressesWatchAlerts() {
        Context context = RuntimeEnvironment.getApplication();
        setPhoneLocked(context, false);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_HIGH));
        assertFalse(EmbeddedHooks.shouldForwardNotification(context,
                new Notification.Builder(context, "messages").build(), false));
        setPhoneLocked(context, true);
        assertTrue(EmbeddedHooks.shouldForwardNotification(context,
                new Notification.Builder(context, "messages").build(), false));
        setPhoneLocked(context, false);
        assertFalse(EmbeddedHooks.shouldForwardNotification(context,
                new Notification.Builder(context, "messages").build(), false));
    }

    @Test public void missingPhoneLockServiceSuppressesWatchAlerts() {
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                return Context.KEYGUARD_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertFalse(EmbeddedHooks.shouldForwardNotification(context,
                new Notification.Builder(context, "messages").build(), false));
    }

    private static void setPhoneLocked(Context context, boolean locked) {
        Shadows.shadowOf(context.getSystemService(KeyguardManager.class)).setKeyguardLocked(locked);
    }
}
