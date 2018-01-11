package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xProperty
        extends ClassTemplate
    {
    public static xProperty INSTANCE;

    public xProperty(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof PropertyConstant)
            {
            PropertyStructure prop = (PropertyStructure) ((PropertyConstant) constant).getComponent();
            TypeComposition clz = ensureParameterizedClass(
                prop.getType(), prop.getParent().getIdentityConstant().asTypeConstant());

            return new PropertyHandle(clz, prop);
            }
        return null;
        }

    public static class PropertyHandle extends ObjectHandle
        {
        public PropertyStructure m_property;

        protected PropertyHandle(TypeComposition clazz, PropertyStructure prop)
            {
            super(clazz);
            m_property = prop;
            }
        }
    }
