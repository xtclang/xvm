package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xUInt64
        extends TypeCompositionTemplate
    {
    public xUInt64(TypeSet types)
        {
        super(types, "x:UInt64", "x:Object", Shape.Const);

        addImplement("x:IntNumber");
        }

    @Override
    public void initDeclared()
        {
        }
    }
