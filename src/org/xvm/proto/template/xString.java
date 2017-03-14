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
public class xString
        extends xObject
    {
    public xString(TypeSet types)
        {
        super(types, "x:String", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Sequence<x:Char>");

        //     Int length.get()

        addPropertyTemplate("length", "x:Int").makeReadOnly();
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        StringHandle hThis = (StringHandle) handle;

        hThis.m_sValue = (String) oValue;
        }

    public static class StringHandle
            extends ObjectHandle
        {
        protected String m_sValue;

        protected StringHandle(Type type, TypeComposition clazz)
            {
            super(type, clazz);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_sValue;
            }
        }
    }
