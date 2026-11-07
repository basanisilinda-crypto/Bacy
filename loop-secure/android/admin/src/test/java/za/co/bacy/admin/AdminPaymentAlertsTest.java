package za.co.bacy.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AdminPaymentAlertsTest {
    @Test
    public void unseenPendingClaimsNotifyOnceAcrossReorderingAndPersistence() throws Exception {
        JSONArray claims = new JSONArray()
                .put(claim("pending-a", "pending", 100))
                .put(claim("approved-a", "approved", 110))
                .put(claim("pending-b", "pending", 120))
                .put(claim("pending-a", "pending", 100))
                .put(claim("rejected-a", "rejected", 130));

        AdminPaymentAlerts.Plan first = AdminPaymentAlerts.plan(new JSONObject(), claims);
        assertEquals(2, first.newClaims);
        assertEquals(2, first.pendingClaims);

        // A JSON round trip represents another process reading the saved history.
        AdminPaymentAlerts.Plan repeated = AdminPaymentAlerts.plan(
                persisted(first.history), reversed(claims));
        assertEquals(0, repeated.newClaims);
        assertEquals(2, repeated.pendingClaims);

        JSONArray withNewClaim = reversed(claims).put(claim("pending-c", "pending", 140));
        AdminPaymentAlerts.Plan next = AdminPaymentAlerts.plan(
                persisted(repeated.history), withNewClaim);
        assertEquals(1, next.newClaims);
        assertEquals(3, next.pendingClaims);
    }

    @Test
    public void returningPendingStateDoesNotRepeatAnAlreadyNotifiedClaim() throws Exception {
        AdminPaymentAlerts.Plan pending = AdminPaymentAlerts.plan(new JSONObject(),
                new JSONArray().put(claim("same-claim", "pending", 100)));
        assertEquals(1, pending.newClaims);

        AdminPaymentAlerts.Plan approved = AdminPaymentAlerts.plan(persisted(pending.history),
                new JSONArray().put(claim("same-claim", "approved", 100)));
        assertEquals(0, approved.newClaims);
        assertEquals(0, approved.pendingClaims);

        AdminPaymentAlerts.Plan pendingAgain = AdminPaymentAlerts.plan(
                persisted(approved.history),
                new JSONArray().put(claim("same-claim", "pending", 100)));
        assertEquals(0, pendingAgain.newClaims);
        assertEquals(1, pendingAgain.pendingClaims);
    }

    @Test
    public void claimFirstSeenApprovedCanNotifyWhenItFirstBecomesPending() throws Exception {
        AdminPaymentAlerts.Plan approved = AdminPaymentAlerts.plan(new JSONObject(),
                new JSONArray().put(claim("same-claim", "approved", 100)));
        assertEquals(0, approved.newClaims);
        assertEquals(0, approved.pendingClaims);

        AdminPaymentAlerts.Plan pending = AdminPaymentAlerts.plan(persisted(approved.history),
                new JSONArray().put(claim("same-claim", "pending", 100)));
        assertEquals(1, pending.newClaims);
        assertEquals(1, pending.pendingClaims);
        assertEquals(0, AdminPaymentAlerts.plan(persisted(pending.history),
                new JSONArray().put(claim("same-claim", "pending", 100))).newClaims);
    }

    @Test
    public void overflowIsBoundedWithoutReplayingTheSameLargeSnapshot() throws Exception {
        JSONArray claims = new JSONArray();
        for (int i = 0; i < 230; i++) {
            claims.put(claim(claimId(i), "pending", 1000 + i));
        }

        AdminPaymentAlerts.Plan first = AdminPaymentAlerts.plan(new JSONObject(), claims);
        assertEquals(200, first.newClaims);
        assertEquals(230, first.pendingClaims);
        assertEquals(200, first.history.getJSONArray("seen").length());

        AdminPaymentAlerts.Plan repeated = AdminPaymentAlerts.plan(
                persisted(first.history), reversed(claims));
        assertEquals(0, repeated.newClaims);
        assertEquals(230, repeated.pendingClaims);
        assertEquals(200, repeated.history.getJSONArray("seen").length());

        // A later server snapshot may contain only an old, evicted claim.
        AdminPaymentAlerts.Plan oldClaimOnly = AdminPaymentAlerts.plan(
                persisted(repeated.history),
                new JSONArray().put(claim(claimId(0), "pending", 1000)));
        assertEquals(0, oldClaimOnly.newClaims);

        claims.put(claim("newest-claim", "pending", 2000));
        AdminPaymentAlerts.Plan next = AdminPaymentAlerts.plan(
                persisted(repeated.history), claims);
        assertEquals(1, next.newClaims);
        assertEquals(200, next.history.getJSONArray("seen").length());
        assertEquals(0, AdminPaymentAlerts.plan(
                persisted(next.history), reversed(claims)).newClaims);
    }

    @Test
    public void tiedSubmissionTimesHaveDeterministicOverflowRetention() throws Exception {
        JSONArray claims = new JSONArray();
        for (int i = 0; i < 225; i++) {
            claims.put(claim(claimId(i), "pending", 1000));
        }

        AdminPaymentAlerts.Plan forward = AdminPaymentAlerts.plan(new JSONObject(), claims);
        AdminPaymentAlerts.Plan backward = AdminPaymentAlerts.plan(
                new JSONObject(), reversed(claims));
        assertEquals(200, forward.newClaims);
        assertEquals(200, forward.history.getJSONArray("seen").length());
        assertEquals(forward.history.getJSONArray("seen").toString(),
                backward.history.getJSONArray("seen").toString());
        assertEquals(0, AdminPaymentAlerts.plan(
                persisted(forward.history), reversed(claims)).newClaims);

        // The ID breaks timestamp ties, so this claim is newer than the cutoff.
        claims.put(claim("zzzz-new-claim", "pending", 1000));
        AdminPaymentAlerts.Plan next = AdminPaymentAlerts.plan(
                persisted(forward.history), claims);
        assertEquals(1, next.newClaims);
        assertEquals(0, AdminPaymentAlerts.plan(persisted(next.history), claims).newClaims);
    }

    @Test
    @Config(sdk = {30, 33})
    public void notificationIsPrivateAndUsesAnExplicitImmutablePaymentsIntent() {
        Context context = RuntimeEnvironment.getApplication();
        Notification notification = AdminPaymentAlerts.notification(context, 2);

        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.getSmallIcon());
        assertNotNull(notification.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertNotNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT));
        String copy = (notification.extras.getCharSequence(Notification.EXTRA_TITLE) + " "
                + notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                .toLowerCase(Locale.ROOT);
        assertTrue(copy.contains("payment"));
        assertTrue(copy.contains("does not confirm funds received"));
        assertNotNull(notification.publicVersion);
        assertNotNull(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertNotNull(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertNull(notification.fullScreenIntent);

        PendingIntent pendingIntent = notification.contentIntent;
        assertNotNull(pendingIntent);
        assertTrue(shadowOf(pendingIntent).isImmutable());
        assertTrue(shadowOf(pendingIntent).isActivity());
        Intent intent = shadowOf(pendingIntent).getSavedIntent();
        assertEquals(new ComponentName(context, MainActivity.class), intent.getComponent());
        assertTrue(intent.getBooleanExtra(AdminPaymentAlerts.EXTRA_OPEN_PAYMENTS, false));
        assertNull(intent.getData());
        assertEquals(1, intent.getExtras().size());
    }

    @Test
    @Config(sdk = {30, 33})
    public void paymentPollingJobPersistsWithNetworkAndTheMinimumPeriodicInterval() {
        Context context = RuntimeEnvironment.getApplication();
        JobInfo job = AdminPaymentAlerts.jobInfo(context);

        assertEquals(new ComponentName(context, PaymentAlertJob.class), job.getService());
        assertTrue(job.isPersisted());
        assertTrue(job.isPeriodic());
        assertEquals(15 * 60 * 1000L, job.getIntervalMillis());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.getNetworkType());
        assertFalse(job.isRequireCharging());
        assertFalse(job.isRequireDeviceIdle());
    }

    private static JSONObject claim(String id, String state, long submitted) throws Exception {
        return new JSONObject().put("id", id).put("state", state).put("submitted", submitted);
    }

    private static String claimId(int index) {
        return String.format(Locale.ROOT, "claim-%04d", index);
    }

    private static JSONObject persisted(JSONObject history) throws Exception {
        return new JSONObject(history.toString());
    }

    private static JSONArray reversed(JSONArray claims) throws Exception {
        JSONArray reversed = new JSONArray();
        for (int i = claims.length() - 1; i >= 0; i--) {
            reversed.put(claims.getJSONObject(i));
        }
        return reversed;
    }
}
