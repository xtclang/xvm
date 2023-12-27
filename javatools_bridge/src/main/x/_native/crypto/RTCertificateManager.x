import libcrypto.CertificateManager;

service RTCertificateManager
        implements CertificateManager{

    @Override
    void createCertificate(File keystore, String password, String name, String dName) {
        createCertificateImpl(getPath(keystore), password, name, dName);
    }

    @Override
    void revokeCertificate(File keystore, String password, String name) {
        revokeCertificateImpl(getPath(keystore), password, name);
    }

    @Override
    void createSymmetricKey(File keystore, String password, String name) {
        createSymmetricKeyImpl(getPath(keystore), password, name);
    }

    @Override
    void createPassword(File keystore, String storePassword, String name, String passwordValue) {
        createPasswordImpl(getPath(keystore), storePassword, name, passwordValue);
    }

    @Override
    void changeStorePassword(File keystore, String password, String newPassword) {
        changeStorePasswordImpl(getPath(keystore), password, newPassword);
    }

    private String getPath(File keystore) {
        import fs.OSFile;
        assert OSFile file := &keystore.revealAs(OSFile) as $"ReadOnly {keystore=}";
        return file.pathString;
    }

    private void createCertificateImpl(String path, String password, String name, String dName)
        {TODO("Native");}

    private void revokeCertificateImpl(String path, String password, String name)
        {TODO("Native");}

    private void createSymmetricKeyImpl(String path, String password, String name)
        {TODO("Native");}

    private void createPasswordImpl(String path, String storePassword, String name, String passwordValue)
        {TODO("Native");}

    private void changeStorePasswordImpl(String path, String password, String newPassword)
        {TODO("Native");}
}