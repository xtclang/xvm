package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xFunction
        extends xObject
    {
    public xFunction(TypeSet types)
        {
        super(types, "x:Function", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Const");

        //    Tuple invoke(Tuple args)
        //
        //    Type[] ReturnType;
        //
        //    Type[] ParamType;

        addPropertyTemplate("ReturnType", "x:collections.Array<x:Type>");
        addPropertyTemplate("ParamType", "x:collections.Array<x:Type>");
        addMethodTemplate("invoke", new String[] {"x:Tuple"}, new String[] {"x:Tuple"});
        }
    }
