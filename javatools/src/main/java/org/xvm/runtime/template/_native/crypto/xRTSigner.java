package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.PrivateKey;

import javax.crypto.Cipher;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.CipherHandle;
import org.xvm.runtime.template._native.crypto.xRTKeyStore.SecretHandle;


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
        markNativeMethod("sign", null, BYTES);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "sign":
                return invokeSign(frame, (CipherHandle) ahArg[0], ahArg[1],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of
     *     "Byte[] sign(Object cipher, CryptoKey privateKey, Byte[] data)"
     */
    private int invokeSign(Frame frame, CipherHandle hCipher, ObjectHandle hKey,
                           ByteArrayHandle haData, int iReturn)
        {
        Cipher cipher = hCipher.f_cipher;

        PrivateKey privateKey;
        if (hKey.getComposition().getInceptionType().toString().contains("RTPrivateKey"))
            {
            GenericHandle hRTPrivateKey = (GenericHandle) hKey.revealOrigin();
            PropertyStructure prop = hRTPrivateKey.getComposition().getTemplate().getStructure().findPropertyDeep("secret");

            SecretHandle hSecret = (SecretHandle) hRTPrivateKey.getField(frame, prop.getIdentityConstant());
            privateKey = (PrivateKey) hSecret.f_key;
            }
        else
            {
            ByteArrayHandle hBytes = (ByteArrayHandle) ((ArrayHandle) hKey).m_hDelegate;
            byte[] abPrivate = xRTUInt8Delegate.getBytes(hBytes);
            // make the private key
            throw new UnsupportedOperationException();
            }

        byte[] abData = xRTUInt8Delegate.getBytes(haData);

        try
            {
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            byte[] abSig = cipher.doFinal(abData);

            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abSig, Mutability.Constant));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(e.getMessage());
            }
        }
    }