package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template.text.xString.StringHandle;


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

                    nBlockSize = 1024; // how to compute this?
                    hImpl      = new SignatureHandle(sig);
                    break;
                    }

                default:
                    throw new IllegalArgumentException();
                }

            List<ObjectHandle> list = new ArrayList<>(9);
            list.add(xInt.makeHandle(nBlockSize));
            list.add(hImpl);
            return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));

            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(e.getMessage());
            }
        }

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
    }