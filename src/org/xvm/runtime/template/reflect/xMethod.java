package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.TypeInfo;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template._native.reflect.xRTType;


/**
 * TODO:
 */
public class xMethod
        extends ClassTemplate
    {
    public static xMethod INSTANCE;
    public static TypeConstant TYPE;

    public static xEnum ACCESS;

    public xMethod(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            TYPE = getCanonicalType();
            }
        }

    @Override
    public void initDeclared()
        {
        ACCESS = (xEnum) getChildTemplate("Access");

        markNativeProperty("name");
        markNativeProperty("conditionalReturn");
        markNativeProperty("access");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
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
            case "name":
                return frame.assignValue(iReturn, xString.makeHandle(hMethod.f_idMethod.getName()));

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

        SignatureConstant sig       = idMethod.getSignature();
        TypeConstant      typeRet   = pool.ensureParameterizedTypeConstant(pool.typeTuple(), sig.getRawReturns());
        TypeConstant      typeArg   = pool.ensureParameterizedTypeConstant(pool.typeTuple(), sig.getRawParams());
        ClassComposition  clzMethod = INSTANCE.ensureParameterizedClass(pool, typeTarget, typeArg, typeRet);

        return new MethodHandle(clzMethod, idMethod);
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public final MethodConstant f_idMethod;

        protected MethodHandle(TypeComposition clazz, MethodConstant idMethod)
            {
            super(clazz);

            f_idMethod = idMethod;
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
        public String toString()
            {
            return super.toString() + f_idMethod;
            }
        }
    }
