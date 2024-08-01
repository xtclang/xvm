package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;

import java.security.spec.X509EncodedKeySpec;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;

import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;


/**
 * Native implementation of the xRTAlgorithms.x service.
 */
public class xRTAlgorithms
        extends xService
    {
    public static xRTAlgorithms INSTANCE;

    public xRTAlgorithms(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("getAlgorithmInfo", null, null);

        invalidateTypeInfo();
        }

    /**
     * Injection support method.
     */
    public ObjectHandle ensureAlgorithms(Frame frame, ObjectHandle hOpts)
        {
        ObjectHandle hAlgorithms = m_hAlgorithms;
        if (hAlgorithms == null)
            {
            m_hAlgorithms = hAlgorithms = instantiateAlgorithms(frame, hOpts);
            }

        return hAlgorithms;
        }

    protected ObjectHandle instantiateAlgorithms(Frame frame, ObjectHandle hOpts)
        {
        TypeComposition clz     = getCanonicalClass();
        MethodStructure ctor    = getStructure().findConstructor();
        ServiceContext  context = f_container.createServiceContext(f_sName);

        switch (context.sendConstructRequest(frame, clz, ctor, null,
                    new ObjectHandle[ctor.getMaxVars()], Op.A_STACK))
            {
            case Op.R_NEXT:
                return invokeCreateAlgorithms(frame);

            case Op.R_CALL:
                {
                Frame frameNext = frame.m_frameNext;
                frameNext.addContinuation(frameCaller ->
                        frameCaller.pushStack(invokeCreateAlgorithms(frameCaller)));
                return new DeferredCallHandle(frameNext);
                }

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    private ObjectHandle invokeCreateAlgorithms(Frame frame)
        {
        TypeComposition clz     = getCanonicalClass();
        MethodStructure method = getStructure().findMethod("createAlgorithms", 0);
        CallChain       chain  = clz.getMethodCallChain(method.getIdentityConstant().getSignature());

        switch (chain.invoke(frame, frame.popStack(), Op.A_STACK))
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                return new DeferredCallHandle(frame.m_frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getAlgorithmInfo":
                return invokeGetAlgorithmInfo(frame, (StringHandle) ahArg[0], (EnumHandle) ahArg[1],
                        aiReturn);
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Native implementation of
     *     "(Int blockSize, Object impl) getAlgorithmInfo(String name, AlgorithmMethod method)"
     */
    private int invokeGetAlgorithmInfo(Frame frame, StringHandle hName, EnumHandle hMethod,
                                       int[] aiReturn)
        {
        String sName = hName.getStringValue();
        try
            {
            int          nBlockSize;
            ObjectHandle hImpl;

            switch (hMethod.getOrdinal())
                {
                case 0: // Hasher
                    {
                    MessageDigest digest = MessageDigest.getInstance(sName);

                    nBlockSize = 0;
                    hImpl      = new DigestHandle(digest);
                    break;
                    }

                case 1: // SymmetricalCipher
                case 2: // AsymmetricalCipher
                    {
                    Cipher cipher = Cipher.getInstance(sName);

                    nBlockSize = cipher.getBlockSize();
                    hImpl      = new CipherHandle(cipher);
                    break;
                    }

                case 3: // Signature
                    {
                    Signature sig = Signature.getInstance(sName);

                    nBlockSize = 0;
                    hImpl      = new SignatureHandle(sig);
                    break;
                    }

                case 4: // KeyGenerator
                    {
                    KeyGenerator generator = KeyGenerator.getInstance(sName);

                    nBlockSize = 0;
                    hImpl      = new KeyGenHandle(generator);
                    break;
                    }

                default:
                    throw new IllegalArgumentException();
                }

            List<ObjectHandle> list = new ArrayList<>(9);
            list.add(xInt64.makeHandle(nBlockSize));
            list.add(hImpl);
            return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));

            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
            }
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Extract a key from the specified handle.
     */
    public static Key extractKey(Frame frame, ObjectHandle hKey, String sAlgorithm, KeyForm keyForm)
            throws GeneralSecurityException
        {
        if (hKey instanceof SecretHandle hSecret)
            {
            return hSecret.f_key;
            }
        else
            {
            ByteArrayHandle hBytes = (ByteArrayHandle) ((ArrayHandle) hKey).m_hDelegate;
            byte[]          abRaw  = xRTUInt8Delegate.getBytes(hBytes);

            switch (sAlgorithm)
                {
                case "DES":
                    return switch (keyForm)
                        {
                        case PublicOrSecret, PrivateOrSecret ->
                            SecretKeyFactory.getInstance(sAlgorithm).
                                generateSecret(new DESKeySpec(abRaw));

                        default ->
                            throw new GeneralSecurityException(
                                sAlgorithm + " algorithm only supports secret keys");
                        };

                case "DESede":
                    return switch (keyForm)
                        {
                        case PublicOrSecret, PrivateOrSecret ->
                            SecretKeyFactory.getInstance(sAlgorithm).
                                generateSecret(new DESedeKeySpec(abRaw));

                        default ->
                            throw new GeneralSecurityException(
                                sAlgorithm + " algorithm only supports secret keys");
                        };

                case "RSA":
                    return switch (keyForm)
                        {
                        case Public, PublicOrSecret ->
                            KeyFactory.getInstance("RSA").
                                generatePublic(new X509EncodedKeySpec(abRaw));

                        case Private, PrivateOrSecret ->
                            KeyFactory.getInstance("RSA").
                                generatePrivate(new X509EncodedKeySpec(abRaw));
                        };

                default:
                    throw new GeneralSecurityException("Cannot make a raw key for " +  sAlgorithm);
                }
            }
        }

    public enum KeyForm {Public, Private, PublicOrSecret, PrivateOrSecret}


    // ----- handles -------------------------------------------------------------------------------

    /**
     * Native handle holding a MessageDigest.
     */
    public static class DigestHandle
            extends ObjectHandle
        {
        protected DigestHandle(MessageDigest digest)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_digest = digest;
            }

        /**
         * The wrapped {@link MessageDigest}.
         */
        public final MessageDigest f_digest;
        }

    /**
     * Native handle holding a Cipher.
     */
    public static class CipherHandle
            extends ObjectHandle
        {
        protected CipherHandle(Cipher cipher)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_cipher = cipher;
            }

        /**
         * The wrapped {@link Cipher}.
         */
        public final Cipher f_cipher;
        }

    /**
     * Native handle holding a Signature.
     */
    public static class SignatureHandle
            extends ObjectHandle
        {
        protected SignatureHandle(Signature signature)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_signature = signature;
            }

        /**
         * The wrapped {@link Signature}.
         */
        public final Signature f_signature;
        }

    /**
     * Native handle holding a key.
     */
    public static class SecretHandle
            extends ObjectHandle
        {
        protected SecretHandle(Key key)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_key = key;
            }

        /**
         * The wrapped {@link Key}.
         */
        public final Key f_key;
        }

    /**
     * Native handle holding a KeyGenerator.
     */
    public static class KeyGenHandle
            extends ObjectHandle
        {
        /**
         * The wrapped {@link KeyGenerator}.
         */
        public final KeyGenerator f_generator;

        protected KeyGenHandle(KeyGenerator generator)
            {
            super(xObject.INSTANCE.getCanonicalClass());

            f_generator = generator;
            }
        }

    /**
     * Cached Algorithms handle.
     */
    private ObjectHandle m_hAlgorithms;
    }