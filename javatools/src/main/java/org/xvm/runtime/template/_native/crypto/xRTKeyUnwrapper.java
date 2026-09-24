package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.security.Key;

import javax.crypto.Cipher;

import javax.crypto.spec.SecretKeySpec;

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
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.crypto.xRTAlgorithms.KeyForm;
import org.xvm.runtime.template._native.crypto.xRTAlgorithms.SecretHandle;


/**
 * Native implementation of the RTKeyUnwrapper service.
 */
public class xRTKeyUnwrapper
        extends xService {
    public static xRTKeyUnwrapper INSTANCE;

    public xRTKeyUnwrapper(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("wrap",
                new String[] {STRING[0], OBJECT[0], STRING[0], OBJECT[0]}, BYTES);
        markNativeMethod("unwrap",
                new String[] {STRING[0], OBJECT[0], BYTES[0], STRING[0]}, null);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        if ("wrap".equals(method.getName())) {
            return invokeWrap(frame,
                    ((StringHandle) ahArg[0]).getStringValue(),
                    ahArg[1],
                    ((StringHandle) ahArg[2]).getStringValue(),
                    ahArg[3],
                    iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        if ("unwrap".equals(method.getName())) {
            return invokeUnwrap(frame,
                    ((StringHandle) ahArg[0]).getStringValue(),
                    ahArg[1],
                    (ArrayHandle) ahArg[2],
                    ((StringHandle) ahArg[3]).getStringValue(),
                    aiReturn);
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Native implementation of:
     * {@code Byte[] wrap(String algorithm, Object wrappingSecret, String keyAlgorithm,
     * Object keySecret)}.
     */
    private int invokeWrap(Frame frame, String sAlgorithm, ObjectHandle hWrappingKey,
                           String sKeyAlgorithm, ObjectHandle hKey, int iReturn) {
        try {
            validateAlgorithm(sAlgorithm);

            Key keyWrapping = xRTAlgorithms.extractKey(
                    frame, hWrappingKey, "AES", KeyForm.PrivateOrSecret);
            Key key = extractSecretKey(hKey, sKeyAlgorithm);

            Cipher cipher = Cipher.getInstance(sAlgorithm);
            cipher.init(Cipher.WRAP_MODE, keyWrapping);

            byte[] abWrapped = cipher.wrap(key);
            return frame.assignValue(iReturn,
                    xArray.makeByteArrayHandle(abWrapped, Mutability.Constant));
        } catch (GeneralSecurityException e) {
            return frame.raiseException(xException.illegalArgument(frame, e.getMessage()));
        }
    }

    /**
     * Native implementation of:
     * {@code conditional (Int keySize, Object keySecret) unwrap(String algorithm,
     * Object wrappingSecret, Byte[] wrapped, String keyAlgorithm)}.
     */
    private int invokeUnwrap(Frame frame, String sAlgorithm, ObjectHandle hWrappingKey,
                             ArrayHandle haWrapped, String sKeyAlgorithm, int[] aiReturn) {
        try {
            validateAlgorithm(sAlgorithm);

            Key keyWrapping = xRTAlgorithms.extractKey(
                    frame, hWrappingKey, "AES", KeyForm.PrivateOrSecret);

            Cipher cipher = Cipher.getInstance(sAlgorithm);
            cipher.init(Cipher.UNWRAP_MODE, keyWrapping);

            Key key = cipher.unwrap(
                    xByteArray.getBytes(haWrapped), sKeyAlgorithm, Cipher.SECRET_KEY);
            byte[] abKey = key.getEncoded();
            if (abKey == null) {
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

            return frame.assignValues(aiReturn, xBoolean.TRUE,
                    xInt64.makeHandle(abKey.length), new SecretHandle(key));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }
    }

    /**
     * Extract an opaque key, or represent visible bytes directly as a secret key. Key wrapping only
     * requires an encoded secret and must not depend on a SecretKeyFactory for the target algorithm.
     */
    private static Key extractSecretKey(ObjectHandle hKey, String sAlgorithm) {
        return hKey instanceof SecretHandle hSecret
                ? hSecret.f_key
                : new SecretKeySpec(xByteArray.getBytes((ArrayHandle) hKey), sAlgorithm);
    }

    /**
     * Verify that the bridge requested one of the registered key-wrapping transformations.
     */
    private static void validateAlgorithm(String sAlgorithm)
            throws GeneralSecurityException {
        if (!AES_KW_ALGORITHM.equals(sAlgorithm) && !AES_KWP_ALGORITHM.equals(sAlgorithm)) {
            throw new GeneralSecurityException(
                    "Unsupported key-wrapping algorithm: " + sAlgorithm);
        }
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final String AES_KW_ALGORITHM  = "AES/KW/NoPadding";
    private static final String AES_KWP_ALGORITHM = "AES/KWP/NoPadding";
}
