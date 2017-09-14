package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ParameterizedTypeConstant;

import org.xvm.proto.*;

import java.util.Collections;
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
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ParameterizedTypeConstant)
            {
            ParameterizedTypeConstant constClass = (ParameterizedTypeConstant) constant;
            TypeComposition clzTarget = f_types.ensureComposition(constClass.getPosition(), Collections.emptyMap());

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
