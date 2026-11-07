package za.co.loopmanager;
public final class AccountConnectionErrorChecks {
 public static void main(String[] args) {
  String security=AccountConnectionError.describe(new java.security.NoSuchAlgorithmException("secret"));
  if(!security.contains("verified securely")||security.contains("secret"))throw new AssertionError("Provider failure must not be called offline or expose raw errors");
  if(!AccountConnectionError.describe(new java.net.UnknownHostException()).contains("could not be reached"))throw new AssertionError("Network failure");
  if(!AccountConnectionError.describe(new javax.net.ssl.SSLHandshakeException("secret")).contains("secure connection"))throw new AssertionError("TLS failure");
  if(AccountConnectionError.describe(new IllegalStateException("private server detail")).contains("private server detail"))throw new AssertionError("Raw server errors must not be displayed");
  System.out.println("Account error checks passed: network, TLS and verification remain distinct, without exposing raw error data.");
 }
}
