package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xHashable
        extends TypeCompositionTemplate
    {
    public xHashable(TypeSet types)
        {
        super(types, "x:collections.Hashable", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        // @ro Int hash;
        ensurePropertyTemplate("hash", "x:Int").makeReadOnly();
        }
    }
