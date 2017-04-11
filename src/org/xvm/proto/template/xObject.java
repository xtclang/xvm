package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

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

        ensureFunctionTemplate("equals", new String[]{"x:Object", "x:Object"}, VOID);

        ensureMethodTemplate("to", VOID, STRING);
        ensureMethodTemplate("to", VOID, new String[]{"x:collections.Array<x:String>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Tuple<x:Object>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:Function"});
        }
    }
