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
                        invokeRevokeCertificate(frame, ahArg));

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

        String  sDName   = hDName.getStringValue();
        String  sName    = hName.getStringValue();
        boolean fStaging = false;
        switch (hProvider.getStringValue())
            {
            case "self":
                // create self-signed certificate
                return runNoInputCommand(frame,
                        "keytool", "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-validity", "90",
                        "-alias", sName,
                        "-dname", sDName,
                        "-storetype", "PKCS12",
                        "-keystore", hStorePath.getStringValue(),
                        "-storepass", hPwd.getStringValue()
                        );

            case "certbot-staging":
                fStaging = true;
                // fall-through
            case "certbot":
                {
                File   dirCerts   = getCertsPath(hStorePath);
                String sCertsPath = dirCerts.getAbsolutePath();
                if (!dirCerts.exists() && !dirCerts.mkdir() || !dirCerts.isDirectory())
                    {
                    return xException.ioException(frame, "Cannot create directory: " + sCertsPath);
                    }

                File   dirChallenge   = getChallengePath(hStorePath);
                String sChallengePath = dirChallenge.getAbsolutePath();
                if (!dirChallenge.exists() && !dirChallenge.mkdir() || !dirChallenge.isDirectory())
                    {
                    return xException.ioException(frame, "Cannot create directory: " + sChallengePath);
                    }

                int ofDomain = sDName.indexOf("CN=");
                assert ofDomain > 0;
                String sDomain = sDName.substring(ofDomain + 3);

                // the "-key-path" and "--fullchain-path" options don't have any effect, so we
                // need to rely on the default behavior placing the pem files under "config/live"
                String sConfigPath = sCertsPath + File.separator + "config";
                ExceptionHandle hFailure = fStaging
                    ? runCommand(frame, "yes\nyes",
                        "certbot", "certonly",
                        "--staging",
                        "--webroot",
                        "--webroot-path",   sChallengePath,
                        "--config-dir",     sConfigPath,
                        "--work-dir",       sCertsPath + File.separator + "work",
                        "--logs-dir",       sCertsPath + File.separator + "logs",
                        "--register-unsafely-without-email",
                        "-d", sDomain)
                    : runCommand(frame, "yes\nyes",
                        "certbot", "certonly",
                        "--webroot",
                        "--webroot-path",   sChallengePath,
                        "--config-dir",     sConfigPath,
                        "--work-dir",       sCertsPath + File.separator + "work",
                        "--logs-dir",       sCertsPath + File.separator + "logs",
                        "--register-unsafely-without-email",
                        "-d", sDomain);

                // the "certonly" command above could fail if there was already a valid certificate,
                // in which case we could run the conversion routine below regardless
                String sDestPath = sConfigPath + File.separator + "live" + File.separator + sName;
                if (new File(sDestPath).exists())
                    {
                    // convert "pem" files into "pkcs12" format
                    hFailure = runCommand(frame, null,
                        "openssl", "pkcs12", "-export",
                        "-out",      sCertsPath + File.separator + sName + ".p12",
                        "-inkey",    sDestPath + File.separator + "privkey.pem",
                        "-in",       sDestPath + File.separator + "fullchain.pem",
                        "-name",     sName,
                        "-passin",   "pass:" + hPwd.getStringValue(),
                        "-passout",  "pass:" + hPwd.getStringValue()
                        );

                    if (hFailure == null)
                        {
                        // transfer the key-pair into the target keystore
                        hFailure = runCommand(frame, null,
                            "keytool", "-importkeystore",
                            "-srckeystore",   sCertsPath + File.separator + sName + ".p12",
                            "-srcstoretype",  "PKCS12",
                            "-destkeystore",  hStorePath.getStringValue(),
                            "-deststoretype", "PKCS12",
                            "-alias",         sName,
                            "-srcstorepass",  hPwd.getStringValue(),
                            "-deststorepass", hPwd.getStringValue()
                            );
                        }
                    }
                return hFailure;
                }

            default:
                return xException.makeHandle(frame,
                    "Unsupported certificate provider: " + hProvider.getStringValue());
            }
        }

    /**
     * Native implementation of
     *     "revokeCertificateImpl(String path, Password pwd, String name)"
     */
    private ExceptionHandle invokeRevokeCertificate(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath = (StringHandle) ahArg[0];
        StringHandle hPwd  = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName = (StringHandle) ahArg[2];

        File   dirCerts   = getCertsPath(hPath);
        String sCertsPath = dirCerts.getAbsolutePath();

        if (dirCerts.isDirectory())
            {
            runCommand(frame, "yes\nyes",
                        "certbot", "revoke",
                        "--staging",
                        "--config-dir", sCertsPath + File.separator + "config",
                        "--work-dir",   sCertsPath + File.separator + "work",
                        "--logs-dir",   sCertsPath + File.separator + "logs",
                        "--cert-name",  hName.getStringValue(),
                        "--reason",     "unspecified"
                      );
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

        try
            {
            char[]   achPwd = hPwd.getValue();
            String   sKey   = hName.getStringValue();
            KeyStore keyStore;
            if (hPathOrStore instanceof StringHandle hPath)
                {
                File fileStore = new File(hPath.getStringValue());
                keyStore  = KeyStore.getInstance("PKCS12");
                keyStore.load(new FileInputStream(fileStore), achPwd);
                }
            else
                {
                KeyStoreHandle hKeyStore = (KeyStoreHandle) hPathOrStore;
                keyStore = hKeyStore.f_keyStore;
                }

            Key key = keyStore.getKey(sKey, achPwd);
            if (key == null)
                {
                return frame.raiseException(
                        xException.ioException(frame, "Invalid or inaccessible key"));
                }
            byte[] abPrivate = key.getEncoded();
            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abPrivate, xArray.Mutability.Constant));
            }
        catch (Exception e)
            {
            return frame.raiseException(
                xException.obscureIoException(frame, "Inaccessible key: " + e.getMessage()));
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
     * @return a exception handler or null if operation succeeded
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