package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xConditionalTuple
        extends TypeCompositionTemplate
    {
    public xConditionalTuple(TypeSet types)
        {
        // deferring the variable length generics <ElementType...>
        super(types, "x:ConditionalTuple", "x:Tuple", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        }
    }
