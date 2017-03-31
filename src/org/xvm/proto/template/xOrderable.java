package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xOrderable
        extends TypeCompositionTemplate
    {
    public xOrderable(TypeSet types)
        {
        super(types, "x:Orderable", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //    @op Interval<Orderable> to(Orderable that)
        //    Orderable minOf(Orderable that)
        //    Orderable maxOf(Orderable that)

        ensureMethodTemplate("to", THIS, new String[]{"x:Interval<x:Orderable>"});
        ensureMethodTemplate("minOf", THIS, THIS);
        ensureMethodTemplate("maxOf", THIS, THIS);
        }
    }
