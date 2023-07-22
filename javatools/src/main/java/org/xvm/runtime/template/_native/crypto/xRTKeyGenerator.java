package org.xvm.runtime.template._native.crypto;


import javax.crypto.SecretKey;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt64;

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
                return invokeGenerateSecret(frame, (KeyGenHandle) ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Native implementation of
     *     "(Int keySize, Object secret) generateSecret(Object factory)"
     */
    private int invokeGenerateSecret(Frame frame, KeyGenHandle hKeyGen, int[] aiReturn)
        {
        SecretKey key   = hKeyGen.f_generator.generateKey();
        int       nSize = key.getEncoded().length;

        return frame.assignValues(aiReturn, xInt64.makeHandle(nSize), new SecretHandle(key));
        }
    }