package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xUInt64
        extends xObject
    {
    public xUInt64(TypeSet types)
        {
        super(types, "x:UInt64", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        ensureImplement("x:IntNumber");

        }
    }
