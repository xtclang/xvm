package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;

import org.xvm.compiler.Token;


/**
 * Represents a statement that corresponds to a Component in an Ecstasy FileStructure.
 *
 * @author cp 2017.04.12
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
    public ComponentStatement getComponentStatement()
        {
        return this;
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

    @Override
    protected Component resolveSimpleName(String sName)
        {
        // TODO this needs to do roughly the same thing that the NameResolve does under "case RESOLVED_PARTIAL:"
        // TODO WARNING this is just temporary!!! - needs to check supers, interfaces, mixins, ...
        return component == null
                ? null
                : component.getChild(sName);
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
