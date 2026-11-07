package za.co.loopmanager;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreSpi;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import javax.crypto.spec.SecretKeySpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35})
public class OwnershipCheckReceiverTest {
    private static final String NONCE = "1234567890123456";
    private static final String PREFIX = "LoopSecureOwnerCheck:v1:" + NONCE + ":";
    private static final String PROVIDER_NAME = "LoopOwnershipCheckTestKeystore";
    private Context context;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE).edit().clear().commit();
        Security.removeProvider(PROVIDER_NAME);
        Security.insertProviderAt(new TestKeyStoreProvider(), 1);
    }

    @After public void removeTestKeyStore() {
        Security.removeProvider(PROVIDER_NAME);
    }

    @Test public void unownedPhoneDoesNotPassOwnershipGate() {
        assertEquals("owner=false;active=false;linked=false", OwnershipCheckReceiver.inspect(context));
    }

    @Test public void activeAdminAloneDoesNotMeanDeviceOwner() {
        Shadows.shadowOf(policy()).setActiveAdmin(PolicyController.admin(context));
        assertEquals("owner=false;active=true;linked=false", OwnershipCheckReceiver.inspect(context));
    }

    @Test public void anotherOwnerDoesNotMakeOurActiveAdminDeviceOwner() {
        Shadows.shadowOf(policy()).setDeviceOwner(new ComponentName("other.owner", "other.owner.Admin"));
        Shadows.shadowOf(policy()).setActiveAdmin(PolicyController.admin(context));
        assertEquals("owner=false;active=true;linked=false", OwnershipCheckReceiver.inspect(context));
    }

    @Test public void ownerPackageStillRequiresOurExactAdminComponentToBeActive() {
        Shadows.shadowOf(policy()).setDeviceOwner(new ComponentName(context.getPackageName(), "other.Admin"));
        assertTrue(policy().isDeviceOwnerApp(context.getPackageName()));
        assertFalse(policy().isAdminActive(PolicyController.admin(context)));
        assertEquals("owner=true;active=false;linked=false", OwnershipCheckReceiver.inspect(context));
    }

    @Test public void orderedProbeReportsActualOwnerAndActiveAdminWithCurrentNonce() {
        Shadows.shadowOf(policy()).setDeviceOwner(PolicyController.admin(context));
        Shadows.shadowOf(policy()).setActiveAdmin(PolicyController.admin(context));
        ProbeResult result = probe(NONCE);
        assertEquals(1, result.code);
        assertEquals(PREFIX + "owner=true;active=true;linked=false", result.data);
    }

    @Test public void linkedAccountIsPresenceOnlyAndItsEncryptedRecordIsPreserved() throws Exception {
        String deviceId = "private-device-id-never-in-probe";
        String token = "private-device-token-never-in-probe";
        new Store(context).establishDevice(deviceId, token);
        String ciphertext = context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE)
                .getString("device_session", null);
        ProbeResult result = probe(NONCE);
        assertEquals(PREFIX + "owner=false;active=false;linked=true", result.data);
        assertFalse(result.data.contains(deviceId));
        assertFalse(result.data.contains(token));
        assertFalse(result.data.contains(ciphertext));
        assertEquals(ciphertext, context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE)
                .getString("device_session", null));
        assertEquals(token, new Store(context).get("device_token"));
    }

    @Test public void corruptLegacyTokenIsUnknownAndDoesNotLeakItsContents() {
        String corrupt = "private-corrupt-token-not-valid-ciphertext";
        context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE).edit()
                .putString("device_token", corrupt).commit();
        ProbeResult result = probe(NONCE);
        assertEquals(PREFIX + "owner=false;active=false;linked=unknown", result.data);
        assertFalse(result.data.contains(corrupt));
    }

    @Test public void corruptSessionCannotFallBackToAnApparentlyEmptyLegacyToken() {
        String corrupt = "private-corrupt-device-session";
        context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE).edit()
                .putString("device_session", corrupt).commit();
        assertEquals("owner=false;active=false;linked=unknown", OwnershipCheckReceiver.inspect(context));
        assertEquals(corrupt, context.getSharedPreferences("loop.secure", Context.MODE_PRIVATE)
                .getString("device_session", null));
    }

    @Test public void missingPolicyServiceCannotClaimVerifiedOwnership() {
        Context unavailablePolicy = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                return Context.DEVICE_POLICY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertEquals("owner=unknown;active=unknown;linked=false", OwnershipCheckReceiver.inspect(unavailablePolicy));
    }

    @Test public void missingAndInvalidNoncesClearAnyPriorOrderedBroadcastResult() {
        String[] invalidNonces = {null, "", "1", "123456789012345678901", "12;owner=true", "12\n", "non-numeric"};
        for (String nonce : invalidNonces) {
            ProbeResult result = probe(nonce);
            assertEquals("Invalid nonce must not return success: " + nonce, 0, result.code);
            assertNull("Invalid nonce must clear stale result data: " + nonce, result.data);
        }
    }

    @Test public void manifestRequiresDumpPermissionForTheExportedProbe() throws Exception {
        ActivityInfo receiver = context.getPackageManager().getReceiverInfo(
                new ComponentName(context, OwnershipCheckReceiver.class), 0);
        assertTrue(receiver.exported);
        assertEquals("android.permission.DUMP", receiver.permission);
    }

    private DevicePolicyManager policy() {
        return context.getSystemService(DevicePolicyManager.class);
    }

    private ProbeResult probe(String nonce) {
        ProbeResult result = new ProbeResult();
        String action = "za.co.loopmanager.test.OWNERSHIP_PROBE";
        OwnershipCheckReceiver receiver = new OwnershipCheckReceiver();
        // Robolectric needs a filter to dispatch a receiver; the production manifest remains explicit-only.
        ContextCompat.registerReceiver(context, receiver, new IntentFilter(action),
                "za.co.loopmanager.permission.INTERNAL", null, ContextCompat.RECEIVER_NOT_EXPORTED);
        Intent request = new Intent(action).setPackage(context.getPackageName());
        if (nonce != null) request.putExtra("nonce", nonce);
        try {
            context.sendOrderedBroadcast(request, null, new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    result.delivered = true;
                    result.code = getResultCode();
                    result.data = getResultData();
                }
            }, new Handler(Looper.getMainLooper()), 99, "stale-unverified-result", null);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        } finally {
            context.unregisterReceiver(receiver);
        }
        assertTrue("The ordered broadcast result must be delivered", result.delivered);
        return result;
    }

    private static final class ProbeResult {
        boolean delivered;
        int code;
        String data;
    }

    /** Allows the real Store AES/GCM implementation to run without a physical Android keystore. */
    public static final class TestKeyStoreProvider extends Provider {
        public TestKeyStoreProvider() {
            super(PROVIDER_NAME, 1.0, "In-memory ownership probe test keystore");
            put("KeyStore.AndroidKeyStore", TestKeyStore.class.getName());
        }
    }

    public static final class TestKeyStore extends KeyStoreSpi {
        private static final String ALIAS = "loop.local.v1";
        private static final Key KEY = new SecretKeySpec(new byte[32], "AES");
        @Override public Key engineGetKey(String alias, char[] password) { return ALIAS.equals(alias) ? KEY : null; }
        @Override public boolean engineContainsAlias(String alias) { return ALIAS.equals(alias); }
        @Override public boolean engineIsKeyEntry(String alias) { return ALIAS.equals(alias); }
        @Override public Enumeration<String> engineAliases() { return Collections.enumeration(Collections.singleton(ALIAS)); }
        @Override public int engineSize() { return 1; }
        @Override public Date engineGetCreationDate(String alias) { return new Date(0); }
        @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
        @Override public Certificate engineGetCertificate(String alias) { return null; }
        @Override public String engineGetCertificateAlias(Certificate certificate) { return null; }
        @Override public boolean engineIsCertificateEntry(String alias) { return false; }
        @Override public void engineLoad(InputStream stream, char[] password) { }
        @Override public void engineStore(OutputStream stream, char[] password) { }
        @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
            throw new UnsupportedOperationException();
        }
        @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
            throw new UnsupportedOperationException();
        }
        @Override public void engineSetCertificateEntry(String alias, Certificate certificate) {
            throw new UnsupportedOperationException();
        }
        @Override public void engineDeleteEntry(String alias) { throw new UnsupportedOperationException(); }
    }
}
