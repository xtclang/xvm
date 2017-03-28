package org.xvm.proto.template;

import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xSequential
        extends xObject
    {
    public xSequential(TypeSet types)
        {
        super(types, "x:Sequential", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        ensureImplement("x:Orderable");

        //    conditional Sequential prev();
        //    conditional Sequential next();
        //    Sequential prevValue()
        //    Sequential nextValue();

        addMethodTemplate("next", VOID, CONDITIONAL_THIS);
        addMethodTemplate("prev", VOID, CONDITIONAL_THIS);
        addMethodTemplate("nextValue", VOID, THIS);
        addMethodTemplate("prevValue", VOID, THIS);
        }
    }
