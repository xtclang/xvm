package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;

import org.xvm.compiler.Token;


/**
 * Represents a statement that corresponds to a Component in an Ecstasy FileStructure.
 */
public abstract class ComponentStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    protected ComponentStatement(long lStartPos, long lEndPos)
        {
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isComponentNode()
        {
        return true;
        }

    @Override
    public Component getComponent()
        {
        return component;
        }

    protected void setComponent(Component component)
        {
        this.component = component;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return false;
        }

    @Override
    protected ConstantPool pool()
        {
        return component == null
                ? super.pool()
                : component.getConstantPool();
        }

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }


    // ----- compile phases ------------------------------------------------------------------------


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

    private Component  component;
    private final long lStartPos;
    private final long lEndPos;
    }
