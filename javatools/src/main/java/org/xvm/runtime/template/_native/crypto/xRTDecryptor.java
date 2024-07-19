package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.Key;

import javax.crypto.Cipher;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.CipherHandle;
import org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyForm;


/**
 * Native implementation of the xRTDecryptor.x service.
 */
public class xRTDecryptor
        extends xService
    {
    public static xRTDecryptor INSTANCE;

    public xRTDecryptor(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("encrypt", new String[] {OBJECT[0], OBJECT[0], BYTES[0]}, BYTES);
        markNativeMethod("decrypt", new String[] {OBJECT[0], OBJECT[0], BYTES[0]}, BYTES);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "encrypt":
                return invokeEncrypt(frame, (CipherHandle) ahArg[0], ahArg[1],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate, iReturn);

            case "decrypt":
                return invokeDecrypt(frame, (CipherHandle) ahArg[0], ahArg[1],
                    (ByteArrayHandle) ((ArrayHandle) ahArg[2]).m_hDelegate, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of
     *     "Byte[] encrypt(Object cipher, Object secret, Byte[] data)"
     */
    private int invokeEncrypt(Frame frame, CipherHandle hCipher, ObjectHandle hKey,
                              ByteArrayHandle haData, int iReturn)
        {
        Cipher cipher = hCipher.f_cipher;
        byte[] abData = xRTUInt8Delegate.getBytes(haData);

        try
            {
            Key key = xRTAlgorithms.extractKey(frame, hKey, cipher.getAlgorithm(),
                            KeyForm.PublicOrSecret);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] abEncoded = cipher.doFinal(abData);

            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abEncoded, Mutability.Constant));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
            }
        }

    /**
     * Native implementation of
     *     "Byte[] decrypt(Object cipher, Object secret, Byte[] bytes)"
     */
    private int invokeDecrypt(Frame frame, CipherHandle hCipher, ObjectHandle hKey,
                              ByteArrayHandle haData, int iReturn)
        {
        byte[] abData  = xRTUInt8Delegate.getBytes(haData);
        Cipher cipher = hCipher.f_cipher;

        try
            {
            Key key = xRTAlgorithms.extractKey(frame, hKey, cipher.getAlgorithm(),
                        KeyForm.PrivateOrSecret);

            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] abSig = cipher.doFinal(abData);

            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abSig, Mutability.Constant));
            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
            }
        }
    }