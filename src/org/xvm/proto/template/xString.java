package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xString
        extends xObject
    {
    public xString(TypeSet types)
        {
        super(types, "x:String", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Sequence<x:Char>");

        //     Int length.get()

        addPropertyTemplate("length", "x:Int").makeReadOnly();
        }
    }
