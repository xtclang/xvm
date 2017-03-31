package org.xvm.proto.template;

import org.xvm.proto.*;

import java.util.HashMap;
import java.util.Map;

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

        PropertyTemplate propMeta = ensurePropertyTemplate("meta", "x:Meta");
        propMeta.setGetAccess(Access.Protected);
        propMeta.setSetAccess(Access.Protected);

        addFunctionTemplate("equals", new String[]{"x:Object", "x:Object"}, VOID);

        ensureMethodTemplate("to", VOID, STRING);
        ensureMethodTemplate("to", VOID, new String[]{"x:collections.Array<x:String>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Tuple<x:Object>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Function"});
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new GenericHandle(clazz);
        }

    @Override
    public ObjectHandle createStruct(Frame frame)
        {
        assert f_asFormalType.length == 0;
        assert f_shape == Shape.Class || f_shape == Shape.Const;

        GenericHandle hThis = new GenericHandle(f_clazzCanonical,
                f_clazzCanonical.ensureStructType());
        hThis.createFields();
        return hThis;
        }

    @Override
    public ObjectHandle getProperty(ObjectHandle hTarget, String sName)
        {
        GenericHandle hThis = (GenericHandle) hTarget;
        ObjectHandle  hProp = hThis.m_mapFields.get(sName);
        if (hProp == null)
            {
            throw new IllegalStateException((hThis.m_mapFields.containsKey(sName) ?
                    "Un-initialized property " : "Invalid property ") + sName);
            }
        return hProp;
        }

    @Override
    public void setProperty(ObjectHandle hTarget, String sName, ObjectHandle hValue)
        {
        // check the access
        GenericHandle hThis = (GenericHandle) hTarget;
        hThis.m_mapFields.put(sName, hValue);
        }

    public static class GenericHandle
            extends ObjectHandle
        {
        // keyed by the property name
        Map<String, ObjectHandle> m_mapFields = new HashMap<>();

        public GenericHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public GenericHandle(TypeComposition clazz, Type type)
            {
            super(clazz, type);
            }

        public void createFields()
            {
            f_clazz.f_template.forEachProperty(pt ->
                {
                if (!pt.isReadOnly())
                    {
                    m_mapFields.put(pt.f_sName, null);
                    }
                });
            }
        @Override
        public String toString()
            {
            return super.toString() + m_mapFields;
            }
        }

    }
