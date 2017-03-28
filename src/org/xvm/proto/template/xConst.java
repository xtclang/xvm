package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xConst
        extends xObject
    {
    public xConst(TypeSet types)
        {
        super(types, "x:Const", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        // in-place generation of Hashable
        xObject tctHashable = new xObject(f_types, "x:collections.Hashable", "x:Object", Shape.Interface);

        // @ro Int hash;
        tctHashable.addPropertyTemplate("hash", "x:Int").makeReadOnly();

        f_types.addTemplate(tctHashable);

        ensureImplement("x:collections.Hashable");
        ensureImplement("x:Orderable");

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

        addFunctionTemplate("compare", new String[]{"x:Const", "x:Const"}, new String[] {"x:Ordered"});
        addFunctionTemplate("equals", new String[]{"x:Const", "x:Const"}, new String[] {"x:Boolean"});

        // an override
        addMethodTemplate("to", STRING, STRING);

        addMethodTemplate("to", new String[]{"x:collections.Array<x:Byte>"}, new String[]{"x:collections.Array<x:Byte>"});

        // TODO: @LazyRef Int hash.get()
        }
    }
