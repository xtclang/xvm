package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

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
    public void initDeclared()
        {
        markNativeGetter("allMethods");
        markNativeGetter("explicitlyImmutable");
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        TypeHandle hThis = (TypeHandle) hTarget;

        switch (property.getName())
            {
            case "allMethods":
                return frame.assignValue(iReturn, getAllMethods(hThis));
            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    public xArray.GenericArrayHandle getAllMethods(TypeHandle hType)
        {
        MethodStructure[] aMethods = null;
        xMethod.MethodHandle[] ahMethods = new xMethod.MethodHandle[aMethods.length];
        TypeConstant typeTarget = hType.getDataType();

        for (int i = 0, c = aMethods.length; i < c; i++)
            {
            ahMethods[i] = xMethod.makeHandle(aMethods[i], typeTarget);
            }
        return xArray.makeHandle(xMethod.TYPE, ahMethods);
        }

    public static TypeHandle makeHandle(TypeConstant type)
        {
        return new TypeHandle(INSTANCE.f_clazzCanonical, type);
        }

    // most of the time the TypeHandle is based on the underlying DataType (Type);
    // however, it if created dynamically, it could be based on a set of methods and properties
    public static class TypeHandle
            extends ObjectHandle
        {
        protected TypeConstant m_type;

        protected TypeHandle(TypeComposition clazz, TypeConstant type)
            {
            super(clazz);

            m_type = type;
            }

        protected TypeConstant getDataType()
            {
            return m_type;
            }
        }
    }
