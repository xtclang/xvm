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
    KeyStore keystoreFor(File keystore, Password pwd) = keystoreForImpl(keystore.contents, pwd);

    @Override
    KeyStore encryptKeyStore(File keystore, Password oldPwd, Password newPwd) {
        encryptKeyStoreImpl(getPath(keystore), oldPwd, newPwd);
        return keystoreFor(keystore, newPwd);
    }

    @Override
    void createCertificate(File keystore, Password pwd, String name, String dName) =
        createCertificateImpl(getPath(keystore), pwd, name, dName);

    @Override
    void revokeCertificate(File keystore, Password pwd, String name) =
        revokeCertificateImpl(getPath(keystore), pwd, name);

    @Override
    void createSymmetricKey(File keystore, Password pwd, String name) =
        createSymmetricKeyImpl(getPath(keystore), pwd, name);

    @Override
    void createPassword(File keystore, Password pwd, String name, String pwdValue) =
        createPasswordImpl(getPath(keystore), pwd, name, pwdValue);

    @Override
    Byte[] extractKey(File|KeyStore keystore, Password pwd, String name) =
        extractKeyImpl(keystore.is(File) ? getPath(keystore) : keystore, pwd, name);

    private String getPath(File keystore) {
        import ecstasy.fs.DirectoryFileStore.FileWrapper;
        import fs.OSFile;
        while (keystore := &keystore.revealAs((protected FileWrapper))) {
            keystore = keystore.origFile;
        }
        assert OSFile file := &keystore.revealAs(OSFile) as $"ReadOnly {keystore=}";
        return file.pathString;
    }

    private KeyStore keystoreForImpl(Byte[] contents, Password pwd)
        {TODO("Native");}

    private void encryptKeyStoreImpl(String path, Password oldPwd, Password newPwd)
        {TODO("Native");}

    private void createCertificateImpl(String path, Password pwd, String name, String dName)
        {TODO("Native");}

    private void revokeCertificateImpl(String path, Password pwd, String name)
        {TODO("Native");}

    private void createSymmetricKeyImpl(String path, Password pwd, String name)
        {TODO("Native");}

    private void createPasswordImpl(String path, Password pwd, String name, String pwdValue)
        {TODO("Native");}

    private Byte[] extractKeyImpl(String|KeyStore pathOrStore, Password pwd, String name)
        {TODO("Native");}
}