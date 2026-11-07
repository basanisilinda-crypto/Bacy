package za.co.loopmanager;

import com.google.crypto.tink.subtle.Ed25519Verify;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Verifies server Ed25519 signatures without Android Keystore's external-key import limitation. */
final class AccountSignatures {
    // RFC 8410 SubjectPublicKeyInfo: Ed25519 OID, absent parameters, 32-byte public key.
    private static final byte[] SPKI_PREFIX = {0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x70,0x03,0x21,0x00};
    private AccountSignatures() {}
    static void verify(byte[] spki, byte[] signature, byte[] payload) throws GeneralSecurityException {
        if (spki.length != SPKI_PREFIX.length + Ed25519Verify.PUBLIC_KEY_LEN
                || !Arrays.equals(Arrays.copyOf(spki, SPKI_PREFIX.length), SPKI_PREFIX)) {
            throw new GeneralSecurityException("Invalid server signing key");
        }
        new Ed25519Verify(Arrays.copyOfRange(spki, SPKI_PREFIX.length, spki.length)).verify(signature, payload);
    }
}
