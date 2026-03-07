package org.xvm.runtime.template._native.crypto;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Path;

import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
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
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore.KeyStoreHandle;

import org.xvm.util.Handy;


/**
 * Native implementation of the xRTCertificateManager.x service.
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
        markNativeMethod("createCertificateImpl"  , null, VOID);
        markNativeMethod("revokeCertificateImpl"  , null, VOID);
        markNativeMethod("createSymmetricKeyImpl" , null, VOID);
        markNativeMethod("createPasswordImpl"     , null, VOID);
        markNativeMethod("changeStorePasswordImpl", null, VOID);
        markNativeMethod("extractKeyImpl"         , null, BYTES);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        var type = m_typeCanonical;
        if (type == null) {
            var pool = pool();
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
        var hProvider = hOpts instanceof StringHandle hS ? hS : xString.makeHandle("self");
        var clz = getCanonicalClass();
        var hMgr = createServiceHandle(
                f_container.createServiceContext("CertificateManager"), clz, getCanonicalType());
        hMgr.setField(0, hProvider);
        return hMgr;
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        return switch (method.getName()) {
            case "createCertificateImpl" ->
                invokeAsIOTask(frame, () ->
                    invokeCreateCertificate(frame, (ServiceHandle) hTarget, ahArg));
            case "revokeCertificateImpl" ->
                invokeAsIOTask(frame, () ->
                    invokeRevokeCertificate(frame, (ServiceHandle) hTarget, ahArg));
            case "createSymmetricKeyImpl" ->
                invokeAsIOTask(frame, () -> invokeCreateSymmetricKey(frame, ahArg));
            case "createPasswordImpl" ->
                invokeAsIOTask(frame, () -> invokeCreatePassword(frame, ahArg));
            case "changeStorePasswordImpl" ->
                invokeAsIOTask(frame, () -> invokeChangeStorePassword(frame, ahArg));
            case "extractKeyImpl" ->
                invokeExtractKey(frame, ahArg, iReturn);
            default ->
                super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        };
    }

    private int invokeAsIOTask(Frame frame, Callable<ExceptionHandle> task) {
        var cfResult = frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller -> {
            try {
                var hFailure = cfResult.get();
                return hFailure == null ? Op.R_NEXT : frameCaller.raiseException(hFailure);
            } catch (Throwable e) {
                // TODO: catching Throwable and discarding the cause is bad practice; the
                //  full exception chain (including stack trace) is lost, making debugging
                //  nearly impossible. raiseException should support a Throwable cause so
                //  it can be logged or chained into the XVM exception model.
                return frameCaller.raiseException("Unexpected execution failure " + e);
            }
        };
        return frame.waitForIO(cfResult, continuation);
    }


    // ----- certificate creation ------------------------------------------------------------------

    /**
     * Native implementation of
     *     "createCertificateImpl(String path, Password pwd, String name, String dName)"
     */
    private ExceptionHandle invokeCreateCertificate(Frame frame, ServiceHandle hMgr,
                                                    ObjectHandle[] ahArg) {
        var hStorePath  = (StringHandle) ahArg[0];
        var hPwd        = xRTKeyStore.getPassword(frame, ahArg[1]);
        var sName       = ((StringHandle) ahArg[2]).getStringValue();
        var sDName      = ((StringHandle) ahArg[3]).getStringValue();
        var sProvider   = ((StringHandle) hMgr.getField(0)).getStringValue();
        var sStorePath  = hStorePath.getStringValue();
        var achPwd      = hPwd.getValue();

        try {
            KeyStoreOperations.deleteKeyStoreEntry(sStorePath, achPwd, sName);

            return switch (sProvider) {
                case "self" -> {
                    KeyStoreOperations.createSelfSignedCertificate(
                            sStorePath, achPwd, sName, sDName);
                    yield null;
                }
                case "certbot-staging" -> {
                    createCertificateWithAcme(
                            sStorePath, achPwd, sName, sDName, true, hStorePath);
                    yield null;
                }
                case "certbot" -> {
                    createCertificateWithAcme(
                            sStorePath, achPwd, sName, sDName, false, hStorePath);
                    yield null;
                }
                default -> xException.makeHandle(frame,
                        "Unsupported certificate provider: " + sProvider);
            };
        } catch (Exception e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Create a certificate using the ACME protocol (Let's Encrypt) via acme4j.
     */
    private void createCertificateWithAcme(String sStorePath, char[] achPwd,
                                           String sName, String sDName,
                                           boolean fStaging, StringHandle hStorePath)
            throws Exception {
        int ofDomain = sDName.indexOf("CN=");
        assert ofDomain >= 0;
        var sDomain      = sDName.substring(ofDomain + 3);
        var dirChallenge = getChallengePath(hStorePath);
        if (!dirChallenge.exists() && !dirChallenge.mkdir() || !dirChallenge.isDirectory()) {
            throw new IOException("Cannot create directory: " + dirChallenge.getAbsolutePath());
        }

        var domainKeyPair  = KeyPairUtils.createKeyPair(2048);
        var accountKeyPair = KeyPairUtils.createKeyPair(2048);
        var session        = new Session(acmeServerUri(fStaging));

        var account = new AccountBuilder()
                .agreeToTermsOfService()
                .useKeyPair(accountKeyPair)
                .create(session);

        var order = account.newOrder().domain(sDomain).create();

        processHttpChallenges(order.getAuthorizations(), dirChallenge, sDomain);

        var csrBuilder = buildCSR(sDName, sDomain);
        csrBuilder.sign(domainKeyPair);
        order.execute(csrBuilder.getEncoded());

        pollUntilValid(order, "Certificate order failed for " + sDomain);

        var certChain = order.getCertificate().getCertificateChain();

        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(sStorePath, achPwd);
        keyStore.setKeyEntry(sName, domainKeyPair.getPrivate(), achPwd,
                certChain.toArray(new Certificate[0]));
        KeyStoreOperations.saveKeyStore(keyStore, sStorePath, achPwd);
    }

    /**
     * Process HTTP-01 challenges for each pending authorization.
     */
    private void processHttpChallenges(List<Authorization> authorizations,
                                       File dirChallenge, String sDomain)
            throws Exception {
        for (var auth : authorizations) {
            if (auth.getStatus() != Status.PENDING) {
                continue;
            }

            var challenge = auth.findChallenge(Http01Challenge.class)
                    .orElseThrow(() -> new AcmeException(
                            "No HTTP-01 challenge available for " + sDomain));

            var challengeDir = new File(dirChallenge,
                    ".well-known" + File.separator + "acme-challenge");
            if (!challengeDir.exists() && !challengeDir.mkdirs()) {
                throw new IOException("Cannot create challenge directory: " + challengeDir);
            }

            var challengeFile = new File(challengeDir, challenge.getToken());
            try (var writer = new FileWriter(challengeFile)) {
                writer.write(challenge.getAuthorization());
            }

            try {
                challenge.trigger();
                pollAuthUntilValid(auth, sDomain);
            } finally {
                challengeFile.delete();
            }
        }
    }

    /**
     * Poll an authorization until it is valid, invalid, or we time out.
     */
    private void pollAuthUntilValid(Authorization auth, String sDomain) throws Exception {
        for (int i = 0; i < MAX_POLL_ATTEMPTS && auth.getStatus() != Status.VALID; i++) {
            Thread.sleep(POLL_INTERVAL_MS);
            auth.update();
            if (auth.getStatus() == Status.INVALID) {
                throw new AcmeException("Challenge failed for " + sDomain);
            }
        }
        if (auth.getStatus() != Status.VALID) {
            throw new AcmeException("Challenge timed out for " + sDomain);
        }
    }

    /**
     * Poll an order until it reaches VALID status.
     */
    private void pollUntilValid(org.shredzone.acme4j.Order order, String sErrorMsg)
            throws Exception {
        for (int i = 0; i < MAX_POLL_ATTEMPTS && order.getStatus() != Status.VALID; i++) {
            Thread.sleep(POLL_INTERVAL_MS);
            order.update();
            if (order.getStatus() == Status.INVALID) {
                throw new AcmeException(sErrorMsg);
            }
        }
        if (order.getStatus() != Status.VALID) {
            throw new AcmeException(sErrorMsg + " (timed out)");
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
     */
    private ExceptionHandle invokeRevokeCertificate(Frame frame, ServiceHandle hMgr,
                                                    ObjectHandle[] ahArg) {
        var sPath     = ((StringHandle) ahArg[0]).getStringValue();
        var achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        var sName     = ((StringHandle) ahArg[2]).getStringValue();
        var sProvider = ((StringHandle) hMgr.getField(0)).getStringValue();

        try {
            switch (sProvider) {
                case "self" -> {}
                case "certbot-staging" -> revokeWithAcme(sPath, achPwd, sName, true);
                case "certbot" -> revokeWithAcme(sPath, achPwd, sName, false);
                default -> {
                    return xException.makeHandle(frame,
                            "Unsupported certificate provider: " + sProvider);
                }
            }

            KeyStoreOperations.deleteKeyStoreEntry(sPath, achPwd, sName);
            return null;
        } catch (Exception e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Revoke a certificate using the ACME protocol via acme4j.
     */
    private void revokeWithAcme(String sStorePath, char[] achPwd, String sName, boolean fStaging)
            throws Exception {
        var keyStore = KeyStoreOperations.loadOrCreateKeyStore(sStorePath, achPwd);
        var cert     = keyStore.getCertificate(sName);

        if (cert instanceof X509Certificate x509Cert) {
            var session = new Session(acmeServerUri(fStaging));
            var accountKeyPair = KeyPairUtils.createKeyPair(2048);

            // register an account (needed for authenticated revocation)
            new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(accountKeyPair)
                    .create(session);

            org.shredzone.acme4j.Certificate.revoke(session, accountKeyPair, x509Cert, null);
        }
    }


    // ----- symmetric key & password management ---------------------------------------------------

    /**
     * Native implementation of
     *     "invokeCreateSymmetricKeyImpl(String path, Password pwd, String name)"
     */
    private ExceptionHandle invokeCreateSymmetricKey(Frame frame, ObjectHandle[] ahArg) {
        var sPath  = ((StringHandle) ahArg[0]).getStringValue();
        var achPwd = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        var sName  = ((StringHandle) ahArg[2]).getStringValue();

        try {
            KeyStoreOperations.createSymmetricKey(sPath, achPwd, sName);
            return null;
        } catch (Exception e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }

    /**
     * Native implementation of
     *     "invokeCreatePasswordImpl(String path, Password pwd, String name, String pwdValue)"
     */
    private ExceptionHandle invokeCreatePassword(Frame frame, ObjectHandle[] ahArg) {
        var sPath     = ((StringHandle) ahArg[0]).getStringValue();
        var achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        var sName     = ((StringHandle) ahArg[2]).getStringValue();
        var sPwdValue = ((StringHandle) ahArg[3]).getStringValue();

        try {
            KeyStoreOperations.createPassword(sPath, achPwd, sName, sPwdValue);
            return null;
        } catch (Exception e) {
            return xException.obscureIoException(frame, e.getMessage());
        }
    }


    // ----- key extraction & password change ------------------------------------------------------

    /**
     * Native implementation of
     *     "Byte[] extractKeyImpl(String|KeyStore pathOrStore, Password pwd, String name)"
     */
    private int invokeExtractKey(Frame frame, ObjectHandle[] ahArg, int iReturn) {
        var hPathOrStore = ahArg[0];
        var hPwd         = xRTKeyStore.getPassword(frame, ahArg[1]);
        var hName        = (StringHandle) ahArg[2];

        var cfResult = frame.f_context.f_container.scheduleIO(
                () -> loadKey(hPathOrStore, hPwd, hName));

        Frame.Continuation continuation = frameCaller -> {
            try {
                var key = cfResult.get();
                return key == null
                        ? frameCaller.raiseException(xException.ioException(frameCaller,
                                "Invalid or inaccessible key \"" + hName.getStringValue() + '"'))
                        : frameCaller.assignValue(iReturn,
                                xArray.makeByteArrayHandle(key.getEncoded(), Mutability.Constant));
            } catch (Throwable e) {
                // TODO: catching Throwable and discarding the cause is bad practice; the
                //  full exception chain (including stack trace) is lost, making debugging
                //  nearly impossible. raiseException should support a Throwable cause so
                //  it can be logged or chained into the XVM exception model.
                return frameCaller.raiseException("Unexpected execution failure " + e);
            }
        };
        return frame.waitForIO(cfResult, continuation);
    }

    private Key loadKey(ObjectHandle hPathOrStore, StringHandle hPwd, StringHandle hName) {
        var achPwd = hPwd.getValue();
        var sKey   = hName.getStringValue();

        try {
            KeyStore keyStore;
            if (hPathOrStore instanceof StringHandle hPath) {
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(new FileInputStream(hPath.getStringValue()), achPwd);
            } else {
                keyStore = ((KeyStoreHandle) hPathOrStore).f_keyStore;
            }
            return keyStore.getKey(sKey, achPwd);
        } catch (Exception e) {
            // TODO: swallowing the exception here loses the root cause; callers have no
            //  way to distinguish "key not found" from "keystore corrupt" or "wrong password".
            //  We should use a proper logging framework (e.g. SLF4J) instead of System.err;
            //  with a real logger, swallowed/wrapped exceptions could at least be emitted at
            //  logger.debug level so they are recoverable when diagnosing production issues.
            System.err.println(Handy.logTime() + " [Debug]: Failed to load key: " + sKey +
                    " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * Native implementation of
     *     "invokeChangeStorePasswordImpl(String path, Password pwd, String newPwd)"
     */
    private ExceptionHandle invokeChangeStorePassword(Frame frame, ObjectHandle[] ahArg) {
        var sPath     = ((StringHandle) ahArg[0]).getStringValue();
        var achPwd    = xRTKeyStore.getPassword(frame, ahArg[1]).getValue();
        var achPwdNew = ((StringHandle) ahArg[2]).getValue();

        try {
            KeyStoreOperations.changeStorePassword(sPath, achPwd, achPwdNew);
            return null;
        } catch (Exception e) {
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

    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final long POLL_INTERVAL_MS = 5000L;
    private TypeConstant m_typeCanonical;
}
