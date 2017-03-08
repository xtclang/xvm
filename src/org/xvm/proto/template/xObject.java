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

        PropertyTemplate propMeta = addPropertyTemplate("meta", "x:Meta");
        propMeta.setGetAccess(Access.Protected);
        propMeta.setSetAccess(Access.Protected);

        addFunctionTemplate("equals", new String[]{"x:Object", "x:Object"}, VOID);

        addMethodTemplate("to", STRING, STRING);
        addMethodTemplate("to", new String[]{"x:collections.Array<x:String>"}, new String[]{"x:collections.Array<x:String>"});
        addMethodTemplate("to", new String[]{"x:Tuple<x:Object>"}, new String[]{"x:Tuple<x:Object>"});
        addMethodTemplate("to", new String[]{"x:Function"}, new String[]{"x:Function"});
        }

    public static String[] VOID = new String[0];
    public static String[] BOOLEAN = new String[]{"x:Boolean"};
    public static String[] INT = new String[]{"x:Int"};
    public static String[] STRING = new String[]{"x:String"};
    public static String[] THIS = new String[]{"this.Type"};
    public static String[] CONDITIONAL_THIS = new String[]{"x:ConditionalTuple<this.Type>"};
    }
