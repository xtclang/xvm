package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Path;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.time.Duration;

import java.util.List;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bouncycastle.operator.OperatorCreationException;

import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore.KeyStoreHandle;


/**
 * Native implementation of the xRTCertificateManager.x service.
 * <p>
 * It uses pure Java APIs (JDK crypto, BouncyCastle, acme4j) that produce byte-for-byte compatible
 * PKCS12 keystore entries. Keystores created by this implementation can be read by keytool and
 * openssl, and vice versa — verified by {@code KeyStoreCompatibilityTest}.
 */
public class xRTCertificateManager
        extends xService {

    public static xRTCertificateManager INSTANCE;

    public xRTCertificateManager(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("keystoreForImpl"       , null, null);
        markNativeMethod("encryptKeyStoreImpl"   , null, null);
        markNativeMethod("createCertificateImpl" , null, null);
        markNativeMethod("revokeCertificateImpl" , null, null);
        markNativeMethod("createSymmetricKeyImpl", null, null);
        markNativeMethod("createPasswordImpl"    , null, null);
        markNativeMethod("extractKeyImpl"        , null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        TypeConstant type = m_typeCanonical;
        if (type == null) {
            ConstantPool pool = pool();
            m_typeCanonical = type = pool.ensureTerminalTypeConstant(
                    pool.ensureClassConstant(pool.ensureModuleConstant("crypto.xtclang.org"),
                    "CertificateManager"));
        }
        return type;
    }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureManager(Frame frame, ObjectHandle hOpts) {
        StringHandle     hProvider = hOpts instanceof StringHandle hS ? hS : xString.makeHandle("self");
        ClassComposition clz       = getCanonicalClass();
        ServiceHandle    hMgr      = createServiceHandle(
                f_container.createServiceContext("CertificateManager"), clz, getCanonicalType());
        hMgr.setField(0, hProvider);
        return hMgr;
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        return switch (method.getName()) {
            case "keystoreForImpl"        ->
                invokeKeystoreFor(frame, ahArg, iReturn);
            case "encryptKeyStoreImpl"    ->
                invokeAsIOTask(frame, ()  -> invokeEncryptKeystore(frame, ahArg));
            case "createCertificateImpl"  ->
                invokeAsIOTask(frame, ()  ->
                    invokeCreateCertificate(frame, (ServiceHandle) hTarget, ahArg));
            case "revokeCertificateImpl"  ->
                invokeAsIOTask(frame, ()  ->
                    invokeRevokeCertificate(frame, (ServiceHandle) hTarget, ahArg));
            case "createSymmetricKeyImpl" ->
                invokeAsIOTask(frame, ()  -> invokeCreateSymmetricKey(frame, ahArg));
            case "createPasswordImpl"     ->
                invokeAsIOTask(frame, ()  -> invokeCreatePassword(frame, ahArg));
            case "extractKeyImpl"         ->
                invokeExtractKey(frame, ahArg, iReturn);
            default ->
                super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        };
    }

    private int invokeAsIOTask(Frame frame, Callable<ExceptionHandle> task) {
        CompletableFuture<ExceptionHandle> cfResult = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                ExceptionHandle hFailure = cfResult.get();
                return hFailure == null ? Op.R_NEXT : frameCaller.raiseException(hFailure);
            } catch (InterruptedException | ExecutionException e) {
                // TODO: we temporarily print the stack trace for unhandled exceptions here; remove
                e.printStackTrace();
                return frameCaller.raiseException(
                    xException.makeObscure(frame, "Unexpected execution failure " + e.getMessage()));
            }
        };
        return frame.waitForIO(cfResult, continuation);
    }


    // ----- certificate creation ------------------------------------------------------------------

    /**
     * Native implementation of
     *     "createCertificateImpl(String path, Password pwd, String name, String dName)"
     * <p>
     * For provider "self", replaces:
     * <pre>{@code
     *   keytool -delete -alias <name> -keystore <path> -storepass <pwd>
     *   keytool -genkeypair -keyalg RSA -keysize 2048 -validity 90
     *           -alias <name> -dname <dName> -storetype PKCS12
     *           -keystore <path> -storepass <pwd>
     * }</pre>
     * The Java implementation uses {@link java.security.KeyPairGenerator} (RSA, 2048-bit)
     * and BouncyCastle's {@code X509v3CertificateBuilder} with SHA256WithRSA — the same
     * JDK crypto primitives that keytool uses internally. The resulting PKCS12 keystore
     * entry is interchangeable with keytool output.
     * <p>
     * For providers "certbot"/"certbot-staging", replaces the multistep native flow:
     * <pre>{@code
     *    openssl genpkey
     *    openssl req
     *    certbot certonly --webroot
     *    openssl pkcs12 -export
     *    keytool -importkeystore}
     * </pre>
     * The Java
     * implementation uses acme4j to speak the ACME protocol directly, eliminating all
     * intermediate files and format conversions. Challenge files are written to the same
     * {@code .challenge/.well-known/acme-challenge/} directory that certbot's webroot
     * mode used, so the platform's {@code AcmeChallenge} web service works unchanged.
     */
    private ExceptionHandle invokeCreateCertificate(Frame frame, ServiceHandle hMgr,
                                                    ObjectHandle[] ahArg) {
        StringHandle hStorePath  = (StringHandle) ahArg[0];
        StringHandle hPwd        = xRTKeyStore.getPassword(frame, ahArg[1]);
        String       sName       = ((StringHandle) ahArg[2]).getStringValue();
        String       sDName      = ((StringHandle) ahArg[3]).getStringValue();
        String       sProvider   = ((StringHandle) hMgr.getField(0)).getStringValue();
        String       sStorePath  = hStorePath.getStringValue();
        char[]       achPwd      = hPwd.getValue();

        try {
            KeyStoreOperations.deleteKeyStoreEntry(sStorePath, achPwd, sName);

            return switch (sProvider) {
                case "self" -> {
                    KeyStoreOperations.createSelfSignedCertificate(sStorePath, achPwd, sName, sDName);
                    yield null;
                }
                case "certbot-staging" -> {
                    createCertificateWithAcme(sStorePath, achPwd, sName, sDName, true, hStorePath);
                    yield null;
                }
                case "certbot" -> {
                    createCertificateWithAcme(sStorePath, achPwd, sName, sDName, false, hStorePath);
                    yield null;
                }
                default -> xException.makeHandle(frame,
                        "Unsupported certificate provider: " + sProvider);
            };
        } catch (AcmeException | OperatorCreationException | GeneralSecurityException |
                 IOException | InterruptedException e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Create a certificate using the ACME protocol (Let's Encrypt) via acme4j.
     * <p>
     * Replaces the old five-step native flow (openssl genpkey → openssl req → certbot
     * certonly → openssl pkcs12 -export → keytool -importkeystore) with a single
     * in-process ACME interaction. The domain keypair and certificate chain are stored
     * directly into the keystore without intermediate PEM/PKCS12 temp files, which is
     * both simpler and more secure (no unencrypted private key written to disk).
     * <p>
     * Polling uses acme4j's {@code waitForCompletion(Duration)} which respects the
     * server's Retry-After header, rather than the old approach of blocking on
     * {@code process.waitFor(300, SECONDS)} while certbot polled internally.
     */
    private void createCertificateWithAcme(String sStorePath, char[] achPwd, String sName, String sDName,
                                           boolean fStaging, StringHandle hStorePath)
                throws AcmeException, GeneralSecurityException, IOException, InterruptedException {
        int ofDomain = sDName.indexOf("CN=");
        assert ofDomain >= 0;

        String sDomain      = sDName.substring(ofDomain + 3);
        File   dirChallenge = getChallengePath(hStorePath);
        if (!dirChallenge.exists() && !dirChallenge.mkdir() || !dirChallenge.isDirectory()) {
            throw new IOException("Cannot create directory: " + dirChallenge.getAbsolutePath());
        }

        KeyPair domainKeyPair  = KeyPairUtils.createKeyPair(2048);
        KeyPair accountKeyPair = KeyPairUtils.createKeyPair(2048);
        Session session        = new Session(acmeServerUri(fStaging));
        Account acmeAccount    = new AccountBuilder()
                                    .agreeToTermsOfService()
                                    .useKeyPair(accountKeyPair)
                                    .create(session);

        Order acmeOrder = acmeAccount.newOrder().domain(sDomain).create();

        processHttpChallenges(acmeOrder.getAuthorizations(), dirChallenge, sDomain);

        CSRBuilder csrBuilder = buildCSR(sDName, sDomain);
        csrBuilder.sign(domainKeyPair);
        acmeOrder.execute(csrBuilder.getEncoded());

        Status orderStatus = acmeOrder.waitForCompletion(ACME_TIMEOUT);
        if (orderStatus != Status.VALID) {
            throw new AcmeException("Certificate order failed for " + sDomain
                    + " (status: " + orderStatus + ")");
        }

        List<X509Certificate> certChain = acmeOrder.getCertificate().getCertificateChain();

        KeyStore keyStore = KeyStoreOperations.loadOrCreateKeyStore(sStorePath, achPwd);
        keyStore.setKeyEntry(sName, domainKeyPair.getPrivate(), achPwd,
                certChain.toArray(new Certificate[0]));
        KeyStoreOperations.saveKeyStore(keyStore, sStorePath, achPwd);
    }

    /**
     * Process HTTP-01 challenges for each pending authorization.
     * <p>
     * Writes challenge token files to {@code .challenge/.well-known/acme-challenge/} —
     * the same directory layout that certbot's {@code --webroot --webroot-path} mode used.
     * The platform's {@code AcmeChallenge} web service serves these files at the path
     * that Let's Encrypt expects ({@code /.well-known/acme-challenge/{token}}).
     */
    private void processHttpChallenges(List<Authorization> authorizations,
                                       File dirChallenge, String sDomain)
            throws AcmeException, IOException, InterruptedException {
        for (Authorization auth : authorizations) {
            if (auth.getStatus() != Status.PENDING) {
                continue;
            }

            Http01Challenge challenge = auth.findChallenge(Http01Challenge.class)
                    .orElseThrow(() -> new AcmeException(
                            "No HTTP-01 challenge available for " + sDomain));

            File challengeDir = new File(dirChallenge,
                    ".well-known" + File.separator + "acme-challenge");
            if (!challengeDir.exists() && !challengeDir.mkdirs()) {
                throw new IOException("Cannot create challenge directory: " + challengeDir);
            }

            File challengeFile = new File(challengeDir, challenge.getToken());
            try (var writer = new FileWriter(challengeFile)) {
                writer.write(challenge.getAuthorization());
            }

            try {
                challenge.trigger();
                Status authStatus = auth.waitForCompletion(ACME_TIMEOUT);
                if (authStatus != Status.VALID) {
                    throw new AcmeException("Challenge failed for " + sDomain
                            + " (status: " + authStatus + ")");
                }
            } finally {
                challengeFile.delete();
            }
        }
    }

    /**
     * Build a CSR from the distinguished name string.
     */
    private CSRBuilder buildCSR(String sDName, String sDomain) {
        var csrBuilder = new CSRBuilder();
        csrBuilder.addDomain(sDomain);

        for (var sPart : sDName.split(",")) {
            var kv    = sPart.trim().split("=", 2);
            var key   = kv[0];
            var value = kv.length > 1 ? kv[1] : "";

            switch (key) {
                case "C"       -> csrBuilder.setCountry(value);
                case "ST", "S" -> csrBuilder.setState(value);
                case "L"       -> csrBuilder.setLocality(value);
                case "O"       -> csrBuilder.setOrganization(value);
                case "OU"      -> csrBuilder.setOrganizationalUnit(value);
                default        -> {} // CN handled separately, ignore unknown
            }
        }
        return csrBuilder;
    }


    // ----- certificate revocation ----------------------------------------------------------------

    /**
     * Native implementation of
     *     "revokeCertificateImpl(String path, Password pwd, String name)"
     * <p>
     * Replaces the old native flow:
     * <pre>{@code
     *   certbot revoke --config-dir <certs>/config --cert-name <name> --reason unspecified
     *   keytool -delete -alias <name> -keystore <path> -storepass <pwd>
     * }</pre>
     * The old certbot revocation used the stored account key from its config directory.
     * The Java implementation uses domain-key revocation (RFC 8555 §7.6) — extracting
     * the domain keypair from the keystore, which is more robust because it doesn't
     * depend on certbot's external config state.
     */
    private ExceptionHandle invokeRevokeCertificate(Frame frame, ServiceHandle hMgr,
                                                    ObjectHandle[] ahArg) {
        String sPath     = ((StringHandle) ahArg[0]).getStringValue();
        char[] achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        String sName     = ((StringHandle) ahArg[2]).getStringValue();
        String sProvider = ((StringHandle) hMgr.getField(0)).getStringValue();

        try {
            switch (sProvider) {
                case "self" -> {}
                case "certbot-staging" -> revokeWithAcme(sPath, achPwd, sName, true);
                case "certbot"         -> revokeWithAcme(sPath, achPwd, sName, false);
                default -> {
                    return xException.makeHandle(frame,
                            "Unsupported certificate provider: " + sProvider);
                }
            }

            KeyStoreOperations.deleteKeyStoreEntry(sPath, achPwd, sName);
            return null;
        } catch (AcmeException | GeneralSecurityException | IOException e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Revoke a certificate using the ACME protocol via acme4j.
     * <p>
     * Uses domain-key-authenticated revocation: the private key that signed the CSR is extracted
     * from the keystore and used to prove ownership to the ACME server. This is one of two
     * revocation mechanisms defined in RFC 8555 §7.6 (the other being account-key revocation).
     * We use domain-key revocation because the account keypair is ephemeral (generated fresh per
     * certificate request) and not persisted, whereas the domain key is always in the keystore
     * alongside the certificate.
     */
    private void revokeWithAcme(String sStorePath, char[] achPwd, String sName, boolean fStaging)
            throws AcmeException, GeneralSecurityException, IOException {
        KeyStore    keyStore = KeyStoreOperations.loadOrCreateKeyStore(sStorePath, achPwd);
        Certificate cert     = keyStore.getCertificate(sName);

        if (cert instanceof X509Certificate x509Cert) {
            Key privateKey = keyStore.getKey(sName, achPwd);
            if (privateKey == null) {
                throw new AcmeException("Cannot revoke certificate '" + sName
                                + "': private key not found in keystore");
            }

            KeyPair domainKeyPair = new KeyPair(x509Cert.getPublicKey(), (PrivateKey) privateKey);
            Session session       = new Session(acmeServerUri(fStaging));

            org.shredzone.acme4j.Certificate.revoke(session, domainKeyPair, x509Cert, null);
        }
    }


    // ----- symmetric key & password management ---------------------------------------------------

    /**
     * Native implementation of
     *     "invokeCreateSymmetricKeyImpl(String path, Password pwd, String name)"
     * <p>
     * Replaces:
     * <pre>{@code
     *   keytool -delete -alias <name> -keystore <path> -storepass <pwd>
     *   keytool -genseckey -keyalg AES -keysize 256 -alias <name>
     *           -storetype PKCS12 -keystore <path> -storepass <pwd>
     * }</pre>
     * Uses {@link javax.crypto.KeyGenerator#getInstance(String)} with AES/256 — the same
     * JDK API that keytool's {@code -genseckey} uses internally. The resulting
     * {@code SecretKeyEntry} in the PKCS12 keystore is identical in format.
     */
    private ExceptionHandle invokeCreateSymmetricKey(Frame frame, ObjectHandle[] ahArg) {
        String sPath  = ((StringHandle) ahArg[0]).getStringValue();
        char[] achPwd = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        String sName  = ((StringHandle) ahArg[2]).getStringValue();

        try {
            KeyStoreOperations.createSymmetricKey(sPath, achPwd, sName);
            return null;
        } catch (GeneralSecurityException | IOException e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Native implementation of
     *     "invokeCreatePasswordImpl(String path, Password pwd, String name, String pwdValue)"
     * <p>
     * Replaces:
     * <pre>{@code
     *   keytool -delete -alias <name> -keystore <path> -storepass <pwd>
     *   echo <pwdValue> | keytool -importpass -alias <name> -storetype PKCS12
     *           -keystore <path> -storepass <pwd>
     * }</pre>
     * Uses {@link javax.crypto.SecretKeyFactory#getInstance(String)} with "PBE" to create
     * a PBE secret key from the password value, then stores it as a {@code SecretKeyEntry}
     * — the same internal representation that keytool's {@code -importpass} produces.
     */
    private ExceptionHandle invokeCreatePassword(Frame frame, ObjectHandle[] ahArg) {
        String sPath     = ((StringHandle) ahArg[0]).getStringValue();
        char[] achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        String sName     = ((StringHandle) ahArg[2]).getStringValue();
        String sPwdValue = ((StringHandle) ahArg[3]).getStringValue();

        try {
            KeyStoreOperations.createPassword(sPath, achPwd, sName, sPwdValue);
            return null;
        } catch (GeneralSecurityException | IOException e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }


    // ----- key extraction & password change ------------------------------------------------------

    /**
     * Native implementation of
     *     "Byte[] extractKeyImpl(String|KeyStore pathOrStore, Password pwd, String name)"
     */
    private int invokeExtractKey(Frame frame, ObjectHandle[] ahArg, int iReturn) {
        ObjectHandle hPathOrStore = ahArg[0];
        StringHandle hPwd         = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName        = (StringHandle) ahArg[2];

        CompletableFuture<Key> cfResult = frame.f_context.f_container.scheduleIO(
                () -> loadKey(hPathOrStore, hPwd, hName));

        Frame.Continuation continuation = frameCaller -> {
            try {
                Key key = cfResult.get();
                return key == null
                        ? frameCaller.raiseException(xException.ioException(frameCaller,
                                "Invalid or inaccessible key \"" + hName.getStringValue() + '"'))
                        : frameCaller.assignValue(iReturn,
                                xArray.makeByteArrayHandle(key.getEncoded(), Mutability.Constant));
            } catch (InterruptedException | ExecutionException e) {
                // TODO: we temporarily print the stack trace for unhandled exceptions here; remove
                e.printStackTrace();
                return frameCaller.raiseException(
                    xException.makeObscure(frame, "Unexpected execution failure " + e));
            }
        };
        return frame.waitForIO(cfResult, continuation);
    }

    private Key loadKey(ObjectHandle hPathOrStore, StringHandle hPwd, StringHandle hName)
            throws GeneralSecurityException, IOException {
        char[] achPwd = hPwd.getValue();
        String sKey   = hName.getStringValue();

        KeyStore keyStore;
        if (hPathOrStore instanceof StringHandle hPath) {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new FileInputStream(hPath.getStringValue()), achPwd);
        } else {
            keyStore = ((KeyStoreHandle) hPathOrStore).f_keyStore;
        }
        return keyStore.getKey(sKey, achPwd);
    }

    /**
     * Native implementation of
     *     "keystoreForImpl(Byte[] contents, Password pwd)"
     */
    private int invokeKeystoreFor(Frame frame, ObjectHandle[] ahArg, int iReturn) {
        ArrayHandle  hContent  = (ArrayHandle) ahArg[0];
        StringHandle hPwd      = xRTKeyStore.getPassword(frame, ahArg[1]);
        ObjectHandle hKeyStore = xRTKeyStore.INSTANCE.ensureKeyStore(frame, hContent, hPwd);

        return frame.assignDeferredValue(iReturn, hKeyStore);
    }

    /**
     * Native implementation of
     *     "encryptKeyStoreImpl(String path, Password pwd, String newPwd)"
     * <p>
     * Loads the keystore with the old password and saves with the new one — the same
     * operation that keytool's {@code -storepasswd} performs internally via the JDK
     * {@link java.security.KeyStore} API.
     */
    private ExceptionHandle invokeEncryptKeystore(Frame frame, ObjectHandle[] ahArg) {
        String sPath     = ((StringHandle) ahArg[0]).getStringValue();
        char[] achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        char[] achPwdNew = ((StringHandle) ahArg[2]).getValue();

        try {
            KeyStoreOperations.changeStorePassword(sPath, achPwd, achPwdNew);
            return null;
        } catch (Exception e) { // TODO: tighten to GeneralSecurityException | IOException
            return xException.obscureIoException(frame, e.getMessage());
        }
    }


    // ----- helper methods ------------------------------------------------------------------------

    private static String acmeServerUri(boolean fStaging) {
        return fStaging ? "acme://letsencrypt.org/staging" : "acme://letsencrypt.org";
    }

    private File getChallengePath(StringHandle hPath) {
        return new File(Path.of(hPath.getStringValue()).toFile().getParentFile(), ".challenge");
    }


    // ----- data fields and constants -------------------------------------------------------------

    private static final Duration ACME_TIMEOUT = Duration.ofMinutes(2);

    private TypeConstant m_typeCanonical;
}
