package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.proto.*;

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
        // TODO
        }

    public static TypeHandle makeHandle(Type type)
        {
        return new TypeHandle(
                INSTANCE.ensureClass(Collections.singletonMap("DataType", type)),
                type);
        }

    public static class TypeHandle
            extends ObjectHandle
        {
        protected TypeHandle(TypeComposition clazz, Type type)
            {
            super(clazz);
            }

        protected Type getType()
            {
            return f_clazz.getActualType("DataType");
            }
        }
    }
