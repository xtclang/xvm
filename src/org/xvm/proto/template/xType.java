package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xType
        extends TypeCompositionTemplate
    {
    public xType(TypeSet types)
        {
        super(types, "x:Type", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        // TODO
        }
    }
