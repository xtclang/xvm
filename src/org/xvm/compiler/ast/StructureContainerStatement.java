package org.xvm.compiler.ast;


import org.xvm.asm.Constants.Access;
import org.xvm.asm.StructureContainer;
import org.xvm.compiler.Token;

import java.util.List;


/**
 * Represents a statement that corresponds to a StructureContainer in an Ecstasy FileStructure.
 *
 * @author cp 2017.04.12
 */
public abstract class StructureContainerStatement
        extends Statement
    {
    // ----- accessors -----------------------------------------------------------------------------

    public StructureContainer getStructure()
        {
        return struct;
        }

    protected void setStructure(StructureContainer struct)
        {
        this.struct = struct;
        }


    // ----- helpers -------------------------------------------------------------------------------

    public static boolean isStatic(List<Token> modifiers)
        {
        if (modifiers != null && !modifiers.isEmpty())
            {
            for (Token modifier : modifiers)
                {
                if (modifier.getId() == Token.Id.STATIC)
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    public static Access getAccess(List<Token> modifiers)
        {
        if (modifiers != null && !modifiers.isEmpty())
            {
            for (Token modifier : modifiers)
                {
                switch (modifier.getId())
                    {
                    case PUBLIC:
                        return Access.PUBLIC;
                    case PROTECTED:
                        return Access.PROTECTED;
                    case PRIVATE:
                        return Access.PRIVATE;
                    }
                }
            }
        return null;
        }

    // ----- fields --------------------------------------------------------------------------------

    protected StructureContainer struct;
    }
