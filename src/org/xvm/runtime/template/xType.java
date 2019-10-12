package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.types.xMethod;


/**
 * TODO:
 */
public class xType
        extends ClassTemplate
    {
    public static xType INSTANCE;

    public xType(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("allMethods");
        markNativeProperty("explicitlyImmutable");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof TypeConstant)
            {
            ConstantPool pool = frame.poolContext();

            TypeConstant typeTarget = (TypeConstant) constant;
            assert typeTarget.isA(pool.typeType());

            TypeConstant typeData = typeTarget.getParamTypesArray()[0].
                    resolveGenerics(pool, frame.getGenericsResolver());
            frame.pushStack(typeData.getTypeHandle());
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TypeHandle hThis = (TypeHandle) hTarget;

        switch (sPropName)
            {
            case "allMethods":
                return frame.assignValue(iReturn, getAllMethods(hThis));
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    public xArray.GenericArrayHandle getAllMethods(TypeHandle hType)
        {
        MethodStructure[] aMethods = null; // TODO: use the type info when done
        xMethod.MethodHandle[] ahMethods = new xMethod.MethodHandle[aMethods.length];
        TypeConstant typeTarget = hType.getDataType();

        for (int i = 0, c = aMethods.length; i < c; i++)
            {
            ahMethods[i] = xMethod.makeHandle(aMethods[i], typeTarget);
            }
        return null; // TODO xArray.createArrayHandle(frame, xMethod.TYPE, ahMethods);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        TypeHandle hThis = (TypeHandle) hTarget;

        if (idProp instanceof FormalTypeChildConstant)
            {
            TypeConstant typeTarget = hThis.getDataType();
            String       sName      = idProp.getName();
            TypeConstant typeValue  = typeTarget.resolveGenericType(sName);

            return typeValue == null
                ? frame.raiseException("Unknown formal type: " + sName)
                : frame.assignValue(iReturn, typeValue.getTypeHandle());
            }

        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
        }

    public static TypeHandle makeHandle(TypeConstant type)
        {
        ConstantPool pool = ConstantPool.getCurrentPool();
        return new TypeHandle(INSTANCE.ensureParameterizedClass(pool, type));
        }

    // most of the time the TypeHandle is based on the underlying DataType (Type);
    // however, it if created dynamically, it could be based on a set of methods and properties
    public static class TypeHandle
            extends ObjectHandle
        {
        protected TypeHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public TypeConstant getDataType()
            {
            return getType().getParamType(0);
            }
        }
    }
