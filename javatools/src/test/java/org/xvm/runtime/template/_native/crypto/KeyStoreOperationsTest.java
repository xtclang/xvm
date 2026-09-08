package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

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
        assertThrows(IOException.class, () ->
            KeyStoreOperations.extractKey("/nonexistent/path.p12", PASSWORD, "key"));
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

    /**
     * The failure the old swallow was hiding, and the reason it mattered.
     *
     * <p>{@code deleteKeyStoreEntry} used to discard {@code GeneralSecurityException} and
     * {@code IOException}, justified as "entry may not exist" - a case the {@code file.exists()} and
     * {@code containsAlias} guards already handle. What it actually silenced was a keystore that
     * could not be OPENED, and a wrong password is the ordinary way that happens.
     *
     * <p>That turned into data loss because every caller does delete-then-create through
     * {@code loadOrCreateKeyStore}: the delete reported success, and the store was then created
     * fresh over the top of the existing one. Master issue 50.
     */
    @Test
    public void deletingWithTheWrongPasswordFailsRatherThanReportingSuccess() throws Exception {
        var path = new File(tempDir, "wrongpass.p12").getAbsolutePath();
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "keep-me");

        char[] wrong = "notthepassword".toCharArray();
        assertThrows(IOException.class,
                () -> KeyStoreOperations.deleteKeyStoreEntry(path, wrong, "keep-me"),
                "a keystore that cannot be opened is not a delete that succeeded");

        // and the store is intact - nothing was silently replaced
        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("keep-me"), "the existing entry survived");
    }

    /**
     * Pins {@code loadOrCreateKeyStore}'s "create" half: it creates only when the file is ABSENT, so
     * a wrong password against an existing store fails rather than replacing it.
     *
     * <p>Recorded because I first assumed the opposite when filing master issue 50 - that a silently
     * failed delete would let the create clobber the store. It does not, and this is the test that
     * says so. It passes with or without the {@code deleteKeyStoreEntry} fix, so it is a contract
     * guard rather than a regression test for that issue.
     */
    @Test
    public void creatingWithTheWrongPasswordDoesNotReplaceTheStore() throws Exception {
        var path = new File(tempDir, "noclobber.p12").getAbsolutePath();
        KeyStoreOperations.createSymmetricKey(path, PASSWORD, "original");

        char[] wrong = "notthepassword".toCharArray();
        assertThrows(IOException.class,
                () -> KeyStoreOperations.createSymmetricKey(path, wrong, "intruder"));

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(path, PASSWORD);
        assertTrue(keyStore.containsAlias("original"),
                "the original entry must still be there; it used to be wiped by the recreate");
        assertFalse(keyStore.containsAlias("intruder"));
    }

    @Test
    public void testDeleteKeyStoreEntryNonexistentFile() throws Exception {
        // Should not throw - and this is delivered by the file.exists() guard, not by a catch.
        // deleteKeyStoreEntry used to swallow GeneralSecurityException and IOException outright,
        // justified as "entry may not exist"; this test and its sibling above pin that case, and
        // both still pass now that the method propagates. What the catch actually silenced was an
        // unreadable or unwritable keystore - a wrong password, a corrupt file - which no test
        // asserted and which callers do want to hear about.
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
