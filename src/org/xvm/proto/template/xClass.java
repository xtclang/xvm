package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ClassTypeConstant;

import org.xvm.proto.*;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
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
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        if (constant instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constant;
            TypeComposition clzTarget = f_types.resolve(constClass);

            ClassTemplate template = clzTarget.f_template;
            if (template.isSingleton())
                {
                return template.createConstHandle(constant, heap);
                }

            Map<String, Type> mapParams = new HashMap<>();
            mapParams.put("PublicType", clzTarget.ensurePublicType());
            mapParams.put("ProtectedType", clzTarget.ensureProtectedType());
            mapParams.put("PrivateType", clzTarget.ensurePrivateType());
            mapParams.put("StructType", clzTarget.ensureStructType());

            TypeComposition clzClass = ensureClass(mapParams);
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
