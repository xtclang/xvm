package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.*;
import org.xvm.proto.template.collections.xArray;

import java.util.Collections;

/**
 * TODO:
 *
 * @author gg 2017.02.27
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
