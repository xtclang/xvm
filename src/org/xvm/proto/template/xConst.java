package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
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

        addImplement("x:collections.Hashable");
        }

    @Override
    public void initDeclared()
        {
        // in-place generation of Hashable
        TypeCompositionTemplate tctHashable = new TypeCompositionTemplate(m_types, "x:collections.Hashable", "x:Object", Shape.Interface);

        // @ro Int hash;
        tctHashable.addPropertyTemplate("hash", "x:Int").makeReadOnly();

        m_types.addCompositionTemplate(tctHashable);

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

        addMethodTemplate("to", new String[] {"x:Array<x:Byte>"}, new String[] {"x:Array<x:Byte>"});

        // TODO: @LazyRef Int hash.get()
        }
    }
