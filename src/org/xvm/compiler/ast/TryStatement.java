package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.constants.StringConstant;

import org.xvm.asm.op.CatchEnd;
import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.FinallyEnd;
import org.xvm.asm.op.FinallyStart;
import org.xvm.asm.op.GuardAll;
import org.xvm.asm.op.GuardStart;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try" or "using" statement.
 */
public class TryStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TryStatement(Token keyword, List<AssignmentStatement> resources, StatementBlock block, List<CatchStatement> catches, StatementBlock catchall)
        {
        assert block != null;

        this.keyword   = keyword;
        this.resources = resources == null ? Collections.EMPTY_LIST : resources;
        this.block     = block;
        this.catches   = catches == null ? Collections.EMPTY_LIST : catches;
        this.catchall  = catchall;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return catchall == null
                ? catches.isEmpty()
                        ? block.getEndPosition()
                        : catches.get(catches.size()-1).getEndPosition()
                : catchall.getEndPosition();
        }

    public boolean hasResources()
        {
        return !resources.isEmpty();
        }

    public boolean hasCatches()
        {
        return !catches.isEmpty();
        }

    public boolean hasFinally()
        {
        return catchall != null;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        if (hasResources())
            {
            // the using/try-with-resources section provides a context to the rest of the statement
            // (it is the outer-most layer of the "onion")
            ctx = ctx.enter();

            int c = resources.size();
            aFinallyClose = new FinallyStart[c];
            for (int i = 0; i < c; ++i)
                {
                Statement stmt = resources.get(i);

                // TODO
                }
            }

        ctx = ctx.enter();
        ctx.markNonCompleting();
        block.validate(ctx, errs);
        // TODO donate varAsn info to end
        ctx = ctx.exit();

        if (catches != null)
            {
            for (int i = 0, c = catches.size(); i < c; ++i)
                {
                CatchStatement stmt = catches.get(i);

                ctx = ctx.enter();
                ctx.markNonCompleting();

                // validate the catch clause
                VariableDeclarationStatement targetNew = (VariableDeclarationStatement) target.validate(ctx, errs);
                if (targetNew != null)
                    {
                    target = targetNew;

                    if (!targetNew.getType().isA(pool().typeException()))
                        {
                        targetNew.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                pool().typeException().getValueString(), targetNew.getType().getValueString());
                        }

                    // validate the block
                    StatementBlock blockNew = (StatementBlock) block.validate(ctx, errs);
                    if (blockNew != null)
                        {
                        block = blockNew;
                        }

                    // contribute the variable assignment information from the catch back to the try statement,
                    // since the normal completion of a try combined with the normal completion of all of its
                    // catch clauses combines to provide the assignment impact of the try/catch
                    // TODO
                    }

                ctx.exit();
                }
            }

        if (catchall != null)
            {

            }

        if (hasResources())
            {
            ctx = ctx.exit();
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        // using() or try()-with-resources
        FinallyStart[] aFinallyClose = null;
        if (hasResources())
            {
            // the first resource is declared outside of any try/finally block, but it is not
            // visible beyond this statement
            ctx = ctx.enter();

            int c = resources.size();
            aFinallyClose = new FinallyStart[c];
            for (int i = 0; i < c; ++i)
                {
                AssignmentStatement stmt = resources.get(i);
                fCompletes = stmt.completes(ctx, fCompletes, code, errs);
                FinallyStart opFinally = new FinallyStart();
                aFinallyClose[i] = opFinally;
                code.add(new GuardAll(opFinally));
                }
            }

        // try..finally
        FinallyStart opFinallyBlock = null;
        if (hasFinally())
            {
            opFinallyBlock = new FinallyStart();
            code.add(new GuardAll(opFinallyBlock));
            }

        // try..catch
        CatchStart[] aCatchStart = null;
        if (hasCatches())
            {
            int c = catches.size();
            aCatchStart = new CatchStart[c];
            for (int i = 0; i < c; ++i)
                {
                CatchStatement               stmtCatch = catches.get(i);
                VariableDeclarationStatement stmtVar   = stmtCatch.target;
                StringConstant               constName = pool().ensureStringConstant(stmtVar.getName());
                aCatchStart[i] = new CatchStart(stmtVar.getRegister(), constName);
                }

            // single "try" for all of the catches
            code.add(new GuardStart(aCatchStart));
            }

        // the "guarded" body of the using/try statement
        boolean fBlockCompletes    = block.completes(ctx, fCompletes, code, errs);

        // the "catch" blocks
        boolean fAnyCatchCompletes = false;
        if (hasCatches())
            {
            int c = catches.size();
            for (int i = 0; i < c; ++i)
                {
                CatchStatement               stmtCatch = catches.get(i);
                VariableDeclarationStatement stmtVar   = stmtCatch.target;
                code.add(aCatchStart[i]);
                fAnyCatchCompletes |= stmtCatch.completes(ctx, fCompletes, code, errs);
                code.add(new CatchEnd(getEndLabel()));
                }
            }

        // the "finally" block
        boolean fFinallyCompletes = true;
        if (hasFinally())
            {
            code.add(opFinallyBlock);
            fFinallyCompletes = catchall.completes(ctx, fCompletes, code, errs);
            code.add(new FinallyEnd());
            }

        if (hasResources())
            {
            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                code.add(aFinallyClose[i]);
                AssignmentStatement stmt = resources.get(i);
                if (stmt.getLValueExpression().getType().isA(pool().typeCloseable()))
                // TODO for provably-Closeable types: value.close()
                // TODO for possibly-Closeable types: if value.is(Closeable) value.close()
                code.add(new FinallyEnd());
                }

            // no resources remain in scope after the try/using statement
            ctx = ctx.exit();
            }

        return (fBlockCompletes | fAnyCatchCompletes) & fFinallyCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (!resources.isEmpty())
            {
            sb.append(" (");
            boolean first = true;
            for (Statement resource : resources)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(resource);
                }
            sb.append(')');
            }

        sb.append('\n')
          .append(indentLines(block.toString(), "    "));

        for (CatchStatement catchone : catches)
            {
            sb.append('\n')
              .append(catchone);
            }

        if (catchall != null)
            {
            sb.append("\nfinally\n")
              .append(indentLines(catchall.toString(), "    "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return keyword.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                     keyword;
    protected List<AssignmentStatement> resources;
    protected StatementBlock            block;
    protected List<CatchStatement>      catches;
    protected StatementBlock            catchall;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TryStatement.class,
            "resources", "block", "catches", "catchall");
    }
