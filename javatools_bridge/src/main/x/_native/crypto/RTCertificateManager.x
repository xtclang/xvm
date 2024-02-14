import libcrypto.CertificateManager;
import libcrypto.Password;

service RTCertificateManager
        implements CertificateManager{

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

    private String getPath(File keystore) {
        import fs.OSFile;
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
}