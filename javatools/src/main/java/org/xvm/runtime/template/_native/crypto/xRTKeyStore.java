package org.xvm.runtime.template._native.crypto;


import java.io.ByteArrayInputStream;
import java.io.InputStream;

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
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPublicKey;

import java.security.spec.ECParameterSpec;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

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
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

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

        markNativeMethod("isKey"             , STRING, null);
        markNativeMethod("getKeyInfo"        , STRING, null);
        markNativeMethod("getCertificateInfo", STRING, null);
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

            ServiceContext  context  = f_container.createServiceContext("KeyStore");
            TypeComposition clzStore = getCanonicalClass(f_container);
            ServiceHandle   hService = new KeyStoreHandle(clzStore, context, keyStore, achPwd,
                                            keyManager, trustManager);
            context.setService(hService);

            MethodStructure ctor = f_struct.findConstructor(); // default constructor
            assert ctor != null;

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
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "isKey":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeIsKey(frame, hStore, hName, aiReturn);
                }

            case "getKeyInfo":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeGetKeyInfo(frame, hStore, hName, aiReturn);
                }

            case "getCertificateInfo":
                {
                KeyStoreHandle hStore = (KeyStoreHandle) hTarget;
                StringHandle   hName  = (StringHandle) ahArg[0];

                return invokeGetCertificateInfo(frame, hStore, hName, aiReturn);
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
            list.add(xInt.makeHandle(nVersion));
            addDate(dateNotBefore, list);
            addDate(dateNotAfter, list);
            list.add(xArray.makeBooleanArrayHandle(abUsage, cUsage, Mutability.Constant));
            list.add(xString.makeHandle(sSigAlgName));
            list.add(xByteArray.makeByteArrayHandle(abSignature, Mutability.Constant));
            list.add(xString.makeHandle(sAlgorithm));
            list.add(xInt.makeHandle(cKeyBits >>> 3));
            list.add(xByteArray.makeByteArrayHandle(abPublic, Mutability.Constant));
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

        list.add(xInt.makeHandle(cal.get(Calendar.YEAR)));
        list.add(xInt.makeHandle(cal.get(Calendar.MONTH)));
        list.add(xInt.makeHandle(cal.get(Calendar.DAY_OF_WEEK)));
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
    private int invokeIsKey(Frame frame, KeyStoreHandle hStore, StringHandle hName,
                                         int[] aiReturn)
        {
        KeyStore keyStore = hStore.f_keyStore;
        String   sName    = hName.getStringValue();
        try
            {
            boolean fPublic  = keyStore.isCertificateEntry(sName);
            boolean fPrivate = keyStore.isKeyEntry(sName);

            if (fPrivate)
                {
                return frame.assignValues(aiReturn, xBoolean.TRUE, fPublic
                        ? xInt.makeHandle(0)   // Secret
                        : xInt.makeHandle(2)); // Pair
                }
            if (fPublic)
                {
                return frame.assignValues(aiReturn, xBoolean.TRUE, xInt.makeHandle(1)); // Public
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
     *                  Int     size,
     *                  Byte[]  bytes, // only for public key
     *                  Object  secret
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
                Key key = keyStore.getKey(hName.getStringValue(), hStore.f_achPwd);
                if (!(key instanceof RSAKey rsaKey && key instanceof PrivateKey privateKey))
                    {
                    return frame.raiseException(xException.makeHandle(frame,
                        "Unsupported private key type: " + key.getFormat()));
                    }

                String sAlgorithm = privateKey.getAlgorithm();
                int    cKeyBits   = rsaKey.getModulus().bitLength();

                List<ObjectHandle> list = new ArrayList<>(9);
                list.add(xBoolean.TRUE);
                list.add(xString.makeHandle(sAlgorithm));
                list.add(xInt.makeHandle(cKeyBits >>> 3));
                list.add(xArray.ensureEmptyByteArray());
                list.add(new Secret(privateKey));
                return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));
                }

            if (keyStore.isCertificateEntry(sName))
                {
                Certificate cert = hStore.f_keyStore.getCertificate(hName.getStringValue());

                PublicKey publicKey  = cert.getPublicKey();
                String    sAlgorithm = publicKey.getAlgorithm();
                int       cKeyBits   = getPublicKeyLength(publicKey);
                byte[]    abPublic   = publicKey.getEncoded();

                List<ObjectHandle> list = new ArrayList<>(9);
                list.add(xBoolean.TRUE);
                list.add(xString.makeHandle(sAlgorithm));
                list.add(xInt.makeHandle(cKeyBits >>> 3));
                list.add(xArray.makeByteArrayHandle(abPublic, Mutability.Constant));
                list.add(new Secret(publicKey));
                return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));
                }

            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeHandle(frame, e.getMessage()));
            }
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * Native handle holding the KeyStore data.
     */
    public static class KeyStoreHandle
                extends ServiceHandle
        {
        public KeyStoreHandle(TypeComposition clz, ServiceContext ctx, KeyStore keyStore,
                              char[] achPwd, KeyManagerFactory keyManager,
                              TrustManagerFactory trustManager)
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
         * The underlying {@link KeyManagerFactory}.
         */
        public final KeyManagerFactory f_keyManager;

        /**
         * The underlying {@link KeyManagerFactory}.
         */
        public final TrustManagerFactory f_trustManager;

        /**
         * The keystore password.
         */
        private final char[] f_achPwd;
        }

    /**
     * Native handle holding a key.
     */
    public static class Secret
            extends ObjectHandle
        {
        protected Secret(Key key)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_key      = key;
            m_fMutable = false;
            }

        /**
         * The wrapped {@link Key}.
         */
        public final Key f_key;
        }


    // ----- data fields and constants -------------------------------------------------------------

    /**
     * Cached canonical type.
     */
    private TypeConstant m_typeCanonical;
    }