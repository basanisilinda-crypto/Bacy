package za.co.bacy.admin;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.HttpsURLConnection;

/** Best-effort polling while an owner session remains authorized; this is not instant push. */
public final class PaymentAlertJob extends JobService {
    private ExecutorService executor;
    private Future<?> running;
    private volatile HttpsURLConnection connection;
    private volatile JobParameters active;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public boolean onStartJob(JobParameters parameters) {
        active = parameters;
        executor = Executors.newSingleThreadExecutor();
        running = executor.submit(() -> {
            boolean retry = false;
            try {
                String token;
                try {
                    token = new Store(this).get("access_token");
                } catch (Exception unavailableSession) {
                    AdminPaymentAlerts.cancel(this);
                    return;
                }
                if (token.isEmpty()) AdminPaymentAlerts.cancel(this);
                else {
                    AdminPaymentAlerts.createChannel(this);
                    if (AdminPaymentAlerts.canNotify(this)) {
                        JSONObject overview = overview(token);
                        if (overview != null && active == parameters && !Thread.currentThread().isInterrupted()) {
                            AdminPaymentAlerts.inspectOverview(this, overview, token);
                        }
                    }
                }
            } catch (Exception ignored) {
                // A temporary network/storage failure preserves the session and retries later.
                retry = true;
            } finally {
                final boolean needsRetry = retry;
                main.post(() -> {
                    if (active != parameters) return;
                    active = null;
                    jobFinished(parameters, needsRetry);
                    if (executor != null) executor.shutdown();
                });
            }
        });
        return true;
    }

    private JSONObject overview(String token) throws Exception {
        HttpsURLConnection request = (HttpsURLConnection) new URL(
                MainActivity.BASE + "/api/admin/overview").openConnection();
        connection = request;
        request.setInstanceFollowRedirects(false);
        request.setConnectTimeout(12000);
        request.setReadTimeout(20000);
        request.setRequestMethod("GET");
        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = request.getResponseCode();
            if (status == 401) {
                AdminPaymentAlerts.unauthorized(this, token);
                return null;
            }
            // Never forward the bearer token to a redirect or parse an untrusted error body.
            if (status < 200 || status >= 300) throw new IOException("Overview unavailable");
            int declaredLength = request.getContentLength();
            if (declaredLength > 2000000) throw new IOException("Overview too large");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = request.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Job stopped");
                    if (bytes.size() + count > 2000000) throw new IOException("Overview too large");
                    bytes.write(buffer, 0, count);
                }
            }
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            request.disconnect();
            if (connection == request) connection = null;
        }
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        if (active == parameters) active = null;
        if (running != null) running.cancel(true);
        HttpsURLConnection request = connection;
        if (request != null) request.disconnect();
        if (executor != null) executor.shutdownNow();
        return true;
    }

    @Override public void onDestroy() {
        active = null;
        if (running != null) running.cancel(true);
        HttpsURLConnection request = connection;
        if (request != null) request.disconnect();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
