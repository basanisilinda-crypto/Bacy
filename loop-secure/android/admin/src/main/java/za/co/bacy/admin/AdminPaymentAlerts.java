package za.co.bacy.admin;

import android.Manifest;
import android.app.job.JobScheduler;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owner-session payment claim alerts. A claim is never described as verified bank funds. */
final class AdminPaymentAlerts {
    static final String EXTRA_OPEN_PAYMENTS = "open_payment_review";
    static final int JOB_ID = 140037;
    static final int NOTIFICATION_ID = 140038;
    static final String CHANNEL_ID = "payment_claim_review";
    private static final String HISTORY_KEY = "payment_alert_history_v1";
    private static final int MAX_SEEN = 200;
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    private AdminPaymentAlerts() {}

    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Payment claim review", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Alerts when an EFT payment claim needs administrator review.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static JobInfo jobInfo(Context context) {
        return new JobInfo.Builder(JOB_ID, new ComponentName(context, PaymentAlertJob.class))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
    }

    static synchronized void schedule(Context context) {
        try {
            if (new Store(context).get("access_token").isEmpty()) {
                cancel(context);
                return;
            }
            createChannel(context);
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            // Do not reset the periodic window whenever foreground records refresh.
            if (scheduler != null && scheduler.getPendingJob(JOB_ID) == null) {
                scheduler.schedule(jobInfo(context));
            }
        } catch (Exception ignored) {
            cancel(context);
        }
    }

    static synchronized void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(JOB_ID);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    static synchronized void unauthorized(Context context, String requestedToken) {
        try {
            Store store = new Store(context);
            // A late response from a revoked session must not erase a newly approved session.
            if (!requestedToken.equals(store.get("access_token"))) return;
            store.put("access_token", "");
            cancel(context);
        } catch (Exception ignored) {
            cancel(context);
        }
    }

    static void inspectOverview(Context context, JSONObject overview, String requestedToken) {
        synchronized (AdminPaymentAlerts.class) {
            try {
                Store store = new Store(context);
                String currentToken = store.get("access_token");
                if (currentToken.isEmpty()) {
                    cancel(context);
                    return;
                }
                if (!currentToken.equals(requestedToken)) return;
                createChannel(context);
                // Keep undelivered claims eligible if the owner grants permission later.
                if (!canNotify(context)) return;
                String saved = store.get(HISTORY_KEY);
                Plan result = plan(saved.isEmpty() ? new JSONObject() : new JSONObject(saved),
                        overview.optJSONArray("claims"));
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager == null) return;
                if (result.newClaims > 0) manager.notify(NOTIFICATION_ID,
                        notification(context, result.newClaims));
                else if (result.pendingClaims == 0) manager.cancel(NOTIFICATION_ID);
                // The history contains claim identifiers/states, so use the existing encrypted store.
                store.put(HISTORY_KEY, result.history.toString());
            } catch (Exception ignored) {
                // Notification/storage failures do not break record refresh or expose session details.
            }
        }
    }

    static Notification notification(Context context, int newClaims) {
        Intent open = new Intent(context, MainActivity.class)
                .setAction("za.co.bacy.admin.REVIEW_PAYMENT_CLAIMS")
                .putExtra(EXTRA_OPEN_PAYMENTS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = newClaims == 1 ? "Payment claim awaiting review"
                : newClaims + " payment claims awaiting review";
        Notification publicVersion = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Loop Secure Admin")
                .setContentText("Open the admin app to review payment claims.")
                .build();
        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText("Review submitted EFT claims. A claim does not confirm funds received.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setNumber(newClaims)
                .build();
    }

    static final class Plan {
        final JSONObject history;
        final int newClaims;
        final int pendingClaims;
        Plan(JSONObject history, int newClaims, int pendingClaims) {
            this.history = history;
            this.newClaims = newClaims;
            this.pendingClaims = pendingClaims;
        }
    }

    private static final class Claim {
        final String id;
        String state;
        long submitted;
        boolean notified;
        Claim(String id, String state, long submitted, boolean notified) {
            this.id = id;
            this.state = state;
            this.submitted = submitted;
            this.notified = notified;
        }
    }

    private static final Comparator<Claim> OLDEST_FIRST = Comparator
            .comparingLong((Claim claim) -> claim.submitted).thenComparing(claim -> claim.id);

    /** Deterministic, bounded ledger shared by foreground refresh and scheduled jobs. */
    static Plan plan(JSONObject history, JSONArray claims) throws JSONException {
        Map<String, Claim> known = new HashMap<>();
        JSONArray previous = history.optJSONArray("seen");
        if (previous != null) for (int i = 0; i < previous.length(); i++) {
            JSONObject item = previous.optJSONObject(i);
            if (item == null || item.optString("id").isEmpty()) continue;
            String id = item.optString("id");
            known.put(id, new Claim(id, item.optString("state"), item.optLong("submitted"),
                    item.optBoolean("notified")));
        }
        JSONObject cutoffObject = history.optJSONObject("cutoff");
        Claim cutoff = cutoffObject == null ? null : new Claim(cutoffObject.optString("id"),
                "", cutoffObject.optLong("submitted"), false);
        Map<String, Claim> incoming = new HashMap<>();
        if (claims != null) for (int i = 0; i < claims.length(); i++) {
            JSONObject item = claims.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id"), state = item.optString("state");
            if (id.isEmpty() || id.length() > 200 || state.length() > 40) continue;
            Claim candidate = new Claim(id, state, item.optLong("submitted"), false);
            Claim duplicate = incoming.get(id);
            // Inconsistent duplicate rows must not turn an already resolved claim into an alert.
            if (duplicate == null || !"pending".equals(state)) incoming.put(id, candidate);
        }
        List<Claim> recentIncoming = new ArrayList<>(incoming.values());
        recentIncoming.sort(OLDEST_FIRST.reversed());
        int pending = 0, added = 0;
        for (Claim claim : incoming.values()) if ("pending".equals(claim.state)) pending++;
        for (int i = 0; i < recentIncoming.size(); i++) {
            Claim claim = recentIncoming.get(i), old = known.get(claim.id);
            if (old == null && (i >= MAX_SEEN || (cutoff != null
                    && OLDEST_FIRST.compare(claim, cutoff) <= 0))) continue;
            if (old != null) claim.notified = old.notified;
            if ("pending".equals(claim.state) && !claim.notified) {
                claim.notified = true;
                added++;
            }
            known.put(claim.id, claim);
        }
        List<Claim> retained = new ArrayList<>(known.values());
        retained.sort(OLDEST_FIRST.reversed());
        JSONArray seen = new JSONArray();
        for (int i = 0; i < retained.size(); i++) {
            Claim claim = retained.get(i);
            if (i < MAX_SEEN) seen.put(new JSONObject().put("id", claim.id)
                    .put("state", claim.state).put("submitted", claim.submitted)
                    .put("notified", claim.notified));
            else if (cutoff == null || OLDEST_FIRST.compare(claim, cutoff) > 0) cutoff = claim;
        }
        // Also remember the overflow boundary on an initial response larger than the ledger.
        if (recentIncoming.size() > MAX_SEEN) {
            Claim boundary = recentIncoming.get(MAX_SEEN);
            if (cutoff == null || OLDEST_FIRST.compare(boundary, cutoff) > 0) cutoff = boundary;
        }
        JSONObject updated = new JSONObject().put("seen", seen);
        if (cutoff != null) updated.put("cutoff", new JSONObject().put("id", cutoff.id)
                .put("submitted", cutoff.submitted));
        return new Plan(updated, added, pending);
    }
}
