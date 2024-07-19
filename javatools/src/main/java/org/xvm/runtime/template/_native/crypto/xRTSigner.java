package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyForm;
import org.xvm.runtime.template._native.crypto.xRTAlgorithms.SignatureHandle;


/**
 * Native implementation of the xRTSigner.x service.
 */
public class xRTSigner
        extends xService
    {
    public static xRTSigner INSTANCE;

    public xRTSigner(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("sign"  , new String[] {OBJECT[0], OBJECT[0], BYTES[0]}, BYTES);
        markNativeMethod("verify", new String[] {OBJECT[0], OBJECT[0], BYTES[0], BYTES[0]}, BOOLEAN);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "sign":
                return invokeSign(frame, (SignatureHandle) ahArg[0], ahArg[1],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate, iReturn);

            case "verify":
                return invokeVerify(frame, (SignatureHandle) ahArg[0], ahArg[1],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate,
                    (ByteArrayHandle) ((ArrayHandle) ahArg[3]).m_hDelegate, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of
     *     "Byte[] sign(Object cipher, Object secret, Byte[] data)".
     */
    private int invokeSign(Frame frame, SignatureHandle hSignature, ObjectHandle hKey,
                           ByteArrayHandle haData, int iReturn)
        {
        Signature signature = hSignature.f_signature;
        byte[]    abData     = xRTUInt8Delegate.getBytes(haData);

        try
            {
            PrivateKey privateKey = (PrivateKey) xRTAlgorithms.extractKey(frame, hKey,
                                    signature.getAlgorithm(), KeyForm.Private);
            signature.initSign(privateKey);
            signature.update(abData);
            byte[] abSig = signature.sign();

            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abSig, Mutability.Constant));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
            }
        }

    /**
     * Native implementation of
     *     "Boolean verify(Object signer, Object secret, Byte[] signature, Byte[] data)".
     */
    private int invokeVerify(Frame frame, SignatureHandle hSignature, ObjectHandle hKey,
                           ByteArrayHandle haSignature, ByteArrayHandle haData, int iReturn)
        {
        Signature signature = hSignature.f_signature;

        byte[] abSig  = xRTUInt8Delegate.getBytes(haSignature);
        byte[] abData = xRTUInt8Delegate.getBytes(haData);

        try
            {
            PublicKey publicKey = (PublicKey) xRTAlgorithms.extractKey(frame, hKey,
                                    signature.getAlgorithm(), KeyForm.Public);
            signature.initVerify(publicKey);
            signature.update(abData);

            return frame.assignValue(iReturn,
                    xBoolean.makeHandle(signature.verify(abSig)));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
            }
        }
    }