package za.co.mdmtest;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** TLS-only Supabase client. Never put service_role secrets in Android applications. */
public final class CloudApi {
    private CloudApi() {}
    public static boolean configured() {
        return BuildConfig.SUPABASE_URL.startsWith("https://")
            && !BuildConfig.SUPABASE_ANON_KEY.isEmpty();
    }
    public static String request(String method, String path, String jwt, JSONObject body) throws Exception {
        if (!configured()) throw new IllegalStateException("Cloud service is not configured in this APK.");
        URL url = new URL(BuildConfig.SUPABASE_URL.replaceAll("/+$", "") + path);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("HTTPS required");
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
        c.setRequestProperty("Authorization", "Bearer " + (jwt == null ? BuildConfig.SUPABASE_ANON_KEY : jwt));
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json");
        try {
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (stream != null) try (InputStream in = stream) {
                byte[] block = new byte[4096]; int n;
                while ((n = in.read(block)) != -1) {
                    buffer.write(block, 0, n);
                    if (buffer.size() > 512000) throw new IllegalStateException("Unexpectedly large server reply");
                }
            }
            String response = buffer.toString(StandardCharsets.UTF_8.name());
            if (status >= 400) {
                String error = response;
                try {
                    JSONObject e = new JSONObject(response);
                    error = e.optString("message", e.optString("msg", e.optString("error_description", response)));
                } catch (Exception ignored) {}
                throw new IllegalStateException("Server " + status + ": " + error.substring(0, Math.min(error.length(), 220)));
            }
            return response;
        } finally { c.disconnect(); }
    }
}
