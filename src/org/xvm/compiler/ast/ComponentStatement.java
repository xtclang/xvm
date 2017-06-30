package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Register the presence of an ImportStatement.
     *
     * @param stmt  an ImportStatement
     */
    protected void registerImport(ImportStatement stmt)
        {
        assert stmt != null;
        if (imports == null)
            {
            imports = new HashMap<>();
            }
        ImportStatement stmtPrev = imports.put(stmt.getAliasName(), stmt);
        assert stmtPrev == null;
        }

    /**
     * Look up the specified simple name to see if there is an ImportStatement associated with it.
     *
     * @param sName  the alias name of an import
     *
     * @return the associated ImportStatement, or null if there is none by that simple alias name
     */
    public ImportStatement getImportStatement(String sName)
        {
        return imports == null ? null : imports.get(sName);
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

    protected Component component;
    protected long      lStartPos;
    protected long      lEndPos;

    protected Map<String, ImportStatement> imports;
    }
