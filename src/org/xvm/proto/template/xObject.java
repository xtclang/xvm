package org.xvm.proto.template;

import org.xvm.proto.*;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xObject
        extends TypeCompositionTemplate
    {
    public xObject(TypeSet types)
        {
        super(types, "x:Object", null, Shape.Class);
        }

    // subclassing
    protected xObject(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        // protected Meta meta.get()
        // static Boolean equals(Object o1, Object o2)
        // String to<String>()
        // Object[] to<Object[]>()
        // (Object) to<(Object)>()
        // @auto function Object() to<function Object()>()

        PropertyTemplate propMeta = addPropertyTemplate("meta", "x:Meta");
        propMeta.setGetAccess(Access.Protected);
        propMeta.setSetAccess(Access.Protected);

        addFunctionTemplate("equals", new String[]{"x:Object", "x:Object"}, VOID);

        addMethodTemplate("to", STRING, STRING);
        addMethodTemplate("to", new String[]{"x:collections.Array<x:String>"}, new String[]{"x:collections.Array<x:String>"});
        addMethodTemplate("to", new String[]{"x:Tuple<x:Object>"}, new String[]{"x:Tuple<x:Object>"});
        addMethodTemplate("to", new String[]{"x:Function"}, new String[]{"x:Function"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new GenericHandle(clazz);
        }

    @Override
    public void assignConstValue(ObjectHandle handle, Object oValue)
        {
        throw new IllegalStateException();
        }

    public void copy(ObjectHandle handle, ObjectHandle that)
        {
        GenericHandle hThis = (GenericHandle) handle;
        GenericHandle hThat = (GenericHandle) that;

        // check the type?
        hThis.m_struct = hThat.m_struct;
        }

    @Override
    public void initializeHandle(ObjectHandle handle, ObjectHandle[] ahArg)
        {
        throw new UnsupportedOperationException("TODO");
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        protected Struct m_struct;

        public GenericHandle(TypeComposition clazz)
            {
            super(clazz, clazz.ensurePublicType());
            }

        @Override
        public String toString()
            {
            return super.toString() + m_struct;
            }
        }

    }
