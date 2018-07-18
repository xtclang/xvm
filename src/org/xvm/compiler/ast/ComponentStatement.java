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
    public Component getComponent()
        {
        return component;
        }

    protected void setComponent(Component component)
        {
        this.component = component;
        }

    /**
     * Given a type expression that is used as some part of this ComponentStatement, determine if
     * that type is allowed to auto narrow.
     *
     * @param type  a TypeExpression that is a child of this ComponentStatement
     *
     * @return true iff the specified TypeExpression is being used in a place that supports
     *         auto-narrowing
     */
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


    // ----- name resolution -----------------------------------------------------------------------

    @Override
    protected Component resolveParentBySimpleName(String sName)
        {
        Component componentParent = super.resolveParentBySimpleName(sName);
        return componentParent == null && component != null && sName.equals(component.getSimpleName())
                ? component
                : componentParent;
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

    private Component component;
    private long      lStartPos;
    private long      lEndPos;
    }
