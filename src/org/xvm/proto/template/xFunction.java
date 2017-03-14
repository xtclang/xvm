package org.xvm.proto.template;

import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFunction
        extends xObject
    {
    public xFunction(TypeSet types)
        {
        super(types, "x:Function", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Const");

        //    Tuple invoke(Tuple args)
        //
        //    Type[] ReturnType;
        //
        //    Type[] ParamType;

        addPropertyTemplate("ReturnType", "x:collections.Array<x:Type>");
        addPropertyTemplate("ParamType", "x:collections.Array<x:Type>");
        addMethodTemplate("invoke", new String[] {"x:Tuple"}, new String[] {"x:Tuple"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new FunctionHandle(clazz.ensurePublicType(), clazz);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        // TODO: wrong
        FunctionHandle hThis = (FunctionHandle) handle;
        FunctionHandle hThat = (FunctionHandle) oValue;

        hThis.m_struct = hThat.m_struct;
        }

    public static class FunctionHandle
            extends ObjectHandle
        {
        protected Struct m_struct;

        public FunctionHandle(Type type, TypeComposition clazz)
            {
            super(type, clazz);
            }
        }
    }
