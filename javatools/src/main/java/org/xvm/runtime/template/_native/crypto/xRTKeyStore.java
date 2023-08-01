package org.xvm.runtime.template._native.crypto;


import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.security.spec.ECParameterSpec;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.crypto.interfaces.PBEKey;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
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
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.SecretHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTBooleanDelegate;


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

        markNativeMethod("entryType"         , STRING, null);
        markNativeMethod("getKeyInfo"        , STRING, null);
        markNativeMethod("getCertificateInfo", STRING, null);
        markNativeMethod("getPasswordInfo"   , STRING, null);

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

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, achPwd);

            X509KeyManager keyManager = null;
            for (KeyManager mgr : keyManagerFactory.getKeyManagers())
                {
                if (mgr instanceof X509KeyManager m)
                    {
                    keyManager = m;
                    break;
                    }
                }
            if (keyManager == null)
                {
                return new DeferredCallHandle(
                        xException.makeHandle(frame, "No X509KeyManager available"));
                }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(keyStore);

            X509TrustManager trustManager = null;
            for (TrustManager mgr : trustManagerFactory.getTrustManagers())
                {
                if (mgr instanceof X509TrustManager m)
                    {
                    trustManager = m;
                    break;
                    }
                }
            if (trustManager == null)
                {
                return new DeferredCallHandle(
                        xException.makeHandle(frame, "No X509TrustManager available"));
                }


            ServiceContext  context  = f_container.createServiceContext("KeyStore");
            TypeComposition clzStore = getCanonicalClass(f_container);
            ServiceHandle   hService = new KeyStoreHandle(clzStore, context, keyStore, achPwd,
                                            keyManager, trustManager);
            context.setService(hService);

            MethodStructure ctor = f_struct.findConstructor(); // default constructor
            assert ctor != null;

            // this is a bit of a hack; since the injected service ic constructed natively, we're
            // calling the default constructor as a regular method, skipping initializers/finalizers
            CallChain chain = clzStore.getMethodCallChain(ctor.getIdentityConstant().getSignature());
            switch (invoke1(frame, chain, hService, Utils.OBJECTS_NONE, Op.A_IGNORE))
                {
                case Op.R_NEXT:
                    return hService;

                case Op.R_CALL:
                    Frame frameNext = frame.m_frameNext;
                    frameNext.addContinuation(frameCaller ->
                        {
                        frameCaller.pushStack(hService);
                        return Op.R_NEXT;
                        });
                    return new DeferredCallHandle(frameNext);

                default:
                    throw new IllegalStateException();
                }
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

                try
                    {
                    ArrayList<String> listNames = Collections.list(hStore.f_keyStore.aliases());
                    return frame.assignValue(iReturn,
                            xString.makeArrayHandle(listNames.toArray(Utils.NO_NAMES)));
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
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "entryType":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeEntryType(frame, hStore, hName, aiReturn);
                }

            case "getCertificateInfo":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeGetCertificateInfo(frame, hStore, hName, aiReturn);
                }

            case "getKeyInfo":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeGetKeyInfo(frame, hStore, hName, aiReturn);
                }

            case "getPasswordInfo":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeGetPasswordInfo(frame, hStore, hName, aiReturn);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Native implementation of
     *   "conditional (String   issuer,
     *                Int       version,
     *                Int       notBeforeYear,
     *                Int       notBeforeMonth,
     *                Int       notBeforeDay,
     *                Int       notAfterYear,
     *                Int       notAfterMonth,
     *                Int       notAfterDay,
     *                Boolean[] usageFlags,
     *                String    signatureAlgorithm,
     *                Byte[]    signatureBytes,
     *                String    publicKeyAlgorithm,
     *                Int       publicKeySize,
     *                Byte[]    publicKeyBytes,
     *                Object    publicSecret,
     *                Byte[]    derValue
     *                )
     *      getCertificateInfo(String name)"
     */
    private int invokeGetCertificateInfo(Frame frame, KeyStoreHandle hStore, StringHandle hName,
                                         int[] aiReturn)
        {
        try
            {
            Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());
            if (!(cert instanceof X509Certificate cert509))
                {
                return cert == null
                    ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                    : frame.raiseException(xException.makeHandle(frame,
                            "Unsupported standard: " + cert.getType()));
                }

            Date dateNotBefore = cert509.getNotBefore();
            Date dateNotAfter  = cert509.getNotAfter();
            if (dateNotBefore == null || dateNotAfter == null)
                {
                // invalid certificate
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }

            // issuer
            String sIssuer = cert509.getIssuerX500Principal().toString();

            // version
            int nVersion = cert509.getVersion();

            // keyUsage
            boolean[] afUsage = cert509.getKeyUsage();
            if (afUsage == null)
                {
                afUsage = new boolean[0];
                }
            int    cUsage  = afUsage.length;
            byte[] abUsage = xRTBooleanDelegate.toBytes(afUsage);

            // signature
            String sSigAlgName = cert509.getSigAlgName();
            byte[] abSignature = cert509.getSignature();

            // public key
            PublicKey publicKey  = cert509.getPublicKey();
            String    sAlgorithm = publicKey.getAlgorithm();
            int       cKeyBits   = getPublicKeyLength(publicKey);
            byte[]    abPublic   = publicKey.getEncoded();

            // DER bytes
            byte[] abDer = cert509.getTBSCertificate();

            // create the arguments
            List<ObjectHandle> list = new ArrayList<>(9);
            list.add(xBoolean.TRUE);
            list.add(xString.makeHandle(sIssuer));
            list.add(xInt64.makeHandle(nVersion));
            addDate(dateNotBefore, list);
            addDate(dateNotAfter, list);
            list.add(xArray.makeBooleanArrayHandle(abUsage, cUsage, Mutability.Constant));
            list.add(xString.makeHandle(sSigAlgName));
            list.add(xByteArray.makeByteArrayHandle(abSignature, Mutability.Constant));
            list.add(xString.makeHandle(sAlgorithm));
            list.add(xInt64.makeHandle(cKeyBits >>> 3));
            list.add(xByteArray.makeByteArrayHandle(abPublic, Mutability.Constant));
            list.add(new SecretHandle(publicKey));
            list.add(xByteArray.makeByteArrayHandle(abDer, Mutability.Constant));

            return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
            }
        }

    private static void addDate(Date date, List<ObjectHandle> list)
        {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        list.add(xInt64.makeHandle(cal.get(Calendar.YEAR)));
        list.add(xInt64.makeHandle(cal.get(Calendar.MONTH) + 1));
        list.add(xInt64.makeHandle(cal.get(Calendar.DAY_OF_MONTH)));
        }

    private static int getPublicKeyLength(PublicKey puk)
            throws InvalidKeyException
        {
        if (puk instanceof RSAPublicKey rsaKey)
            {
            return rsaKey.getModulus().bitLength();
            }

        if (puk instanceof DSAPublicKey dsaKey)
            {
            DSAParams params = dsaKey.getParams();
            return params == null
                ? dsaKey.getY().bitLength()
                : params.getP().bitLength();
            }

        if (puk instanceof ECPublicKey ecKey)
            {
            ECParameterSpec spec = ecKey.getParams();
            return spec == null ? 0 :spec.getOrder().bitLength();
            }

        throw new InvalidKeyException("Unsupported key: " + puk);
        }

    /**
     * Native implementation of "conditional Int isKey(String name)".
     */
    private int invokeEntryType(Frame frame, KeyStoreHandle hStore, StringHandle hName,
                                int[] aiReturn)
        {
        KeyStore keyStore = hStore.f_keyStore;
        String   sName    = hName.getStringValue();
        try
            {
            if (keyStore.isKeyEntry(sName))
                {
                int nType;
                if (keyStore.getCertificate(sName) == null)
                    {
                    Key key = keyStore.getKey(sName, hStore.f_achPwd);

                    assert key != null;

                    // unfortunately com.sun.crypto.provider.PBEKey is not public
                    nType = key instanceof PBEKey || key.getClass().getSimpleName().equals("PBEKey")
                            ? 3  // Password
                            : 1; // Secret
                    }
                else
                    {
                    nType = 1; // Pair
                    }
                return frame.assignValues(aiReturn, xBoolean.TRUE, xInt64.makeHandle(nType));
                }
            if (keyStore.isCertificateEntry(sName))
                {
                return frame.assignValues(aiReturn, xBoolean.TRUE, xInt64.makeHandle(1)); // Certificate
                }
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
            }
        }

    /**
     * Native implementation of
     *     "conditional (String  algorithm,
     *                   Int     size,
     *                   Object  secretHandle
     *                   Object  publicHandle
     *                   Byte[]  publicBytes
     *                  )
     *         getKeyInfo(String name)"
     */
    private int invokeGetKeyInfo(Frame frame, KeyStoreHandle hStore, StringHandle hName,
                                 int[] aiReturn)
        {
        KeyStore keyStore = hStore.f_keyStore;
        String   sName    = hName.getStringValue();
        try
            {
            if (keyStore.isKeyEntry(sName))
                {
                Key key = keyStore.getKey(sName, hStore.f_achPwd);
                if (key == null)
                    {
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }

                String      sAlgorithm = key.getAlgorithm();
                PublicKey   publicKey  = null;
                byte[]      abPublic   = null;
                Certificate cert       = keyStore.getCertificate(sName);
                if (cert != null)
                    {
                    publicKey = cert.getPublicKey();
                    abPublic  = publicKey.getEncoded();
                    }

                int cKeyBits = key instanceof RSAPrivateKey privateKey
                                        ? privateKey.getModulus().bitLength()
                                        : key.getEncoded().length << 3;

                List<ObjectHandle> list = new ArrayList<>(9);
                list.add(xBoolean.TRUE);
                list.add(xString.makeHandle(sAlgorithm));
                list.add(xInt64.makeHandle(cKeyBits >>> 3));
                list.add(new SecretHandle(key));
                if (publicKey == null)
                    {
                    list.add(xNullable.NULL);
                    list.add(xArray.ensureEmptyByteArray());
                    }
                else
                    {
                    list.add(new SecretHandle(publicKey));
                    list.add(xArray.makeByteArrayHandle(abPublic, Mutability.Constant));
                    }
                return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));
                }

            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
            }
        }

    /**
     * Native implementation of "conditional String getPasswordInfo(String name)".
     */
    private int invokeGetPasswordInfo(Frame frame, KeyStoreHandle hStore, StringHandle hName,
                                      int[] aiReturn)
        {
        String sName = hName.getStringValue();
        try
            {
            Key key = hStore.f_keyStore.getKey(sName, hStore.f_achPwd);
            if (key instanceof PBEKey keyPwd)
                {
                return frame.assignValues(aiReturn,
                        xBoolean.TRUE, xString.makeHandle(keyPwd.getPassword()));
                }

            // unfortunately com.sun.crypto.provider.PBEKey is not public
            if (key.getClass().getSimpleName().equals("PBEKey"))
                {
                return frame.assignValues(aiReturn, xBoolean.TRUE,
                        xString.makeHandle(new String(key.getEncoded(), StandardCharsets.UTF_8)));
                }
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
            }
        }

    /**
     * Find a public/private key pair that could be used to encrypt tls communications.
     *
     * @return the alias for the first (in the order of iteration) PrivateKey; null if none is found
     */
    public static String findTlsKey(KeyStoreHandle hKeystore)
                throws GeneralSecurityException
        {
        KeyStore keystore = hKeystore.f_keyStore;
        for (Enumeration<String> it = keystore.aliases(); it.hasMoreElements();)
            {
            String sName = it.nextElement();
            if (keystore.isKeyEntry(sName) &&
                    keystore.getKey(sName, hKeystore.f_achPwd) instanceof PrivateKey &&
                    keystore.getCertificate(sName) != null)
                {
                return sName;
                }
            }
        return null;
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * Native handle holding the KeyStore data.
     */
    public static class KeyStoreHandle
                extends ServiceHandle
        {
        public KeyStoreHandle(TypeComposition clz, ServiceContext ctx, KeyStore keyStore, char[] achPwd,
                              X509KeyManager keyManager, X509TrustManager trustManager)
            {
            super(clz, ctx);

            f_keyStore     = keyStore;
            f_achPwd       = achPwd;
            f_keyManager   = keyManager;
            f_trustManager = trustManager;
            }

        /**
         * The wrapped {@link KeyStore}.
         */
        public final KeyStore f_keyStore;

        /**
         * The key store password.
         */
        private final char[] f_achPwd;

        /**
         * The underlying {@link KeyManager}.
         */
        public final X509KeyManager f_keyManager;

        /**
         * The underlying {@link TrustManager}.
         */
        public final X509TrustManager f_trustManager;
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }