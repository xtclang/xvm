package org.xvm.runtime.template._native.crypto;


import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.Utils;
import org.xvm.runtime.template.numbers.xInt;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xService;

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
        markNativeMethod("getAlgorithmInfo", STRING, null);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "getAlgorithmInfo":
                return invokeGetAlgorithmInfo(frame, (StringHandle) ahArg[0], aiReturn);
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Native implementation of
     *     "(Int blockSize, Int formId, Int keySize, Object cipher) getAlgorithmInfo(String name)"
     */
    private int invokeGetAlgorithmInfo(Frame frame, StringHandle hName, int[] aiReturn)
        {
        String sName = hName.getStringValue();
        try
            {
            Cipher cipher = Cipher.getInstance(sName);

            int nBlockSize     = cipher.getBlockSize();
            int nFormId        = 0;
            int nKeySize       = Cipher.getMaxAllowedKeyLength(sName);

            List<ObjectHandle> list = new ArrayList<>(9);
            list.add(xInt.makeHandle(nBlockSize));
            list.add(xInt.makeHandle(nFormId));
            list.add(xInt.makeHandle(nKeySize));
            list.add(new CipherHandle(cipher));
            return frame.assignValues(aiReturn, list.toArray(Utils.OBJECTS_NONE));

            }
        catch (GeneralSecurityException e)
            {
            return frame.raiseException(e.getMessage());
            }
        }

    /**
     * Native handle holding a cipher.
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
    }