package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests for {@link KeyStoreOperations} — pure Java keystore and certificate operations
 * with no XVM runtime dependencies.
 */
public class KeyStoreOperationsTest {

    private static final char[] PASSWORD = "testpass".toCharArray();

    @TempDir
    File tempDir;

    @Test
    public void testCreateAndLoadKeyStore() throws Exception {
        var path = new File(tempDir, "test.p12").getAbsolutePath();

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertNotNull(keyStore);
        assertEquals(0, keyStore.size());

        KeyStoreOperations.saveKeyStore(keyStore, path, PASSWORD);
        assertTrue(new File(path).exists());

        var reloaded = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertEquals(0, reloaded.size());
    }

    @Test
    public void testCreateSelfSignedCertificate() throws Exception {
        var path = new File(tempDir, "cert.p12").getAbsolutePath();

        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "myalias", "CN=test.example.com,O=Test Corp,C=US");

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("myalias"));
        assertTrue(keyStore.isKeyEntry("myalias"));

        var cert = (X509Certificate) keyStore.getCertificate("myalias");
        assertNotNull(cert);
        assertEquals("SHA256WITHRSA", cert.getSigAlgName().toUpperCase());

        var subject = cert.getSubjectX500Principal().getName();
        assertTrue(subject.contains("CN=test.example.com"));

        var key = keyStore.getKey("myalias", PASSWORD);
        assertNotNull(key);
        assertEquals("RSA", key.getAlgorithm());
    }

    @Test
    public void testCreateSymmetricKey() throws Exception {
        var path = new File(tempDir, "sym.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "aeskey");

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("aeskey"));

        var key = keyStore.getKey("aeskey", PASSWORD);
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length); // 256 bits
    }

    @Test
    public void testCreateSymmetricKeyReplacesExisting() throws Exception {
        var path = new File(tempDir, "replace.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "key1");
        var keyStore1 = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var key1 = keyStore1.getKey("key1", PASSWORD).getEncoded();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "key1");
        var keyStore2 = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var key2 = keyStore2.getKey("key1", PASSWORD).getEncoded();

        // keys should be different (regenerated)
        assertFalse(Arrays.equals(key1, key2));
    }

    @Test
    public void testCreatePassword() throws Exception {
        var path = new File(tempDir, "pwd.p12").getAbsolutePath();

        KeyStoreOperations.createPassword(path, PASSWORD, "dbpass", "s3cret!");

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("dbpass"));

        var key = keyStore.getKey("dbpass", PASSWORD);
        assertNotNull(key);
        assertTrue(key.getAlgorithm().startsWith("PBE"));
    }

    @Test
    public void testChangeStorePassword() throws Exception {
        var path = new File(tempDir, "changepwd.p12").getAbsolutePath();
        var newPwd = "newpass".toCharArray();

        // create a keystore with an entry
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "mykey");

        // change the password
        KeyStoreOperations.changeStorePassword(path, PASSWORD, newPwd);

        // old password should fail
        var keyStore = KeyStore.getInstance("PKCS12");
        assertThrows(Exception.class, () -> {
            try (var in = new FileInputStream(path)) {
                keyStore.load(in, PASSWORD);
            }
        });

        // new password should work
        var reloaded = KeyStoreOperations.loadOrCreateKeyStore(path, newPwd);
        assertTrue(reloaded.containsAlias("mykey"));
    }

    @Test
    public void testExtractKey() throws Exception {
        var path = new File(tempDir, "extract.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "extractme");

        var key = KeyStoreOperations.extractKey(path, PASSWORD, "extractme");
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
    }

    @Test
    public void testExtractKeyReturnsNullForMissingAlias() throws Exception {
        var path = new File(tempDir, "nokey.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "exists");

        assertNull(KeyStoreOperations.extractKey(path, PASSWORD, "doesnotexist"));
    }

    @Test
    public void testExtractKeyReturnsNullForMissingFile() {
        assertNull(KeyStoreOperations.extractKey("/nonexistent/path.p12", PASSWORD, "key"));
    }

    @Test
    public void testDeleteKeyStoreEntry() throws Exception {
        var path = new File(tempDir, "del.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "todelete");
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "tokeep");

        var before = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(before.containsAlias("todelete"));

        KeyStoreOperations.deleteKeyStoreEntry(path, PASSWORD, "todelete");

        var after = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertFalse(after.containsAlias("todelete"));
        assertTrue(after.containsAlias("tokeep"));
    }

    @Test
    public void testDeleteKeyStoreEntryNonexistentAlias() throws Exception {
        var path = new File(tempDir, "delnone.p12").getAbsolutePath();

        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "exists");

        // should not throw
        KeyStoreOperations.deleteKeyStoreEntry(path, PASSWORD, "nosuchalias");

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("exists"));
    }

    @Test
    public void testDeleteKeyStoreEntryNonexistentFile() {
        // should not throw
        KeyStoreOperations.deleteKeyStoreEntry("/nonexistent/path.p12", PASSWORD, "alias");
    }

    @Test
    public void testSelfSignedCertificateValidity() throws Exception {
        var path = new File(tempDir, "validity.p12").getAbsolutePath();

        KeyStoreOperations.createSelfSignedCertificate(
                path, PASSWORD, "cert", "CN=valid.example.com");

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        var cert = (X509Certificate) keyStore.getCertificate("cert");

        // certificate should be valid right now
        cert.checkValidity();

        // verify it was self-signed (issuer == subject)
        assertEquals(cert.getSubjectX500Principal(), cert.getIssuerX500Principal());
    }
}
