package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xProperty
        extends ClassTemplate
    {
    public xProperty(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
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
        if (constant instanceof PropertyConstant)
            {
            frame.pushStack(new DeferredPropertyHandle((PropertyConstant) constant));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    public static class DeferredPropertyHandle
            extends DeferredCallHandle
        {
        protected final PropertyConstant f_property;

        protected DeferredPropertyHandle(PropertyConstant prop)
            {
            super(null);

            f_property = prop;
            }

        public String getProperty()
            {
            return f_property.getName();

            }
        @Override
        public String toString()
            {
            return "Deferred property access: " + f_property.getName();
            }
        }
    }
