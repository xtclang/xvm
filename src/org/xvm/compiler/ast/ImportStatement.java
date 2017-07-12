package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class ImportStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImportStatement(Expression cond, Token keyword, Token alias, List<Token> qualifiedName)
        {
        this.cond          = cond;
        this.keyword       = keyword;
        this.alias         = alias;
        this.qualifiedName = qualifiedName;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the import alias
     */
    public String getAliasName()
        {
        return (String) alias.getValue();
        }

    /**
     * @return the number of simple names in the imported name
     */
    public int getQualifiedNameLength()
        {
        return importExpand == null
                ? qualifiedName.size()
                : importExpand.getQualifiedNameLength() + qualifiedName.size() - 1;
        }

    /**
     * @param i  indicates which simple name of the imported name to obtain
     *
     * @return the i-th simple names in the imported name
     */
    public String getQualifiedNamePart(int i)
        {
        if (importExpand != null)
            {
            int cExpand = importExpand.getQualifiedNameLength();
            if (i < cExpand)
                {
                return importExpand.getQualifiedNamePart(i);
                }

            // consider imports of "a.b.c" and "c.d.e", so expand "c.d.e" to "a.b.c.d.e"
            i = i - cExpand + 1;
            }

        return (String) qualifiedName.get(i).getValue();
        }

    /**
     * @return the imported name as an array of simple names
     */
    public String[] getQualifiedName()
        {
        int      cNames = getQualifiedNameLength();
        String[] asName = new String[cNames];
        for (int i = 0; i < cNames; ++i)
            {
            asName[i] = getQualifiedNamePart(i);
            }
        return asName;
        }

    /**
     * If this import statement begins with another import statement's alias, expand this import
     * statement using that import statement.
     *
     * @param importExpand  the ImportStatement that this ImportStatement can use to expand itself
     */
    protected void expand(ImportStatement importExpand)
        {
        assert importExpand.getAliasName().equals(getQualifiedNamePart(0));
        assert this.importExpand == null;
        this.importExpand = importExpand;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return alias.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(ErrorListener errs)
        {
        if (cond != null)
            {
            log(errs, Severity.WARNING, Compiler.CONDITIONAL_IMPORT);
            }
        super.registerStructures(errs);
        }

    @Override
    public void resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        // as global visibility is resolved, each import statement registers itself so that anything
        // following it can see the import, but anything preceding it does not
        AstNode parent = getParent();
        while (!(parent instanceof StatementBlock))
            {
            parent = parent.getParent();
            }
        ((StatementBlock) parent).registerImport(this, errs);

        super.resolveNames(listRevisit, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (cond != null)
            {
            sb.append("if (")
              .append(cond)
              .append(") { ");
            }

        sb.append("import ");

        boolean first = true;
        String last = null;
        for (Token name : qualifiedName)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            last = String.valueOf(name.getValue());
            sb.append(last);
            }

        if (alias != null && !last.equals(alias.getValue()))
            {
            sb.append(" as ")
              .append(alias.getValue());
            }

        sb.append(';');

        if (cond != null)
            {
            sb.append(" }");
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression  cond;
    protected Token       keyword;
    protected Token       alias;
    protected List<Token> qualifiedName;

    private ImportStatement importExpand;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ImportStatement.class, "cond");
    }
