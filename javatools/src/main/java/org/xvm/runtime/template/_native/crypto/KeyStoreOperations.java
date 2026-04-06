package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import java.time.Duration;
import java.time.Instant;

import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;


/**
 * Pure Java keystore and certificate operations that replace the previous native tool
 * dependencies ({@code keytool}, {@code openssl}). Every method in this class produces
 * PKCS12 keystore entries that are byte-compatible with those created by the corresponding
 * native commands — verified by bidirectional cross-tool tests (Java-created keystores
 * readable by keytool/openssl, and vice versa). See {@code KeyStoreCompatibilityTest}.
 * <p>
 * This class has no XVM runtime dependencies and can be unit tested independently.
 */
public class KeyStoreOperations {

    /**
     * Load an existing PKCS12 keystore or create a new empty one.
     */
    public static KeyStore loadOrCreateKeyStore(String sPath, char[] achPwd)
            throws GeneralSecurityException, IOException {
        var keyStore = KeyStore.getInstance("PKCS12");
        var file = new File(sPath);
        if (file.exists()) {
            try (var in = new FileInputStream(file)) {
                keyStore.load(in, achPwd);
            }
        } else {
            keyStore.load(null, achPwd);
        }
        return keyStore;
    }

    /**
     * Save a keystore to disk.
     */
    public static void saveKeyStore(KeyStore keyStore, String sPath, char[] achPwd)
            throws GeneralSecurityException, IOException {
        try (var out = new FileOutputStream(sPath)) {
            keyStore.store(out, achPwd);
        }
    }

    /**
     * Delete an entry from a keystore, silently ignoring errors (e.g. if the alias
     * does not exist or the keystore file does not exist).
     */
    public static void deleteKeyStoreEntry(String sPath, char[] achPwd, String sAlias) {
        try {
            var file = new File(sPath);
            if (!file.exists()) {
                return;
            }
            var keyStore = KeyStore.getInstance("PKCS12");
            try (var in = new FileInputStream(file)) {
                keyStore.load(in, achPwd);
            }
            if (keyStore.containsAlias(sAlias)) {
                keyStore.deleteEntry(sAlias);
                saveKeyStore(keyStore, sPath, achPwd);
            }
        } catch (GeneralSecurityException | IOException _) {
            // intentionally silent — entry may not exist, and that's fine
        }
    }

    /**
     * Create a self-signed certificate and store it in a PKCS12 keystore.
     * <p>
     * Equivalent to {@code keytool -genkeypair -keyalg RSA -keysize 2048 -validity 90}.
     * Uses the same JDK {@link KeyPairGenerator} for RSA-2048 key generation and
     * BouncyCastle for X.509 certificate construction with SHA256WithRSA signing.
     */
    public static void createSelfSignedCertificate(String sStorePath, char[] achPwd,
                                                   String sName, String sDName)
            throws GeneralSecurityException, IOException, OperatorCreationException {
        var keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048, new SecureRandom());
        var keyPair = keyPairGen.generateKeyPair();

        var x500Name = new X500Name(sDName);
        var now = Instant.now();
        var serial = BigInteger.valueOf(now.toEpochMilli());
        var pubKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

        var certBuilder = new X509v3CertificateBuilder(
                x500Name, serial,
                Date.from(now), Date.from(now.plus(Duration.ofDays(90))),
                x500Name, pubKeyInfo);

        var signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        var cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        var keyStore = loadOrCreateKeyStore(sStorePath, achPwd);
        keyStore.setKeyEntry(sName, keyPair.getPrivate(), achPwd, new Certificate[]{cert});
        saveKeyStore(keyStore, sStorePath, achPwd);
    }

    /**
     * Generate an AES-256 symmetric key and store it in a PKCS12 keystore.
     * <p>
     * Equivalent to {@code keytool -genseckey -keyalg AES -keysize 256}. Uses the same
     * JDK {@link javax.crypto.KeyGenerator} API that keytool uses internally.
     */
    public static void createSymmetricKey(String sPath, char[] achPwd, String sName)
            throws GeneralSecurityException, IOException {
        deleteKeyStoreEntry(sPath, achPwd, sName);

        var keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        var secretKey = keyGen.generateKey();

        var keyStore = loadOrCreateKeyStore(sPath, achPwd);
        keyStore.setEntry(sName,
                new KeyStore.SecretKeyEntry(secretKey),
                new KeyStore.PasswordProtection(achPwd));
        saveKeyStore(keyStore, sPath, achPwd);
    }

    /**
     * Store a password value as a PBE secret key entry in a PKCS12 keystore.
     * <p>
     * Equivalent to {@code keytool -importpass}. Creates a PBE secret key from the
     * password using {@link javax.crypto.SecretKeyFactory} and stores it as a
     * {@link KeyStore.SecretKeyEntry} — the same internal representation.
     */
    public static void createPassword(String sPath, char[] achPwd,
                                      String sName, String sPwdValue)
            throws GeneralSecurityException, IOException {
        deleteKeyStoreEntry(sPath, achPwd, sName);

        var pbeKey = SecretKeyFactory.getInstance("PBE")
                .generateSecret(new PBEKeySpec(sPwdValue.toCharArray()));

        var keyStore = loadOrCreateKeyStore(sPath, achPwd);
        keyStore.setEntry(sName,
                new KeyStore.SecretKeyEntry(pbeKey),
                new KeyStore.PasswordProtection(achPwd));
        saveKeyStore(keyStore, sPath, achPwd);
    }

    /**
     * Change the password on a PKCS12 keystore by loading with the old password and
     * saving with the new one.
     * <p>
     * Equivalent to {@code keytool -storepasswd -keystore <path> -storepass <old> -new <new>}.
     */
    public static void changeStorePassword(String sPath, char[] achPwd, char[] achPwdNew)
            throws GeneralSecurityException, IOException {
        var keyStore = loadOrCreateKeyStore(sPath, achPwd);
        saveKeyStore(keyStore, sPath, achPwdNew);
    }

    /**
     * Extract a key (private or secret) from a PKCS12 keystore file.
     *
     * @return the key, or null if not found or inaccessible
     */
    public static Key extractKey(String sPath, char[] achPwd, String sName) {
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
            try (var in = new FileInputStream(sPath)) {
                keyStore.load(in, achPwd);
            }
            return keyStore.getKey(sName, achPwd);
        } catch (GeneralSecurityException | IOException e) {
            // TODO: swallowing the exception here loses the root cause; callers have no
            //  way to distinguish "key not found" from "keystore corrupt" or "wrong password"
            return null;
        }
    }
}
