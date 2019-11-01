package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.util.Handy;


/**
 * Native Method implementation.
 */
public class xRTMethod
        extends xRTSignature
    {
    public static xRTMethod INSTANCE;

    public xRTMethod(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("access");

        // TODO

        super.initDeclared();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            frame.pushStack(makeHandle(frame.getThis().getType(), (MethodConstant) constant));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;

        switch (sPropName)
            {
            case "access":
                Constants.Access access  = hMethod.getMethodInfo().getAccess();
                ObjectHandle     hAccess = xRTType.INSTANCE.makeAccessHandle(frame, access);
                return frame.assignValue(iReturn, hAccess);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    public static MethodHandle makeHandle(TypeConstant typeTarget, MethodConstant idMethod)
        {
        ConstantPool pool = ConstantPool.getCurrentPool();

        SignatureConstant sig        = idMethod.getSignature();
        TypeConstant      typeRet    = pool.ensureParameterizedTypeConstant(pool.typeTuple(), sig.getRawReturns());
        TypeConstant      typeArg    = pool.ensureParameterizedTypeConstant(pool.typeTuple(), sig.getRawParams());
        TypeConstant      typeMethod = pool.ensureParameterizedTypeConstant(pool.typeMethod(), typeTarget, typeArg, typeRet);

        return new MethodHandle(typeMethod, idMethod);
        }

    /**
     * Method handle.
     *
     * Similarly to the {@link xRTFunction.FunctionHandle}, all Method handles are based on the same
     * canonical ClassComposition, but carry the actual type as a part of their state,
     */
    public static class MethodHandle
            extends SignatureHandle
        {
        protected MethodHandle(TypeConstant type, MethodConstant idMethod)
            {
            super(INSTANCE.getCanonicalClass(), idMethod, (MethodStructure) idMethod.getComponent(), type);
            }

        public TypeConstant getTargetType()
            {
            return getType().resolveGenericType("TargetType");
            }

        public TypeInfo getTargetInfo()
            {
            return getTargetType().ensureTypeInfo();
            }

        public MethodInfo getMethodInfo()
            {
            return getTargetInfo().getMethodById(f_idMethod);
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (obj instanceof MethodHandle)
                {
                MethodHandle that = (MethodHandle) obj;
                return Handy.equals(this.f_idMethod, that.f_idMethod)
                    && Handy.equals(this.f_type    , that.f_type    );
                }

            return false;
            }

        @Override
        public String toString()
            {
            return "Method: " + getMethod();
            }
        }
    }
