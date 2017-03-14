package org.xvm.proto.template;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Type;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMethod
        extends xObject
    {
    public xMethod(TypeSet types)
        {
        // TODO:ParamType extends Tuple, ReturnType extends Tuple
        super(types, "x:Method<TargetType,ParamType,ReturnType>", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        // todo
        addPropertyTemplate("name", "x:String");
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return super.createHandle(clazz);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        // TODO: oValue should be a MethodConstant, allowing to find the necessary info in the module
        }

    public static class MethodHandle
            extends ObjectHandle
        {
        protected String m_sName;

        protected MethodHandle(Type type, TypeComposition clazz)
            {
            super(type, clazz);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_sName;
            }
        }

    }
