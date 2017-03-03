package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xMethod
        extends xObject
    {
    public xMethod(TypeSet types)
        {
        // TODO:ParamType extends Tuple, ReturnType extends Tuple
        super(types, "x:Method<TargetType,ParamType,ReturnType>", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        // todo
        addPropertyTemplate("name", "x:String");
        }
    }
