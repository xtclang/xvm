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
import org.xvm.runtime.Runtime;

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
                return invokeCreateCertificate(frame, (ServiceHandle) hTarget, ahArg);

            case "revokeCertificateImpl":
                return invokeRevokeCertificate(frame, ahArg);

            case "createSymmetricKeyImpl":
                return invokeCreateSymmetricKey(frame, ahArg);

            case "createPasswordImpl":
                return invokeCreatePassword(frame, ahArg);

            case "changeStorePasswordImpl":
                return invokeChangeStorePassword(frame, ahArg);

            case "extractKeyImpl":
                return invokeExtractKey(frame, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of
     *     "createCertificateImpl(String path, Password pwd, String name, String dName)"
     */
    private int invokeCreateCertificate(Frame frame, ServiceHandle hMgr, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPwd      = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName     = (StringHandle) ahArg[2];
        StringHandle hDName    = (StringHandle) ahArg[3];
        StringHandle hProvider = (StringHandle) hMgr.getField(0); // "provider" property

        runSilentCommand(
                "keytool", "-delete",
                "-alias",     hName.getStringValue(),
                "-keystore",  hPath.getStringValue(),
                "-storepass", hPwd.getStringValue()
                );

        String sDName = hDName.getStringValue();
        switch (hProvider.getStringValue())
            {
            case "self":
                // create self-signed certificate
                return runNoInputCommand(frame,
                        "keytool", "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                        "-alias", hName.getStringValue(),
                        "-dname", sDName,
                        "-storetype", "PKCS12",
                        "-keystore", hPath.getStringValue(),
                        "-storepass", hPwd.getStringValue()
                        );

            case "certbot":
                {
                File   dirCerts   = getCertsPath(hPath);
                String sCertsPath = dirCerts.getAbsolutePath();

                if (!dirCerts.exists() && !dirCerts.mkdir() || !dirCerts.isDirectory())
                    {
                    return frame.raiseException(xException.ioException(frame,
                            "Cannot create directory: " + sCertsPath));
                    }

                int ofDomain = sDName.indexOf("CN=");
                assert ofDomain > 0;
                String sDomain = sDName.substring(ofDomain + 3);

                int iResult = runCommand(frame, "yes\nyes",
                        "certbot", "certonly",
                        "--staging",
                        "--webroot",
                        "--webroot-path", sCertsPath,
                        "--config-dir",   sCertsPath + File.separator + "config",
                        "--work-dir",     sCertsPath + File.separator + "work",
                        "--logs-dir",     sCertsPath + File.separator + "logs",
                        "--register-unsafely-without-email",
                        "-d", sDomain);
                if (iResult == Op.R_NEXT)
                    {
                    // TODO: process further
                    }
                return iResult;
                }

            default:
                return frame.raiseException(
                        "Unsupported certificate provider: " + hProvider.getStringValue());
            }
        }

    /**
     * Native implementation of
     *     "revokeCertificateImpl(String path, Password pwd, String name)"
     */
    private int invokeRevokeCertificate(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath  = (StringHandle) ahArg[0];
        StringHandle hPwd   = xRTKeyStore.getPassword(frame, ahArg[1]);
        StringHandle hName  = (StringHandle) ahArg[2];

        File   dirCerts   = getCertsPath(hPath);
        String sCertsPath = dirCerts.getAbsolutePath();

        if (dirCerts.isDirectory())
            {
            runCommand(frame, "yes\nyes",
                        "certbot", "remove",
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
        return Op.R_NEXT;
        }

    private File getCertsPath(StringHandle hPath)
        {
        File fileKeystore = Path.of(hPath.getStringValue()).toFile();
        return new File(fileKeystore.getParentFile(), ".certs");
        }

    /**
     * Native implementation of
     *     "invokeCreateSymmetricKeyImpl(String path, Password pwd, String name)"
     */
    private int invokeCreateSymmetricKey(Frame frame, ObjectHandle[] ahArg)
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
    private int invokeCreatePassword(Frame frame, ObjectHandle[] ahArg)
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
            return frame.raiseException(xException.ioException(frame,
                    Runtime.logRuntimeException("Inaccessible key: " + e.getMessage())));
            }
        }

    /**
     * Native implementation of
     *     "invokeChangeStorePasswordImpl(String path, Password pwd, String newPwd)"
     */
    private int invokeChangeStorePassword(Frame frame, ObjectHandle[] ahArg)
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

    private int runSilentCommand(String... cmd)
        {
        return runCommand(null, null, cmd);
        }

    private int runNoInputCommand(Frame frame, String... cmd)
        {
        return runCommand(frame, null, cmd);
        }

    private int runCommand(Frame frame, String sInput, String... cmd)
        {
        // *** IMPORTANT SECURITY NOTE***:
        //  ProcessBuilder does not invoke a shell by default, and we should never take the command
        //  itself (i.e. cmd[0]) from a passed-in argument, which then removes the risk of a shell
        //  injection attack.
        ProcessBuilder builder = new ProcessBuilder(cmd);
        try
            {
            Process process = builder.start();
            if (sInput != null)
                {
                OutputStream out = process.getOutputStream();
                out.write(sInput.getBytes());
                out.close();
                }
            // TODO: remove
            System.out.println("*** running command: " + toString(cmd));
            if (!process.waitFor(30, TimeUnit.SECONDS))
                {
                process.destroy();
                return frame.raiseException(xException.timedOut(frame,
                        Runtime.logRuntimeException("Timed out: " + toString(cmd)), xNullable.NULL));
                }

            if (frame != null && process.exitValue() != 0)
                {
                String sOut = getOutput(process.getInputStream());
                String sErr = getOutput(process.getErrorStream());

                    return frame.raiseException(xException.ioException(frame,
                            Runtime.logRuntimeException(sOut + '\n' + sErr)));
                }

            return Op.R_NEXT;
            }
        catch (Exception e)
            {
            return frame == null
                ? Op.R_NEXT
                : frame.raiseException(Runtime.logRuntimeException(e.getMessage()));
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