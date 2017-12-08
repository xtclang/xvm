package org.xvm.runtime.template;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.ParameterizedTypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
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
        if (constant instanceof ParameterizedTypeConstant)
            {
            ParameterizedTypeConstant constClass = (ParameterizedTypeConstant) constant;
            TypeComposition clzTarget = f_types.resolveClass(constClass.getPosition(), Collections.emptyMap());

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
