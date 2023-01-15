package org.xvm.runtime.template._native.crypto;


import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native implementation of the xRTKeyStore.x service.
 */
public class xRTKeyStore
        extends xService
    {
    public static xRTKeyStore INSTANCE;

    public xRTKeyStore(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("aliases");

        markNativeMethod("getIssuer"   , STRING, STRING);
        markNativeMethod("getNotBefore", STRING, null);
        markNativeMethod("getNotAfter" , STRING, null);
        markNativeMethod("getTbsCert"  , STRING, null);
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
                    "KeyStore"));
            }
        return type;
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureKeyStore(Frame frame, ObjectHandle hOpts)
        {
        try
            {
            GenericHandle hInfo    = (GenericHandle) hOpts;
            ArrayHandle   hContent = (ArrayHandle) hInfo.getField(frame, "content");
            StringHandle  hPwd     = (StringHandle) hInfo.getField(frame, "password");

            byte[] abStore = xByteArray.getBytes(hContent);
            char[] achPwd  = hPwd.getValue();

            KeyStore    keyStore = KeyStore.getInstance("PKCS12");
            InputStream in       = new ByteArrayInputStream(abStore);

            keyStore.load(in, achPwd);

            KeyManagerFactory keyManager = KeyManagerFactory.getInstance("SunX509");
            keyManager.init(keyStore, achPwd);

            TrustManagerFactory trustManager = TrustManagerFactory.getInstance("SunX509");
            trustManager.init(keyStore);

            ServiceContext context  = f_container.createServiceContext("KeyStore");
            ServiceHandle  hService = new KeyStoreHandle(getCanonicalClass(f_container), context,
                                            keyStore, keyManager, trustManager);
            context.setService(hService);
            return hService;
            }
        catch (Exception e)
            {
            return new DeferredCallHandle(
                    xException.makeHandle(frame, "Illegal KeyStore arguments"));
            }
        }


    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "aliases":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;

                ArrayList<StringHandle> listNames = new ArrayList<>();
                try
                    {
                    for (Enumeration<String> en = hStore.f_keyStore.aliases(); en.hasMoreElements();)
                        {
                        listNames.add(xString.makeHandle(en.nextElement()));
                        }
                    return frame.assignValue(iReturn,
                        xArray.makeStringArrayHandle(listNames.toArray(Utils.STRINGS_NONE)));
                    }
                catch (KeyStoreException e)
                    {
                    // TODO GG: dedicated KeyStore exception
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "getIssuer":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle)   hArg;
                try
                    {
                    Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());
                    return cert instanceof X509Certificate cert509
                            ? frame.assignValue(iReturn,
                                    xString.makeHandle(cert509.getIssuerX500Principal().getName()))
                            : frame.raiseException(xException.makeHandle(frame,
                                    "Unsupported standard: " + cert.getType()));
                    }
                catch (KeyStoreException e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "getTbsCert":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle)   hArg;
                try
                    {
                    Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());
                    return cert instanceof X509Certificate cert509
                            ? frame.assignValue(iReturn,
                                    xByteArray.makeByteArrayHandle(cert509.getTBSCertificate(), Mutability.Constant))
                            : frame.raiseException(xException.makeHandle(frame,
                                    "Unsupported standard: " + cert.getType()));
                    }
                catch (GeneralSecurityException e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getNotBefore":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];
                try
                    {
                    Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());
                    return cert instanceof X509Certificate cert509
                            ? computeDate(frame, cert509.getNotBefore(), aiReturn)
                            : frame.raiseException(xException.makeHandle(frame,
                                    "Unsupported standard: " + cert.getType()));
                    }
                catch (KeyStoreException e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }

            case "getNotAfter":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];
                try
                    {
                    Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());
                    return cert instanceof X509Certificate cert509
                            ? computeDate(frame, cert509.getNotAfter(), aiReturn)
                            : frame.raiseException(xException.makeHandle(frame,
                                    "Unsupported standard: " + cert.getType()));
                    }
                catch (KeyStoreException e)
                    {
                    return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
                    }
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    protected int computeDate(Frame frame, Date date, int[] aiReturn)
        {
        if (date == null)
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        int iYear  = cal.get(Calendar.YEAR);
        int iMonth = cal.get(Calendar.MONTH);
        int iDay   = cal.get(Calendar.DAY_OF_WEEK);
        return frame.assignValues(aiReturn, xBoolean.TRUE,
                    xInt.makeHandle(iYear), xInt.makeHandle(iMonth), xInt.makeHandle(iDay));
        }

    /**
     * Native handle holding the KeyStore data.
     */
    protected static class KeyStoreHandle
                extends ServiceHandle
        {
        public KeyStoreHandle(TypeComposition clz, ServiceContext ctx,
                KeyStore keyStore, KeyManagerFactory keyManager, TrustManagerFactory trustManager)
            {
            super(clz, ctx);

            f_keyStore     = keyStore;
            f_keyManager   = keyManager;
            f_trustManager = trustManager;
            m_fMutable     = false;
            }

        /**
         * The wrapped {@link KeyStore}.
         */
        public final KeyStore f_keyStore;

        /**
         * The underlying {@link KeyManagerFactory}.
         */
        public final KeyManagerFactory f_keyManager;

        /**
         * The underlying {@link KeyManagerFactory}.
         */
        public final TrustManagerFactory f_trustManager;
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }