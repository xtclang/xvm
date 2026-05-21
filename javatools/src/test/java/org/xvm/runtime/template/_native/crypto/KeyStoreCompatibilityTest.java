package org.xvm.runtime.template._native.crypto;


import java.io.File;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Drop-in compatibility tests that verify the pure Java implementation produces keystores
 * and entries that are interchangeable with those created by the native keytool/openssl
 * commands.
 * <p>
 * Tests annotated with {@code @EnabledIf("isKeytoolAvailable")} run only when keytool is
 * on the PATH. Tests annotated with {@code @EnabledIf("isOpensslAvailable")} run only
 * when openssl is on the PATH.
 */
public class KeyStoreCompatibilityTest {

    private static final char[] PASSWORD = "compat-test".toCharArray();
    private static final String PASSWORD_STR = "compat-test";

    @TempDir
    File tempDir;

    // ----- keytool cross-compatibility tests ----------------------------------------------------

    /**
     * Create a keystore with keytool, then verify our Java code can read and extract its
     * contents correctly. This proves our code is a drop-in reader for keytool output.
     */
    @Test
    @EnabledIf("isKeytoolAvailable")
    public void testJavaReadsKeytoolSelfSignedCert() throws Exception {
        var path = new File(tempDir, "keytool-created.p12").getAbsolutePath();

        // create a self-signed cert using keytool (the old way)
        var exitCode = new ProcessBuilder(
                "keytool", "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-validity", "90",
                "-alias", "testcert",
                "-dname", "CN=compat.example.com,O=Test,C=US",
                "-storetype", "PKCS12",
                "-keystore", path,
                "-storepass", PASSWORD_STR
        ).redirectErrorStream(true).start().waitFor();
        assertEquals(0, exitCode, "keytool command failed");

        // read the keytool-created keystore using our Java code
        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("testcert"));

        var cert = (X509Certificate) keyStore.getCertificate("testcert");
        assertNotNull(cert);
        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=compat.example.com"));

        var key = KeyStoreOperations.extractKey(path, PASSWORD, "testcert");
        assertNotNull(key);
        assertEquals("RSA", key.getAlgorithm());
    }

    /**
     * Create a keystore with our Java code, then verify keytool can read and list its
     * contents. This proves our output is a drop-in replacement for keytool output.
     */
    @Test
    @EnabledIf("isKeytoolAvailable")
    public void testKeytoolReadsJavaSelfSignedCert() throws Exception {
        var path = new File(tempDir, "java-created.p12").getAbsolutePath();

        // create a self-signed cert using our Java code (the new way)
        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "testcert", "CN=compat.example.com,O=Test,C=US");

        // verify keytool can read it
        var process = new ProcessBuilder(
                "keytool", "-list", "-v",
                "-keystore", path,
                "-storepass", PASSWORD_STR,
                "-alias", "testcert"
        ).redirectErrorStream(true).start();

        var output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "keytool -list failed: " + output);
        assertTrue(output.contains("compat.example.com"), "keytool should see our CN");
        assertTrue(output.contains("RSA"), "keytool should see RSA key type");
    }

    /**
     * Create an AES symmetric key with keytool, verify our Java code can extract it.
     * Then create one with Java and verify keytool can read it.
     */
    @Test
    @EnabledIf("isKeytoolAvailable")
    public void testSymmetricKeyInterop() throws Exception {
        var keytoolPath = new File(tempDir, "keytool-sym.p12").getAbsolutePath();
        var javaPath = new File(tempDir, "java-sym.p12").getAbsolutePath();

        // keytool creates symmetric key
        var exitCode = new ProcessBuilder(
                "keytool", "-genseckey", "-keyalg", "AES", "-keysize", "256",
                "-alias", "aeskey",
                "-storetype", "PKCS12",
                "-keystore", keytoolPath,
                "-storepass", PASSWORD_STR
        ).redirectErrorStream(true).start().waitFor();
        assertEquals(0, exitCode, "keytool -genseckey failed");

        // Java reads keytool's key
        var keytoolKey = KeyStoreOperations.extractKey(keytoolPath, PASSWORD, "aeskey");
        assertNotNull(keytoolKey, "should be able to read keytool's AES key");
        assertEquals("AES", keytoolKey.getAlgorithm());
        assertEquals(32, keytoolKey.getEncoded().length, "should be 256-bit");

        // Java creates symmetric key
        KeyStoreOperations.createSymmetricKey(javaPath, PASSWORD, "aeskey");

        // keytool reads Java's key
        var process = new ProcessBuilder(
                "keytool", "-list", "-v",
                "-keystore", javaPath,
                "-storepass", PASSWORD_STR,
                "-alias", "aeskey"
        ).redirectErrorStream(true).start();

        var output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "keytool can't read Java's AES key: " + output);
    }

    // ----- openssl cross-compatibility tests ----------------------------------------------------

    /**
     * Create a self-signed cert with our Java code, export the private key, and verify
     * openssl can parse it. This proves our keystore entries are compatible with openssl
     * PKCS12 handling.
     */
    @Test
    @EnabledIf("isOpensslAvailable")
    public void testOpensslReadJavaKeystore() throws Exception {
        var path = new File(tempDir, "openssl-test.p12").getAbsolutePath();

        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "sslcert", "CN=ssl.example.com");

        // openssl should be able to parse our PKCS12 keystore
        var process = new ProcessBuilder(
                "openssl", "pkcs12", "-in", path, "-passin", "pass:" + PASSWORD_STR,
                "-nokeys", "-info"
        ).redirectErrorStream(true).start();

        var output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "openssl can't read Java's PKCS12: " + output);
    }

    // ----- platform layout tests (no native tools needed) ---------------------------------------

    /**
     * Verify that the delete-then-recreate pattern works correctly — the platform relies
     * on this for certificate renewal.
     */
    @Test
    public void testDeleteAndRecreatePattern() throws Exception {
        var path = new File(tempDir, "renewal.p12").getAbsolutePath();

        // create initial cert
        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "host", "CN=host.example.com");

        var keyStore1 = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var cert1 = (X509Certificate) keyStore1.getCertificate("host");
        assertNotNull(cert1);

        // delete and recreate (simulates certificate renewal)
        KeyStoreOperations.deleteKeyStoreEntry(path, PASSWORD, "host");
        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "host", "CN=host.example.com");

        var keyStore2 = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var cert2 = (X509Certificate) keyStore2.getCertificate("host");
        assertNotNull(cert2);

        // new cert should be different (new keypair)
        assertTrue(!cert1.getSerialNumber().equals(cert2.getSerialNumber()),
                "renewed cert should have different serial");

        // but same subject
        assertEquals(cert1.getSubjectX500Principal(), cert2.getSubjectX500Principal());
    }

    /**
     * Verify that keystores with multiple entry types (cert + symmetric keys + passwords)
     * work correctly — this mirrors the platform's actual keystore layout (TLS cert +
     * CookieEncryptionKey + PasswordEncryptionKey).
     */
    @Test
    public void testPlatformKeystoreLayout() throws Exception {
        var path = new File(tempDir, "platform.p12").getAbsolutePath();

        // create the same layout as the platform: TLS cert + two encryption keys
        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "PlatformTlsKey", "CN=platform.example.com");
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "CookieEncryptionKey");
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "PasswordEncryptionKey");

        // reload and verify everything is accessible
        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertEquals(3, keyStore.size());

        // TLS cert: RSA keypair + X509 certificate
        assertTrue(keyStore.isKeyEntry("PlatformTlsKey"));
        var cert = (X509Certificate) keyStore.getCertificate("PlatformTlsKey");
        assertNotNull(cert);
        cert.checkValidity();
        var tlsKey = keyStore.getKey("PlatformTlsKey", PASSWORD);
        assertNotNull(tlsKey);
        assertEquals("RSA", tlsKey.getAlgorithm());

        // Cookie encryption: AES-256
        var cookieKey = (SecretKey) keyStore.getKey("CookieEncryptionKey", PASSWORD);
        assertNotNull(cookieKey);
        assertEquals("AES", cookieKey.getAlgorithm());
        assertEquals(32, cookieKey.getEncoded().length);

        // Password encryption: AES-256
        var pwdKey = (SecretKey) keyStore.getKey("PasswordEncryptionKey", PASSWORD);
        assertNotNull(pwdKey);
        assertEquals("AES", pwdKey.getAlgorithm());
        assertEquals(32, pwdKey.getEncoded().length);
    }

    /**
     * Verify that a domain keypair stored during certificate creation can be reconstructed
     * for revocation — the exact pattern used by revokeWithAcme().
     */
    @Test
    public void testDomainKeyPairRoundTrip() throws Exception {
        var path = new File(tempDir, "keypair.p12").getAbsolutePath();

        // create a self-signed cert (stores private key in keystore, same as ACME flow)
        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "domain", "CN=domain.example.com");

        // reconstruct the keypair from keystore (same as revokeWithAcme does)
        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var cert = (X509Certificate) keyStore.getCertificate("domain");
        var privateKey = keyStore.getKey("domain", PASSWORD);
        assertNotNull(privateKey, "private key should be in keystore");

        var domainKeyPair = new KeyPair(
                cert.getPublicKey(),
                (java.security.PrivateKey) privateKey);

        // verify the keypair is consistent: sign something and verify it
        var signer = java.security.Signature.getInstance("SHA256withRSA");
        var testData = "test data for signing".getBytes();

        signer.initSign(domainKeyPair.getPrivate());
        signer.update(testData);
        var signature = signer.sign();

        signer.initVerify(domainKeyPair.getPublic());
        signer.update(testData);
        assertTrue(signer.verify(signature),
                "reconstructed keypair should produce valid signatures");
    }

    // ----- helper methods -----------------------------------------------------------------------

    static boolean isKeytoolAvailable() {
        try {
            var process = new ProcessBuilder("keytool", "-help")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isOpensslAvailable() {
        try {
            var process = new ProcessBuilder("openssl", "version")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
