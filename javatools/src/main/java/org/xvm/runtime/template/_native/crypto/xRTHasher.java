package org.xvm.runtime.template._native.crypto;


import java.security.MessageDigest;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.DigestHandle;


/**
 * Native implementation of the xRTHasher.x service.
 */
public class xRTHasher
        extends xService
    {
    public static xRTHasher INSTANCE;

    public xRTHasher(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("digest", null, BYTES);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "digest":
                return invokeDigest(frame, (DigestHandle) ahArg[0],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[1]).m_hDelegate, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of "Byte[] digest(Object digest, Byte[] data)".
     */
    private int invokeDigest(Frame frame, DigestHandle hDigest, ByteArrayHandle haData, int iReturn)
        {
        MessageDigest digest = hDigest.f_digest;

        byte[] abData = xRTUInt8Delegate.getBytes(haData);
        byte[] abSig = digest.digest(abData);

        return frame.assignValue(iReturn,
                xArray.makeByteArrayHandle(abSig, Mutability.Constant));
        }
    }