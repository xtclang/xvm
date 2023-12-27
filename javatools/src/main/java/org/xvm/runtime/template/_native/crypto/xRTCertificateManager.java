package org.xvm.runtime.template._native.crypto;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.text.xString.StringHandle;


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
        // we could cache the handle as well
        return createServiceHandle(f_container.createServiceContext("CertificateManager"),
                getCanonicalClass(), getCanonicalType());
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "createCertificateImpl":
                return invokeCreateCertificate(frame, ahArg);

            case "createSymmetricKeyImpl":
                return invokeCreateSymmetricKey(frame, ahArg);

            case "createPasswordImpl":
                return invokeCreatePassword(frame, ahArg);

            case "changeStorePasswordImpl":
                return invokeChangeStorePassword(frame, ahArg);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of
     *     "createCertificateImpl(String path, String password, String name, String dName)"
     */
    private int invokeCreateCertificate(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPassword = (StringHandle) ahArg[1];
        StringHandle hName     = (StringHandle) ahArg[2];
        StringHandle hDName    = (StringHandle) ahArg[3];

        runCommand(null, null,
                "keytool", "-delete",
                "-alias", hName.getStringValue(),
                "-keystore", hPath.getStringValue(),
                "-storepass", hPassword.getStringValue()
                );
        return runCommand(frame, null,
                "keytool", "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-alias", hName.getStringValue(),
                "-dname", hDName.getStringValue(),
                "-storetype", "PKCS12",
                "-keystore", hPath.getStringValue(),
                "-storepass", hPassword.getStringValue()
                );
        }

    /**
     * Native implementation of
     *     "invokeCreateSymmetricKeyImpl(String path, String password, String name)"
     */
    private int invokeCreateSymmetricKey(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPassword = (StringHandle) ahArg[1];
        StringHandle hName     = (StringHandle) ahArg[2];

        runCommand(null, null,
                "keytool", "-delete",
                "-alias", hName.getStringValue(),
                "-keystore", hPath.getStringValue(),
                "-storepass", hPassword.getStringValue()
                );
        return runCommand(frame, null,
                "keytool", "-genseckey", "-keyalg", "AES", "-keysize", "256",
                "-alias", hName.getStringValue(),
                "-storetype", "PKCS12",
                "-keystore", hPath.getStringValue(),
                "-storepass", hPassword.getStringValue()
                );
        }

    /**
     * Native implementation of
     *     "invokeCreatePasswordImpl(String path, String storePassword, String name, String passwordValue)"
     */
    private int invokeCreatePassword(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hStorePwd = (StringHandle) ahArg[1];
        StringHandle hName     = (StringHandle) ahArg[2];
        StringHandle hPwdValue = (StringHandle) ahArg[3];

        runCommand(null, null,
                "keytool", "-delete",
                "-alias", hName.getStringValue(),
                "-keystore", hPath.getStringValue(),
                "-storepass", hStorePwd.getStringValue()
                );
        return runCommand(frame, hPwdValue.getStringValue(),
                "keytool", "-importpass",
                "-alias", hName.getStringValue(),
                "-storetype", "PKCS12",
                "-keystore", hPath.getStringValue(),
                "-storepass", hStorePwd.getStringValue()
                );
        }

    /**
     * Native implementation of
     *     "invokeChangeStorePasswordImpl(String path, String password, String newPassword)"
     */
    private int invokeChangeStorePassword(Frame frame, ObjectHandle[] ahArg)
        {
        StringHandle hPath     = (StringHandle) ahArg[0];
        StringHandle hPassword = (StringHandle) ahArg[1];
        StringHandle hPassNew  = (StringHandle) ahArg[2];

        return runCommand(frame, null,
                "keytool", "-storepasswd",
                " -keystore " + hPath.getStringValue(),
                " -storepass " + hPassword.getStringValue() +
                " -new " + hPassNew.getStringValue()
            );
        }

    private int runCommand(Frame frame, String sInput, String... cmd)
        {
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
            process.waitFor();
//            if (frame != null)
//                {
//                log(process.getInputStream(), System.out);
//                log(process.getErrorStream(), System.err);
//                }
            return Op.R_NEXT;
            }
        catch (Exception e)
            {
            return frame == null
                ? Op.R_NEXT
                : frame.raiseException(e.getMessage());
            }
        }

    private void log(InputStream streamIn, PrintStream streamOut)
            throws IOException
        {
        BufferedReader reader = new BufferedReader(new InputStreamReader(streamIn));

        String sLine;
        while ((sLine = reader.readLine()) != null)
            {
            streamOut.println(sLine);
            }
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }