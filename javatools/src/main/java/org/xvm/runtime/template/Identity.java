package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native Identity implementation.
 */
public class Identity
        extends ClassTemplate
    {
    public static Identity INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public Identity(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                    (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("equals",   null, BOOLEAN);
        markNativeMethod("hashCode", null, INT);

        invalidateTypeInfo();
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return INCEPTION_CLASS;
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "equals":
                {
                IdentityHandle hId1 = (IdentityHandle) ahArg[1];
                IdentityHandle hId2 = (IdentityHandle) ahArg[2];
                return frame.assignValue(iReturn, xBoolean.makeHandle(hId1.m_hValue == hId2.m_hValue));
                }

            case "hashCode":
                {
                IdentityHandle hId = (IdentityHandle) ahArg[1];
                return frame.assignValue(iReturn, xInt64.makeHandle(hId.hashCode()));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IdentityHandle hId1 = (IdentityHandle) hValue1;
        IdentityHandle hId2 = (IdentityHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(hId1.m_hValue == hId2.m_hValue));
        }

    /**
     * Create an identity handle for a mutable or non-hashable object.
     */
    public static IdentityHandle ensureIdentity(ObjectHandle h)
        {
        return new IdentityHandle(INSTANCE.getCanonicalClass(), h);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * An identity handle for non-hashable or mutable values.
     */
    public static class IdentityHandle
            extends ObjectHandle
        {
        protected IdentityHandle(ClassComposition clzIdentity, ObjectHandle hValue)
            {
            super(clzIdentity);

            m_hValue = hValue;
            }

        @Override
        public int hashCode()
            {
            return System.identityHashCode(m_hValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof IdentityHandle that && this.m_hValue == that.m_hValue;
            }

        private final ObjectHandle m_hValue;
        }
    }