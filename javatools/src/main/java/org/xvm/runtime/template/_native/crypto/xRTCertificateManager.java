package org.xvm.runtime.template._native.crypto;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.nio.file.Path;

import java.security.Key;
import java.security.KeyStore;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.crypto.xRTKeyStore.KeyStoreHandle;


/**
 * Native implementation of the xRTCertificateManager.x service.
 */
public class xRTCertificateManager
        extends xService
    {
    public static xRTCertificateManager INSTANCE;

    public xRTCertificateManager(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("createCertificateImpl"  , null, VOID);
        markNativeMethod("revokeCertificateImpl"  , null, VOID);
        markNativeMethod("createSymmetricKeyImpl" , null, VOID);
        markNativeMethod("createPasswordImpl"     , null, VOID);
        markNativeMethod("changeStorePasswordImpl", null, VOID);
        markNativeMethod("extractKeyImpl"         , null, BYTES);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        TypeConstant type = m_typeCanonical;
        if (type == null)
            {
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
    public ObjectHandle ensureManager(Frame frame, ObjectHandle hOpts)
        {
        StringHandle hProvider = hOpts instanceof StringHandle hS
                ? hS
                : xString.makeHandle("self");

        // we could cache the handles based on the provider
        ClassComposition clz  = getCanonicalClass();
        ServiceHandle    hMgr = createServiceHandle(f_container.
                createServiceContext("CertificateManager"), clz, getCanonicalType());
        hMgr.setField(0, hProvider); // "provider" property
        return hMgr;
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "createCertificateImpl":
                return invokeAsIOTask(frame, () ->
                        invokeCreateCertificate(frame, (ServiceHandle) hTarget, ahArg));

            case "revokeCertificateImpl":
                return invokeAsIOTask(frame, () ->
                        invokeRevokeCertificate(frame, (ServiceHandle) hTarget, ahArg));

            case "createSymmetricKeyImpl":
                return invokeAsIOTask(frame, () ->
                        invokeCreateSymmetricKey(frame, ahArg));

            case "createPasswordImpl":
                return invokeAsIOTask(frame, () ->
                        invokeCreatePassword(frame, ahArg));

            case "changeStorePasswordImpl":
                return invokeAsIOTask(frame, () ->
                        invokeChangeStorePassword(frame, ahArg));

            case "extractKeyImpl":
                return invokeExtractKey(frame, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    private int invokeAsIOTask(Frame frame, Callable<ExceptionHandle> task)
        {
        CompletableFuture<ExceptionHandle> cfResult =
                frame.f_context.f_container.scheduleIO(task);
        Frame.Continuation continuation = frameCaller ->
            {
            try
                {
                ExceptionHandle hFailure = cfResult.get();
                return hFailure == null ? Op.R_NEXT : frameCaller.raiseException(hFailure);
                }
            catch (Throwable e)
                {
                return frameCaller.raiseException("Unexpected execution failure " + e);
                }
            };

        return frame.waitForIO(cfResult, continuation);
        }

    /**
     * Native implementation of
     *     "createCertificateImpl(String path, Password pwd, String name, String dName)"
     */
    private ExceptionHandle invokeCreateCertificate(Frame frame, ServiceHandle hMgr, ObjectHandle[] ahArg)
        {
        StringHandle hStorePath = (StringHandle) ahArg[0];
        StringHandle hPwd       = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName      = (StringHandle) ahArg[2];
        StringHandle hDName     = (StringHandle) ahArg[3];
        StringHandle hProvider  = (StringHandle) hMgr.getField(0); // "provider" property

        runSilentCommand(
                "keytool", "-delete",
                "-alias",     hName.getStringValue(),
                "-keystore",  hStorePath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );

        switch (hProvider.getStringValue())
            {
            case "self":
                // create self-signed certificate
                return runNoInputCommand(frame,
                        "keytool", "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-validity", "90",
                        "-alias", hName.getStringValue(),
                        "-dname", hDName.getStringValue(),
                        "-storetype", "PKCS12",
                        "-keystore", hStorePath.getStringValue(),
                        "-storepass", hPwd.getStringValue()
                        );

            case "certbot-staging":
                return createCertificateWithCertbot(frame, hStorePath, hPwd, hName, hDName, true);

            case "certbot":
                return createCertificateWithCertbot(frame, hStorePath, hPwd, hName, hDName, false);

            default:
                return xException.makeHandle(frame,
                    "Unsupported certificate provider: " + hProvider.getStringValue());
            }
        }

    private ExceptionHandle createCertificateWithCertbot(
            Frame frame, StringHandle hStorePath, StringHandle hPwd, StringHandle hName,
            StringHandle hDName, boolean fStaging)
        {
        String sDName = hDName.getStringValue();
        String sName  = hName.getStringValue();

        // ensure the
        File   dirCerts  = getCertsPath(hStorePath);
        String sCertsDir = dirCerts.getAbsolutePath();
        if (!dirCerts.exists() && !dirCerts.mkdir() || !dirCerts.isDirectory())
            {
            return xException.ioException(frame, "Cannot create directory: " + sCertsDir);
            }

        File   dirChallenge  = getChallengePath(hStorePath);
        String sChallengeDir = dirChallenge.getAbsolutePath();
        if (!dirChallenge.exists() && !dirChallenge.mkdir() || !dirChallenge.isDirectory())
            {
            return xException.ioException(frame, "Cannot create directory: " + sChallengeDir);
            }

        int ofDomain = sDName.indexOf("CN=");
        assert ofDomain > 0;
        String sDomain = sDName.substring(ofDomain + 3);

        String sKeyPath  = sCertsDir + File.separator + sDomain + ".key";
        String sCsrPath  = sCertsDir + File.separator + sDomain + ".csr";

        try {
            ExceptionHandle hFailure;

            // create the key
            hFailure = runCommand(frame, null,
                "openssl", "genpkey", "-algorithm", "RSA",
                "-out", sKeyPath,
                "-pkeyopt", "rsa_keygen_bits:2048");
            if (hFailure != null)
                {
                return hFailure;
                }

            // create the CSR; note that openssl requires a DName in a '/'-delimited format
            //
            // Note: since we're using Let's Encrypt, only the "CN" and "SAN" fields will be filled
            // based on the CSR; the rest gets filtered out
            hFailure = runCommand(frame, null,
                "openssl", "req", "-new",
                "-key",  sKeyPath,
                "-out",  sCsrPath,
                "-subj", '/' + sDName.replace(',', '/'));
            if (hFailure != null)
                {
                return hFailure;
                }

            // we don't use the "cert" and "chain" files, but need to specify the path regardless
            // to avoid them being placed at some random location
            String sConfigDir    = sCertsDir + File.separator + "config";
            String sWorkDir      = sCertsDir + File.separator + "work";
            String sLogDir       = sCertsDir + File.separator + "logs";
            String sCertPath     = sCertsDir + File.separator + "cert.pem";
            String sChainPath    = sCertsDir + File.separator + "chain.pem";
            String sFullCertPath = sCertsDir + File.separator + "fullchain.pem";

            // remove existing files (certbot doesn't override anything)
            new File(sCertPath).delete();
            new File(sChainPath).delete();
            new File(sFullCertPath).delete();

            // ask Let's Encrypt now!
            hFailure = fStaging
                ? runCommand(frame, "yes\nyes",
                    "certbot", "certonly",
                    "--staging",
                    "--webroot",
                    "--webroot-path",   sChallengeDir,
                    "--config-dir",     sConfigDir,
                    "--work-dir",       sWorkDir,
                    "--logs-dir",       sLogDir,
                    "--key-path",       sKeyPath,
                    "--cert-path",      sCertPath,
                    "--chain-path",     sChainPath,
                    "--fullchain-path", sFullCertPath,
                    "-d",               sDomain,
                    "--csr",            sCsrPath,
                    "--register-unsafely-without-email")
                : runCommand(frame, "yes\nyes",
                    "certbot", "certonly",
                    "--webroot",
                    "--webroot-path",   sChallengeDir,
                    "--config-dir",     sConfigDir,
                    "--work-dir",       sWorkDir,
                    "--logs-dir",       sLogDir,
                    "--key-path",       sKeyPath,
                    "--cert-path",      sCertPath,
                    "--chain-path",     sChainPath,
                    "--fullchain-path", sFullCertPath,
                    "-d",               sDomain,
                    "--csr",            sCsrPath,
                    "--register-unsafely-without-email");

            if (hFailure != null)
                {
                return hFailure;
                }

            // convert certificates from "pem" to "pkcs12" format
            String sTempStorePath = sCertsDir + File.separator + sName + ".p12";
            hFailure = runCommand(frame, null,
                "openssl", "pkcs12", "-export",
                "-out",      sTempStorePath,
                "-inkey",    sKeyPath,
                "-in",       sFullCertPath,
                "-name",     sName,
                "-passin",   "pass:" + hPwd.getStringValue(),
                "-passout",  "pass:" + hPwd.getStringValue()
                );

            if (hFailure != null)
                {
                return hFailure;
                }

            // transfer the key-pair into the target keystore
            hFailure = runCommand(frame, null,
                "keytool", "-importkeystore",
                "-srckeystore",   sTempStorePath,
                "-srcstoretype",  "PKCS12",
                "-destkeystore",  hStorePath.getStringValue(),
                "-deststoretype", "PKCS12",
                "-alias",         sName,
                "-srcstorepass",  hPwd.getStringValue(),
                "-deststorepass", hPwd.getStringValue()
                );

            new File(sTempStorePath).delete(); // it's encrypted, but still no reason to leave
            return hFailure;
            }
        finally
            {
            try
                {
                // no matter what; don't leave the unencrypted key file
                new File(sKeyPath).delete();
                }
            catch (Exception ignore) {}
            }
        }

    /**
     * Native implementation of
     *     "revokeCertificateImpl(String path, Password pwd, String name)"
     */
    private ExceptionHandle invokeRevokeCertificate(Frame frame, ServiceHandle hMgr, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPwd      = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName     = (StringHandle) ahArg[2];
        StringHandle hProvider = (StringHandle) hMgr.getField(0); // "provider" property

        File dirCerts  = getCertsPath(hPath);
        if (dirCerts.isDirectory())
            {
            String sCertsDir = dirCerts.getAbsolutePath();

            switch (hProvider.getStringValue())
                {
                case "self":
                    break;

                case "certbot-staging":
                    runCommand(frame, "yes\nyes",
                                "certbot", "revoke",
                                "--staging",
                                "--config-dir", sCertsDir + File.separator + "config",
                                "--work-dir",   sCertsDir + File.separator + "work",
                                "--logs-dir",   sCertsDir + File.separator + "logs",
                                "--cert-name",  hName.getStringValue(),
                                "--reason",     "unspecified"
                              );
                    break;

                case "certbot":
                    runCommand(frame, "yes\nyes",
                                "certbot", "revoke",
                                "--config-dir", sCertsDir + File.separator + "config",
                                "--work-dir",   sCertsDir + File.separator + "work",
                                "--logs-dir",   sCertsDir + File.separator + "logs",
                                "--cert-name",  hName.getStringValue(),
                                "--reason",     "unspecified"
                              );
                    break;

                default:
                    return xException.makeHandle(frame,
                        "Unsupported certificate provider: " + hProvider.getStringValue());
                }
            }

        runSilentCommand(
                "keytool", "-delete",
                "-alias",     hName.getStringValue(),
                "-keystore",  hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );
        return null;
        }

    private File getCertsPath(StringHandle hPath)
        {
        File fileKeystore = Path.of(hPath.getStringValue()).toFile();
        return new File(fileKeystore.getParentFile(), ".certs");
        }

    private File getChallengePath(StringHandle hPath)
        {
        File fileKeystore = Path.of(hPath.getStringValue()).toFile();
        return new File(fileKeystore.getParentFile(), ".challenge");
        }

    /**
     * Native implementation of
     *     "invokeCreateSymmetricKeyImpl(String path, Password pwd, String name)"
     */
    private ExceptionHandle invokeCreateSymmetricKey(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath = (StringHandle) ahArg[0];
        StringHandle hPwd  = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName = (StringHandle) ahArg[2];

        runSilentCommand(
                "keytool", "-delete",
                "-alias",     hName.getStringValue(),
                "-keystore",  hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );
        return runNoInputCommand(frame,
                "keytool", "-genseckey", "-keyalg", "AES", "-keysize", "256",
                "-alias",     hName.getStringValue(),
                "-storetype", "PKCS12",
                "-keystore",  hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );
        }

    /**
     * Native implementation of
     *     "invokeCreatePasswordImpl(String path, Password pwd, String name, String pwdValue)"
     */
    private ExceptionHandle invokeCreatePassword(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPwd      = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName     = (StringHandle) ahArg[2];
        StringHandle hPwdValue = (StringHandle) ahArg[3];

        runSilentCommand(
                "keytool", "-delete",
                "-alias",     hName.getStringValue(),
                "-keystore",  hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );
        return runCommand(frame, hPwdValue.getStringValue(),
                "keytool", "-importpass",
                "-alias", hName.getStringValue(),
                "-storetype", "PKCS12",
                "-keystore", hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );
        }

    /**
     * Native implementation of
     *     "Byte[] extractKeyImpl(String|KeyStore pathOrStore, Password pwd, String name)"
     */
    private int invokeExtractKey(Frame frame, ObjectHandle[] ahArg, int iReturn)
        {
        ObjectHandle hPathOrStore = ahArg[0];
        StringHandle hPwd         = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName        = (StringHandle) ahArg[2];

        CompletableFuture<Key> cfResult = frame.f_context.f_container.scheduleIO(
                () -> loadKey(hPathOrStore, hPwd, hName));
        Frame.Continuation continuation = frameCaller ->
            {
            try
                {
                Key key = cfResult.get();
                return key == null
                    ? frameCaller.raiseException(xException.ioException(frameCaller,
                        "Invalid or inaccessible key \"" + hName.getStringValue() + '"'))
                    : frameCaller.assignValue(iReturn,
                        xArray.makeByteArrayHandle(key.getEncoded(), Mutability.Constant));
                }
            catch (Throwable e)
                {
                return frameCaller.raiseException("Unexpected execution failure " + e);
                }
            };

        return frame.waitForIO(cfResult, continuation);
        }

    private Key loadKey(ObjectHandle hPathOrStore, StringHandle hPwd, StringHandle hName)
        {
        try
            {
            char[] achPwd = hPwd.getValue();
            String sKey   = hName.getStringValue();

            KeyStore keyStore;
            if (hPathOrStore instanceof StringHandle hPath)
                {
                File fileStore = new File(hPath.getStringValue());
                keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(new FileInputStream(fileStore), achPwd);
                }
            else
                {
                KeyStoreHandle hKeyStore = (KeyStoreHandle) hPathOrStore;
                keyStore = hKeyStore.f_keyStore;
                }

            return keyStore.getKey(sKey, achPwd);
            }
        catch (Exception e)
            {
            return null;
            }
        }

    /**
     * Native implementation of
     *     "invokeChangeStorePasswordImpl(String path, Password pwd, String newPwd)"
     */
    private ExceptionHandle invokeChangeStorePassword(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath   = (StringHandle) ahArg[0];
        StringHandle hPwd    = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hPwdNew = (StringHandle) ahArg[2];

        return runNoInputCommand(frame,
                "keytool", "-storepasswd",
                "-keystore", hPath.getStringValue(),
                "-storepass", hPwd.getStringValue(),
                "-new ", hPwdNew.getStringValue()
            );
        }

    private ExceptionHandle runSilentCommand(String... cmd)
        {
        return runCommand(null, null, cmd);
        }

    private ExceptionHandle runNoInputCommand(Frame frame, String... cmd)
        {
        return runCommand(frame, null, cmd);
        }

    /**
     * @return an exception handler or null if operation succeeded
     */
    private ExceptionHandle runCommand(Frame frame, String sInput, String... cmd)
        {
        // *** IMPORTANT SECURITY NOTE***:
        //  ProcessBuilder does not invoke a shell by default, and we should never take the command
        //  itself (i.e. cmd[0]) from a passed-in argument, which then removes the risk of a shell
        //  injection attack.
        ProcessBuilder builder = new ProcessBuilder(cmd);
        try
            {
            // TODO: remove
            System.out.println("*** running command: " + toString(cmd));

            Process process = builder.start();
            if (sInput != null)
                {
                OutputStream out = process.getOutputStream();
                out.write(sInput.getBytes());
                out.close();
                }

            if (!process.waitFor(300, TimeUnit.SECONDS))
                {
                process.destroy();
                return xException.timedOut(frame, "Timed out: " + cmd[0], xNullable.NULL);
                }

            if (frame != null && process.exitValue() != 0)
                {
                String sOut = getOutput(process.getInputStream());
                String sErr = getOutput(process.getErrorStream());

                return xException.obscureIoException(frame, sOut + '\n' + sErr);
                }

            return null;
            }
        catch (Exception e)
            {
            return frame == null ? null : xException.makeObscure(frame, e.getMessage());
            }
        }

    /**
     * Get a message from the specified input stream.
     *
     * @return an error message
     */
    private String getOutput(InputStream streamIn)
        {
        BufferedReader reader = new BufferedReader(new InputStreamReader(streamIn));
        StringBuilder  sb     = new StringBuilder();
        try
            {
            String sLine;
            while ((sLine = reader.readLine()) != null)
                {
                if (!sb.isEmpty())
                    {
                    sb.append('\n');
                    }
                sb.append(sLine);
                }
            }
        catch (IOException ignore) {}

        return sb.toString();
        }

    private String toString(String... cmd)
        {
        StringBuilder sb = new StringBuilder();
        for (String s : cmd)
            {
            sb.append(' ')
              .append(s);
            }
        return sb.substring(1);
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }