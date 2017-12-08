package org.xvm.runtime.template;


import java.util.Collections;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.collections.xArray;


/**
 * TODO:
 */
public class xType
        extends ClassTemplate
    {
    public static xType INSTANCE;

    public xType(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

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
                Type type = hThis.getType();
                xArray.GenericArrayHandle methods = type.getAllMethods();
                return frame.assignValue(iReturn, methods);
            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    public static TypeHandle makeHandle(Type type)
        {
        return new TypeHandle(INSTANCE.ensureClass(Collections.singletonMap("DataType", type)));
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

        protected Type getType()
            {
            return f_clazz.getActualType("DataType");
            }
        }
    }
