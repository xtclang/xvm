package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;


/**
 * TODO:
 */
public class xClass
        extends ClassTemplate
    {
    public static xClass INSTANCE;

    public xClass(TypeSet types, ClassStructure structure, boolean fInstance)
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
        if (constant instanceof TypeConstant)
            {
            TypeConstant typeTarget = (TypeConstant) constant;
            TypeComposition clzTarget = f_types.resolveClass(
                typeTarget.getPosition(), frame.getGenericsResolver());

            TypeConstant typeClass = frame.f_context.f_pool.ensureParameterizedTypeConstant(
                INSTANCE.getTypeConstant(),
                clzTarget.ensurePublicType(),
                clzTarget.ensureProtectedType(),
                clzTarget.ensurePrivateType(),
                clzTarget.ensureStructType()
                );
            TypeComposition clzClass = f_types.resolveClass(typeClass);

            return new ClassHandle(clzClass, clzTarget);
            }
        return null;
        }

    public static class ClassHandle
            extends ObjectHandle
        {
        protected TypeComposition m_clzTarget;

        protected ClassHandle(TypeComposition clazz, TypeComposition clzTarget)
            {
            super(clazz);

            m_clzTarget = clzTarget;
            }

        @Override
        public String toString()
            {
            return super.toString() + m_clzTarget;
            }
        }
    }
