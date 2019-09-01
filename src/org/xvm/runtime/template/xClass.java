package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xClass
        extends ClassTemplate
    {
    public static xClass INSTANCE;

    public xClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant)
            {
            ConstantPool   pool   = frame.poolContext();
            ClassConstant  idClz  = (ClassConstant) constant;
            ClassStructure struct = (ClassStructure) idClz.getComponent();

            if (struct.getFormat() == Format.ENUM)
                {
                ClassTemplate templateEnum = f_templates.getTemplate(idClz);
                // TODO: route to the native xEnumeration.java
                }

            TypeConstant typePublic    = idClz.getType();
            TypeConstant typeProtected = pool.ensureAccessTypeConstant(typePublic, Access.PROTECTED);
            TypeConstant typePrivate   = pool.ensureAccessTypeConstant(typePublic, Access.PRIVATE);
            TypeConstant typeStruct    = pool.ensureAccessTypeConstant(typePublic, Access.STRUCT);

            ClassComposition clz = ensureParameterizedClass(pool,
                typePublic, typeProtected, typePrivate, typeStruct);

            frame.pushStack(new ClassHandle(clz));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        String sProp = idProp.getName();

        switch (sProp)
            {
            }

        return frame.raiseException("Not implemented property: "  + sProp);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName,
                               ObjectHandle hTarget, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hTarget;

        switch (sPropName)
            {
            case "hash":
                return frame.assignValue(iReturn, xInt64.makeHandle(hThis.getType().hashCode()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(hThis == hThat));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ClassHandle hThis = (ClassHandle) hValue1;
        ClassHandle hThat = (ClassHandle) hValue2;

        return frame.assignValue(iReturn, xInt64.makeHandle(hThis.getType().compareTo(hThat.getType())));
        }


    // ----- ObjectHandle -----

    public static class ClassHandle
            extends ObjectHandle
        {
        protected ClassHandle(TypeComposition clzTarget)
            {
            super(clzTarget);
            }

        public TypeConstant getPublicType()
            {
            return getType().getParamTypesArray()[0];
            }

        @Override
        public String toString()
            {
            return super.toString();
            }
        }

    }
