package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;

import javax.crypto.spec.GCMParameterSpec;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyForm;


/**
 * Native implementation of the RTAuthenticatedDecryptor service.
 */
public class xRTAuthenticatedDecryptor
        extends xService {
    public static xRTAuthenticatedDecryptor INSTANCE;

    public xRTAuthenticatedDecryptor(Container container, ClassStructure structure,
                                     boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("seal",
                new String[] {STRING[0], OBJECT[0], BYTES[0], BYTES[0]}, null);
        markNativeMethod("open",
                new String[] {STRING[0], OBJECT[0], BYTES[0], BYTES[0], BYTES[0]}, null);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "seal":
            return invokeSeal(frame, ((StringHandle) ahArg[0]).getStringValue(), ahArg[1],
                    (ArrayHandle) ahArg[2], (ArrayHandle) ahArg[3], aiReturn);

        case "open":
            return invokeOpen(frame, ((StringHandle) ahArg[0]).getStringValue(), ahArg[1],
                    (ArrayHandle) ahArg[2], (ArrayHandle) ahArg[3],
                    (ArrayHandle) ahArg[4], aiReturn);
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Native implementation of:
     * {@code (Byte[] nonce, Byte[] ciphertext) seal(String algorithm, Object secret,
     * Byte[] data, Byte[] associatedData)}.
     */
    private int invokeSeal(Frame frame, String sAlgorithm, ObjectHandle hKey,
                           ArrayHandle haData, ArrayHandle haAssociatedData, int[] aiReturn) {
        byte[] abData           = xByteArray.getBytes(haData);
        byte[] abAssociatedData = xByteArray.getBytes(haAssociatedData);
        byte[] abNonce          = new byte[NONCE_SIZE];
        s_random.nextBytes(abNonce);

        try {
            String sKeyAlgorithm = validateAlgorithm(sAlgorithm);
            Key    key           = xRTAlgorithms.extractKey(frame, hKey, sKeyAlgorithm,
                    KeyForm.PrivateOrSecret);

            Cipher cipher = Cipher.getInstance(sAlgorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE_BITS, abNonce));
            cipher.updateAAD(abAssociatedData);

            byte[] abCiphertext = cipher.doFinal(abData);
            return frame.assignValues(aiReturn,
                    xArray.makeByteArrayHandle(abNonce, Mutability.Constant),
                    xArray.makeByteArrayHandle(abCiphertext, Mutability.Constant));
        } catch (GeneralSecurityException e) {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
        }
    }

    /**
     * Native implementation of:
     * {@code conditional Byte[] open(String algorithm, Object secret, Byte[] nonce,
     * Byte[] ciphertext, Byte[] associatedData)}.
     */
    private int invokeOpen(Frame frame, String sAlgorithm, ObjectHandle hKey,
                           ArrayHandle haNonce, ArrayHandle haCiphertext,
                           ArrayHandle haAssociatedData, int[] aiReturn) {
        byte[] abNonce          = xByteArray.getBytes(haNonce);
        byte[] abCiphertext     = xByteArray.getBytes(haCiphertext);
        byte[] abAssociatedData = xByteArray.getBytes(haAssociatedData);

        if (abNonce.length != NONCE_SIZE || abCiphertext.length < TAG_SIZE_BYTES) {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

        try {
            String sKeyAlgorithm = validateAlgorithm(sAlgorithm);
            Key    key           = xRTAlgorithms.extractKey(frame, hKey, sKeyAlgorithm,
                    KeyForm.PrivateOrSecret);

            Cipher cipher = Cipher.getInstance(sAlgorithm);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE_BITS, abNonce));
            cipher.updateAAD(abAssociatedData);

            byte[] abData = cipher.doFinal(abCiphertext);
            return frame.assignValues(aiReturn, xBoolean.TRUE,
                    xArray.makeByteArrayHandle(abData, Mutability.Constant));
        } catch (AEADBadTagException e) {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        } catch (GeneralSecurityException e) {
            return frame.raiseException(xException.makeObscure(frame, e.getMessage()));
        }
    }

    /**
     * Validate the selected authenticated-encryption transformation and obtain its key algorithm.
     */
    private static String validateAlgorithm(String sAlgorithm)
            throws GeneralSecurityException {
        if (!AES_GCM_ALGORITHM.equals(sAlgorithm)) {
            throw new GeneralSecurityException(
                    "Unsupported authenticated-encryption algorithm: " + sAlgorithm);
        }
        return "AES";
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int    NONCE_SIZE        = 12;
    private static final int    TAG_SIZE_BITS     = 128;
    private static final int    TAG_SIZE_BYTES    = TAG_SIZE_BITS / 8;

    private static final SecureRandom s_random = new SecureRandom();
}
