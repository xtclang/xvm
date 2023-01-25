package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import java.util.Random;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

import javax.crypto.spec.SecretKeySpec;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyGenHandle;
import org.xvm.runtime.template._native.crypto.xRTAlgorithms.SecretHandle;


/**
 * Native implementation of the xRTSigner.x service.
 */
public class xRTKeyGenerator
        extends xService
    {
    public static xRTKeyGenerator INSTANCE;

    public xRTKeyGenerator(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("generateSecret", null, null);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "generateSecret":
                return invokeGenerateSecret(frame, (KeyGenHandle) ahArg[0],  (JavaLong) ahArg[1],
                        aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Native implementation of
     *     "(Int keySize, Object secret) generateSecret(Object factory, Int seedSize)"
     */
    private int invokeGenerateSecret(Frame frame, KeyGenHandle hKeyGen, JavaLong hSeedSize,
                                     int[] aiReturn)
        {
        SecretKeyFactory factory = hKeyGen.f_factory;

        try
            {
            byte[] abSeed = new byte[(int) hSeedSize.getValue()];
            RANDOM.nextBytes(abSeed);

            SecretKey key   = factory.generateSecret(new SecretKeySpec(abSeed, factory.getAlgorithm()));
            int       nSize = key.getEncoded().length;

            return frame.assignValues(aiReturn,
                    xInt.makeHandle(nSize),
                    new SecretHandle(key));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(e.getMessage());
            }
        }

    private static final Random RANDOM = new SecureRandom();
    }