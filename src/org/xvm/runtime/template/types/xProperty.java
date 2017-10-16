package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;


/**
 * TODO:
 */
public class xProperty
        extends ClassTemplate
    {
    public static xProperty INSTANCE;

    public xProperty(TypeSet types, ClassStructure structure, boolean fInstance)
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
        }

    @Override
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof PropertyConstant)
            {
            return new PropertyHandle(f_clazzCanonical, (PropertyConstant) constant);
            }
        return null;
        }

    public static class PropertyHandle extends ObjectHandle
        {
        public PropertyConstant m_constProperty;

        protected PropertyHandle(TypeComposition clazz, PropertyConstant constProperty)
            {
            super(clazz);
            m_constProperty = constProperty;
            }
        }
    }
