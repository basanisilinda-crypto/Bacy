package za.co.loopmanager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLException;

/** User-facing failure categories; never include credentials, payloads or raw server errors. */
final class AccountConnectionError {
    private AccountConnectionError() {}
    static String describe(Exception failure) {
        if (failure instanceof SSLException) return "A secure connection to Bacy could not be established. Check the phone's automatic date and time, then refresh.";
        if (failure instanceof IOException) return "The Bacy server could not be reached. Check Wi-Fi or mobile data, then refresh.";
        if (failure instanceof GeneralSecurityException || failure instanceof SecurityException) return "The account reply could not be verified securely. Refresh, or ask your administrator for an app update.";
        return "The account could not be loaded. Refresh, or contact your administrator if this continues.";
    }
}
