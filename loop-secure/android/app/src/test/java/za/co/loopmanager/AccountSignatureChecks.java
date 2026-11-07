package za.co.loopmanager;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.HexFormat;

/** Pure-Java interoperability and rejection checks. Does not simulate handset management. */
public final class AccountSignatureChecks {
    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    private static void reject(byte[] key, byte[] signature, byte[] payload) throws Exception {
        try { AccountSignatures.verify(key,signature,payload); }
        catch (GeneralSecurityException expected) { return; }
        throw new AssertionError("Invalid authorization was accepted");
    }
    public static final class ImportRefusingFactory extends KeyFactorySpi {
        protected PublicKey engineGeneratePublic(KeySpec spec) throws InvalidKeySpecException { throw new InvalidKeySpecException("External public keys unsupported"); }
        protected PrivateKey engineGeneratePrivate(KeySpec spec) throws InvalidKeySpecException { throw new InvalidKeySpecException(); }
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> spec) throws InvalidKeySpecException { throw new InvalidKeySpecException(); }
        protected Key engineTranslateKey(Key key) throws InvalidKeyException { throw new InvalidKeyException(); }
    }
    public static void main(String[] args) throws Exception {
        byte[] prefix=hex("302a300506032b6570032100");
        byte[] rfcKey=hex("302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] rfcSignature=hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b");
        AccountSignatures.verify(rfcKey,rfcSignature,new byte[0]);
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload="{\"device_id\":\"signature-test-only\",\"revision\":1,\"released\":false}".getBytes(StandardCharsets.UTF_8);
        Signature signer=Signature.getInstance("Ed25519");signer.initSign(pair.getPrivate());signer.update(payload);byte[] signature=signer.sign();
        byte[] spki=pair.getPublic().getEncoded();
        for(Provider p:Security.getProviders()) if(p.getService("KeyFactory","Ed25519")!=null) Security.removeProvider(p.getName());
        Provider restricted=new Provider("ImportRefusing", 1.0, "Represents the Android external-key import limitation") {};
        restricted.put("KeyFactory.Ed25519",ImportRefusingFactory.class.getName());Security.insertProviderAt(restricted,1);
        try { KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));throw new AssertionError("Old import path unexpectedly succeeded"); }
        catch(InvalidKeySpecException expected) { /* Reproduce the failed import independently of our verifier. */ }
        AccountSignatures.verify(spki,signature,payload);
        AccountSignatures.verify(rfcKey,rfcSignature,new byte[0]);
        byte[] changed=payload.clone();changed[changed.length-1]^=1;reject(spki,signature,changed);
        byte[] badSignature=signature.clone();badSignature[0]^=1;reject(spki,badSignature,payload);
        byte[] wrongKey=spki.clone();wrongKey[wrongKey.length-1]^=1;reject(wrongKey,signature,payload);
        byte[] wrongAlgorithm=spki.clone();wrongAlgorithm[8]^=1;reject(wrongAlgorithm,signature,payload);
        reject(Arrays.copyOf(spki,spki.length-1),signature,payload);
        reject(spki,Arrays.copyOf(signature,signature.length-1),payload);
        byte[] trailingKey=Arrays.copyOf(spki,spki.length+1);reject(trailingKey,signature,payload);
        System.out.println("Account signatures passed: RFC8032 vector, server-format signature despite unavailable external-key import, and altered payload/signature/key/encoding rejection.");
    }
}
