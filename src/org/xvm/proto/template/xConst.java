package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xConst
        extends TypeCompositionTemplate
    {
    public xConst(TypeSet types)
        {
        super(types, "x:Const", "x:Object", Shape.Interface);

        addImplement("x:collections.Hashable");
        addImplement("x:Orderable");
        }

    @Override
    public void initDeclared()
        {
        //    static Ordered compare(Const value1, Const value2)
        //
        //    static Boolean equals(Const value1, Const value2)
        //
        //    String to<String>()
        //
        //    Byte[] to<Byte[]>()
        //        {
        //        Field[] fields = meta.struct.to<Field[]>();
        //        // TODO use meta.struct
        //        }
        //
        //    @lazy Int hash.get()

        ensureFunctionTemplate("compare", new String[]{"x:Const", "x:Const"}, new String[]{"x:Ordered"});
        ensureFunctionTemplate("equals", new String[]{"x:Const", "x:Const"}, new String[]{"x:Boolean"});

        // an override
        ensureMethodTemplate("to", STRING, STRING);

        ensureMethodTemplate("to", new String[]{"x:collections.Array<x:Byte>"}, new String[]{"x:collections.Array<x:Byte>"});

        // TODO: @LazyRef Int hash.get()
        }
    }
