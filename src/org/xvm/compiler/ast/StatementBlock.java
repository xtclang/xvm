package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Return_0;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * An block statement specifies a series of statements.
 */
public class StatementBlock
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public StatementBlock(List<Statement> stmts)
        {
        this(stmts, null,
                stmts.isEmpty() ? 0L : stmts.get(0).getStartPosition(),
                stmts.isEmpty() ? 0L : stmts.get(stmts.size()-1).getEndPosition());
        }

    public StatementBlock(List<Statement> stmts, long lStartPos, long lEndPos)
        {
        this(stmts, null, lStartPos, lEndPos);
        }

    public StatementBlock(List<Statement> stmts, Source source, long lStartPos, long lEndPos)
        {
        this.stmts     = stmts;
        this.source    = source;
        this.lStartPos = lStartPos;
        this.lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public List<Statement> getStatements()
        {
        return stmts;
        }

    public void addStatement(Statement stmt)
        {
        stmt.setParent(this);
        stmts.add(stmt);
        }

    /**
     * Mark the statement block as representing a file boundary, such that the parent (if any) and
     * each of the child (if any) statements are each assumed to be from separate files.
     */
    protected void markFileBoundary()
        {
        boundary = true;
        }

    /**
     * @return true if this StatementBlock has been marked as a file boundary
     */
    public boolean isFileBoundary()
        {
        return boundary;
        }

    /**
     * Register an import statement that occurs within this StatementBlock.
     *
     * @param stmt  the ImportStatement to register
     * @param errs  the ErrorListener to use to log any errors
     */
    protected void registerImport(ImportStatement stmt, ErrorListener errs)
        {
        if (imports == null)
            {
            imports = new HashMap<>();
            }

        // make sure that no existing import uses the same alias
        String sAlias = stmt.getAliasName();
        if (imports.containsKey(sAlias))
            {
            log(errs, Severity.ERROR, Compiler.DUPLICATE_IMPORT, sAlias);
            // fall through; don't stop compilation at this point, and just use the new import to
            // overwrite the old
            }

        imports.put(stmt.getAliasName(), stmt);
        }

    /**
     * Obtain the ImportStatement for a particular import alias. This method has different behaviors
     * depending on the phase of compilation. During the phase in which the imports are registered,
     * this will only provide an answer for the imports that have already been registered. For
     * example, the {@link AstNode#resolveNames(List, ErrorListener)} method
     * is used to resolve all global names (all names, down to the method level, but not resolving
     * within any methods), and thus imports outside of methods are all registered during that
     * phase, such that only the ones registered will be visible via this method. The reason for
     * this approach is that imports are not visible outside of a file, and furthermore, because
     * they can occur at any point within the file, only those encountered "above" some current
     * point in that file are considered to be visible at that point.
     *
     * @param sName  the import alias
     *
     * @return the ImportStatement, or null
     */
    public ImportStatement getImport(String sName)
        {
        return imports == null
                ? null
                : imports.get(sName);
        }

    @Override
    public Source getSource()
        {
        return source == null
                ? super.getSource()
                : source;
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

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    /**
     * Generate assembly code for a method. This is the entry point for the compilation of a method.
     *
     * @param code  the code object to which the assembly is added
     * @param errs  the error listener to log to
     */
    public void compileMethod(Code code, ErrorListener errs)
        {
        Context ctx = new Context(code.getMethodStructure());

        if (validate(ctx, errs))
            {
            boolean fCompletes = completes(ctx, true, code, errs);

            if (fCompletes)
                {
                if (code.getMethodStructure().getReturns().isEmpty())
                    {
                    // a void method has an implicit "return;" at the end of it
                    code.add(new Return_0());
                    }
                else
                    {
                    errs.log(Severity.ERROR, Compiler.RETURN_REQUIRED, null, source,
                            getEndPosition(), getEndPosition());
                    }
                }
            }
        }

    @Override
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        int cErrs = 0;

        List<Statement> stmts = this.stmts;
        if (stmts != null && !stmts.isEmpty())
            {
            ctx.enterScope();
            for (Statement stmt : stmts)
                {
                if (!stmt.validate(ctx, errs) && ++cErrs > 10)
                    {
                    break;
                    }
                }
            ctx.exitScope();
            }

        return cErrs == 0;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletable = fReachable;

        List<Statement> stmts = this.stmts;
        if (stmts != null && !stmts.isEmpty())
            {
            code.add(new Enter());
            for (Statement stmt : stmts)
                {
                if (fReachable && !fCompletable)
                    {
                    // this statement is the first statement that cannot be reached
                    fReachable = false;
                    stmt.log(errs, Severity.ERROR, Compiler.NOT_REACHABLE);
                    }

                fCompletable &= stmt.completes(ctx, fReachable, code, errs);
                }
            code.add(new Exit());
            }

        return fCompletable;
        }


    // ----- name resolution -----------------------------------------------------------------------

    @Override
    protected ImportStatement resolveImportBySingleName(String sName)
        {
        // if this is a synthetic block statement that acts as a collection of multiple files, then
        // the search for the import has just crossed a file boundary, and nothing was found
        if (isFileBoundary())
            {
            return null;
            }

        ImportStatement stmtImport = getImport(sName);
        return stmtImport == null
                ? super.resolveImportBySingleName(sName)
                : stmtImport;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        if (stmts == null || stmts.isEmpty())
            {
            return "{}";
            }

        StringBuilder sb = new StringBuilder();
        sb.append('{');

        int firstNonEnum = 0;
        if (stmts.get(0) instanceof TypeCompositionStatement
                && ((TypeCompositionStatement) stmts.get(0)).category.getId() == Token.Id.ENUM_VAL)
            {
            boolean multiline = false;
            for (int i = 0, c = stmts.size(); i < c; ++i)
                {
                Statement stmt = stmts.get(i);
                if (stmt instanceof TypeCompositionStatement
                        && ((TypeCompositionStatement) stmt).category.getId() == Token.Id.ENUM_VAL)
                    {
                    TypeCompositionStatement enumStmt = (TypeCompositionStatement) stmt;
                    multiline |= enumStmt.doc != null || enumStmt.body != null;
                    ++firstNonEnum;
                    }
                }

            String sBetweenEnums = multiline ? ",\n" : ", ";
            for (int i = 0; i < firstNonEnum; ++i)
                {
                if (i == 0)
                    {
                    sb.append('\n');
                    }
                else
                    {
                    sb.append(sBetweenEnums);
                    }
                sb.append(stmts.get(i));
                }
            if (firstNonEnum < stmts.size())
                {
                sb.append(';');
                }
            }

        for (int i = firstNonEnum, c = stmts.size(); i < c; ++i)
            {
            sb.append('\n')
              .append(stmts.get(i));
            }
        sb.append("\n}");

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Source          source;
    protected List<Statement> stmts;
    protected long            lStartPos;
    protected long            lEndPos;
    protected boolean         boundary;

    protected Map<String, ImportStatement> imports;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementBlock.class, "stmts");
    }
