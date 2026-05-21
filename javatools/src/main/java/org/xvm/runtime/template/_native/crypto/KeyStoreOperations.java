package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.math.BigInteger;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.time.Duration;
import java.time.Instant;

import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import org.shredzone.acme4j.exception.AcmeException;


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
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        File     file     = new File(sPath);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
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
        try (FileOutputStream out = new FileOutputStream(sPath)) {
            keyStore.store(out, achPwd);
        }
    }

    /**
     * Delete an entry from a keystore, silently ignoring errors (e.g. if the alias
     * does not exist or the keystore file does not exist).
     */
    public static void deleteKeyStoreEntry(String sPath, char[] achPwd, String sAlias) {
        try {
            File file = new File(sPath);
            if (file.exists()) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream in = new FileInputStream(file)) {
                    keyStore.load(in, achPwd);
                }
                if (keyStore.containsAlias(sAlias)) {
                    keyStore.deleteEntry(sAlias);
                    saveKeyStore(keyStore, sPath, achPwd);
                }
            }
        } catch (GeneralSecurityException | IOException ignore) {
            // intentionally silent; enttry may not exist
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
            throws AcmeException, OperatorCreationException,
                   GeneralSecurityException, IOException, InterruptedException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGen.generateKeyPair();

        X500Name   x500Name = new X500Name(sDName);
        Instant    now      = Instant.now();
        BigInteger serial   = BigInteger.valueOf(now.toEpochMilli());
        var        keyInfo  = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                x500Name, serial,
                Date.from(now), Date.from(now.plus(Duration.ofDays(90))),
                x500Name, keyInfo);

        ContentSigner   signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509Certificate cert   = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        KeyStore keyStore = loadOrCreateKeyStore(sStorePath, achPwd);
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

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        SecretKey secretKey = keyGen.generateKey();

        KeyStore keyStore = loadOrCreateKeyStore(sPath, achPwd);
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

        SecretKey pbeKey = SecretKeyFactory.getInstance("PBE")
                .generateSecret(new PBEKeySpec(sPwdValue.toCharArray()));

        KeyStore keyStore = loadOrCreateKeyStore(sPath, achPwd);
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
        KeyStore keyStore = loadOrCreateKeyStore(sPath, achPwd);
        saveKeyStore(keyStore, sPath, achPwdNew);
    }

    /**
     * Extract a key (private or secret) from a PKCS12 keystore file.
     *
     * @return the key, or null if not found or inaccessible
     */
    public static Key extractKey(String sPath, char[] achPwd, String sName)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(sPath)) {
            keyStore.load(in, achPwd);
        }
        return keyStore.getKey(sName, achPwd);
    }
}
