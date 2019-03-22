package org.xvm.runtime.template.types;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

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
            PropertyConstant  idProp = (PropertyConstant) constant;
            PropertyStructure prop   = (PropertyStructure) idProp.getComponent();
            ObjectHandle      hValue;
            if (prop.isStatic())
                {
                Constant constVal = prop.getInitialValue();
                if (constVal == null)
                    {
                    // there must be an initializer
                    MultiMethodStructure mmInit     = (MultiMethodStructure) prop.getChild("=");
                    MethodStructure      methodInit = mmInit.methods().iterator().next();
                    frame.call1(methodInit, null, new ObjectHandle[methodInit.getMaxVars()], Op.A_STACK);
                    hValue = new DeferredCallHandle(frame.m_frameNext);
                    }
                else
                    {
                    hValue = frame.getConstHandle(constVal);
                    }
                }
            else
                {
                hValue = new DeferredPropertyHandle(idProp);
                }

            frame.pushStack(hValue);
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

        public PropertyConstant getProperty()
            {
            return f_property;
            }

        public PropertyConstant getPropertyId()
            {
            return f_property;
            }

        @Override
        public String toString()
            {
            return "Deferred property access: " + f_property.getName();
            }
        }
    }
