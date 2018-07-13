package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Nop;
import org.xvm.asm.op.Return_0;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A block statement specifies a series of statements.
 * <p/>
 * A block statement holds a special role in compilation, in that the four forms of "compilation
 * container" all rely on the block statement as the representation of the code being compiled:
 * <ol>
 * <li>MethodDeclarationStatement - represents a method or function, with a body defined by a
 *     StatementBlock.</li>
 * <li>NewExpression - (optionally) represents an anonymous inner class, defined within a
 *     StatementBlock.</li>
 * <li>LambdaExpression - represents a lambda function, whose body is represented by a
 *     StatementBlock.</li>
 * <li>StatementExpression - represents an "inlined" lambda, represented by a StatementBlock, with
 *     the resulting value of the expression being "returned" from one or more return statements
 *     inside of the StatementBlock.</li>
 * </ol>
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

        boolean fHasEnclosed = containsEnclosed;
        boolean fAddEnclosed = stmt instanceof StatementBlock && ((StatementBlock) stmt).boundary;
        assert !(fHasEnclosed & fAddEnclosed);
        if (fHasEnclosed)
            {
            // insert the new statement before the "enclosed" statements
            stmts.add(stmts.size()-1, stmt);
            }
        else
            {
            stmts.add(stmt);
            containsEnclosed |= fAddEnclosed;
            }
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
     * example, the {@link AstNode#resolveNames(StageMgr, ErrorListener)} method
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
    protected AstNode getCodeContainer()
        {
        AstNode parent = getParent();
        if (       parent instanceof MethodDeclarationStatement
                || parent instanceof NewExpression
                || parent instanceof LambdaExpression
                || parent instanceof StatementExpression)
            {
            return parent;
            }

        return super.getCodeContainer();
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
        RootContext ctx = new RootContext(code.getMethodStructure(), this);

        Statement that = this.validate(ctx.validatingContext(), errs);
        if (that != null && !errs.isAbortDesired())
            {
            boolean fCompletes = that.completes(ctx.emittingContext(), true, code, errs);

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
            else
                {
                // it is is possible that there is a dangling label at the end that is unreachable,
                // and it will not have been eliminated at this point, so "cap" the op code stream
                // with a Nop that will get removed by "dead code elimination"
                code.add(new Nop());
                }
            }
        }

    public TypeConstant determineLambdaResultType(Context ctx)
        {
        ctx = ctx.createCaptureContext(this);
        // TODO
        return null;
        }

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        List<Statement> stmts  = this.stmts;
        boolean         fValid = true;
        if (stmts != null && !stmts.isEmpty())
            {
            ctx = ctx.enterScope();
            for (int i = 0, c = stmts.size(); i < c; ++i)
                {
                Statement stmtOld = stmts.get(i);
                Statement stmtNew = stmtOld.validate(ctx, errs);
                if (stmtNew != stmtOld)
                    {
                    if (stmtNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        this.stmts = ensureArrayList(stmts);
                        stmts.set(i, stmtNew);
                        }
                    }

                if (errs.isAbortDesired())
                    {
                    break;
                    }
                }
            ctx = ctx.exitScope();
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletable = fReachable;

        List<Statement> stmts = this.stmts;
        if (stmts != null && !stmts.isEmpty())
            {
            // there is an implicit scope for the top-most statement block of a method
            boolean fImplicitScope = getParent() instanceof MethodDeclarationStatement;
            if (!fImplicitScope)
                {
                code.add(new Enter());
                }

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
            if (!fImplicitScope)
                {
                code.add(new Exit());
                }
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
    protected boolean         containsEnclosed;

    protected Map<String, ImportStatement> imports;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementBlock.class, "stmts");
    }
