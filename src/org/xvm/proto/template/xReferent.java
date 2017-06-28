package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xReferent
        extends ClassTemplate
    {
    public xReferent(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        }
    }
