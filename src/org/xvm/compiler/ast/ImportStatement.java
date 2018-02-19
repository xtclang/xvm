package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ResolvableConstant;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.NameResolver.Result;
import org.xvm.util.Severity;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 */
public class ImportStatement
        extends Statement
        implements NameResolver.NameResolving
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImportStatement(Expression cond, Token keyword, Token alias, List<Token> qualifiedName)
        {
        this.cond          = cond;
        this.keyword       = keyword;
        this.alias         = alias;
        this.qualifiedName = qualifiedName;

        // the qualified name will have to be resolved
        this.resolver = new NameResolver(this, qualifiedName.stream().map(token -> (String) token.getValue()).iterator());
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
        return qualifiedName.size();
        }

    /**
     * @param i  indicates which simple name of the imported name to obtain
     *
     * @return the i-th simple names in the imported name
     */
    public String getQualifiedNamePart(int i)
        {
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


    // ----- NameResolving interface ---------------------------------------------------------------

    @Override
    public NameResolver getNameResolver()
        {
        return resolver;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected AstNode registerStructures(ErrorListener errs)
        {
        if (cond != null)
            {
            log(errs, Severity.WARNING, Compiler.CONDITIONAL_IMPORT);
            }
        return super.registerStructures(errs);
        }

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        setStage(Stage.Resolving);

        // check if the alias name is an unhideable name
        Component component = resolveParentBySimpleName(getAliasName());
        if (component != null)
            {
            log(errs, Severity.ERROR, Compiler.NAME_UNHIDEABLE, getAliasName(), component.getIdentityConstant());
            }

        // as global visibility is resolved, each import statement registers itself so that anything
        // following it can see the import, but anything preceding it does not
        AstNode parent = getParent();
        while (!(parent instanceof StatementBlock))
            {
            parent = parent.getParent();
            }
        ((StatementBlock) parent).registerImport(this, errs);

        if (getNameResolver().resolve(errs) == Result.DEFERRED)
            {
            listRevisit.add(this);
            return this;
            }

        return super.resolveNames(listRevisit, errs);
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

    private NameResolver  resolver;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ImportStatement.class, "cond");
    }
