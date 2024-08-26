import libcrypto.CertificateManager;
import libcrypto.KeyStore;
import libcrypto.Password;

service RTCertificateManager
        implements CertificateManager{

    /**
     * The name for the certificate provider, which is only used by the native implementation.
     * Potential values are:
     *   - "self" for the self-signed certificates
     *   - "certbot-staging" for test certificates signed by "Let's Encrypt" using certbot utility
     *   - "certbot" for production certificates signed by "Let's Encrypt" using certbot utility
     */
    private String provider = "self";

    @Override
    void createCertificate(File keystore, Password pwd, String name, String dName) {
        createCertificateImpl(getPath(keystore), pwd, name, dName);
    }

    @Override
    void revokeCertificate(File keystore, Password pwd, String name) {
        revokeCertificateImpl(getPath(keystore), pwd, name);
    }

    @Override
    void createSymmetricKey(File keystore, Password pwd, String name) {
        createSymmetricKeyImpl(getPath(keystore), pwd, name);
    }

    @Override
    void createPassword(File keystore, Password pwd, String name, String pwdValue) {
        createPasswordImpl(getPath(keystore), pwd, name, pwdValue);
    }

    @Override
    void changeStorePassword(File keystore, Password pwd, Password newPassword) {
        changeStorePasswordImpl(getPath(keystore), pwd, newPassword);
    }

    @Override
    Byte[] extractKey(File|KeyStore keystore, Password pwd, String name) {
        return extractKeyImpl(keystore.is(File) ? getPath(keystore) : keystore, pwd, name);
    }

    private String getPath(File keystore) {
        import ecstasy.fs.DirectoryFileStore.FileWrapper;
        import fs.OSFile;
        while (keystore := &keystore.revealAs((protected FileWrapper))) {
            keystore = keystore.origFile;
        }
        assert OSFile file := &keystore.revealAs(OSFile) as $"ReadOnly {keystore=}";
        return file.pathString;
    }

    private void createCertificateImpl(String path, Password pwd, String name, String dName)
        {TODO("Native");}

    private void revokeCertificateImpl(String path, Password pwd, String name)
        {TODO("Native");}

    private void createSymmetricKeyImpl(String path, Password pwd, String name)
        {TODO("Native");}

    private void createPasswordImpl(String path, Password pwd, String name, String pwdValue)
        {TODO("Native");}

    private void changeStorePasswordImpl(String path, Password pwd, Password newPwd)
        {TODO("Native");}

    private Byte[] extractKeyImpl(String|KeyStore pathOrStore, Password pwd, String name)
        {TODO("Native");}
}