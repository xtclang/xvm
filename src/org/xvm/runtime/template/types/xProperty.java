package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
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
            return new DeferredPropertyHandle((PropertyConstant) constant);
            }
        return null;
        }

    public static class DeferredPropertyHandle
            extends DeferredCallHandle
        {
        public PropertyConstant m_property;

        protected DeferredPropertyHandle(PropertyConstant prop)
            {
            super(null);

            m_property = prop;
            }

        @Override
        public String toString()
            {
            return "Deferred property access: " + m_property.getName();
            }
        }
    }
