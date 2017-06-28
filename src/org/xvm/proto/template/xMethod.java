package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.constants.MethodConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMethod
        extends ClassTemplate
    {
    public static xMethod INSTANCE;

    public xMethod(TypeSet types, ClassStructure structure, boolean fInstance)
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
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constMethod = (MethodConstant) constant;
            MethodStructure method = (MethodStructure) constMethod.getComponent();

            // TODO: assert if a function
            return new MethodHandle(f_clazzCanonical, method);
            }
        return null;
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        public MethodStructure m_method;

        protected MethodHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        protected MethodHandle(TypeComposition clazz, MethodStructure method)
            {
            super(clazz);

            m_method = method;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_method;
            }
        }

    }
