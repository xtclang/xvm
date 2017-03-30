package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xStruct
        extends xObject
    {
    public xStruct(TypeSet types)
        {
        super(types, "x:Struct", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    Tuple to<Tuple>();
        //    Ref[] to<Ref[]>();
        //    @op Ref elementFor(String name)

        ensureMethodTemplate("to", new String[]{"x:Tuple"}, new String[]{"x:Tuple"});
        ensureMethodTemplate("to", new String[]{"x:collections.Array<x:Ref>"}, new String[]{"x:collections.Array<x:Ref>"});
        ensureMethodTemplate("elementFor", STRING, new String[]{"x:Ref"});
        }
    }
