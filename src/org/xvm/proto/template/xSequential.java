package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xSequential
        extends TypeCompositionTemplate
    {
    public xSequential(TypeSet types)
        {
        super(types, "x:Sequential", "x:Object", Shape.Interface);

        addImplement("x:Orderable");
        }

    @Override
    public void initDeclared()
        {
        //    conditional Sequential prev();
        //    conditional Sequential next();
        //    Sequential prevValue()
        //    Sequential nextValue();

        ensureMethodTemplate("next", VOID, CONDITIONAL_THIS);
        ensureMethodTemplate("prev", VOID, CONDITIONAL_THIS);
        ensureMethodTemplate("nextValue", VOID, THIS);
        ensureMethodTemplate("prevValue", VOID, THIS);
        }
    }
