package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodType;

import org.xvm.asm.op.CatchEnd;
import org.xvm.asm.op.CatchStart;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.FinallyEnd;
import org.xvm.asm.op.FinallyStart;
import org.xvm.asm.op.GuardAll;
import org.xvm.asm.op.GuardEnd;
import org.xvm.asm.op.GuardStart;
import org.xvm.asm.op.Invoke_00;
import org.xvm.asm.op.JumpNType;
import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Throw;

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
        assert block     != null;

        this.keyword   = keyword;
        this.resources = resources == null || resources.isEmpty() ? null : resources;
        this.block     = block;
        this.catches   = catches == null || catches.isEmpty() ? null : catches;
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
                ? catches == null
                        ? block.getEndPosition()
                        : catches.get(catches.size()-1).getEndPosition()
                : catchall.getEndPosition();
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

        if (resources != null)
            {
            // the using/try-with-resources section provides a context to the rest of the
            // statement (it is the outer-most layer of the "onion")
            ctx = ctx.enter();

            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                AssignmentStatement useOld = resources.get(i);
                AssignmentStatement useNew = (AssignmentStatement) useOld.validate(ctx, errs);
                if (useNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (useNew != useOld)
                        {
                        resources.set(i, useNew);
                        }
                    }

                // each using/try-with-resources is a nested try-finally
                ctx = ctx.enter();
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
                CatchStatement catchOld = catches.get(i);
                CatchStatement catchNew = (CatchStatement) catchOld.validate(ctx, errs);
                if (catchNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (catchNew != catchOld)
                        {
                        catches.set(i, catchNew);
                        }

                    TypeConstant typeE = catchNew.getCatchType();
                    for (int iPrev = 0; iPrev < i; ++iPrev)
                        {
                        TypeConstant typeEPrev = catches.get(iPrev).target.getType();
                        if (typeE.isA(typeEPrev))
                            {
                            catchNew.target.log(errs, Severity.ERROR, Compiler.CATCH_TYPE_ALREADY_CAUGHT,
                                    typeE.getValueString(), typeEPrev.getValueString());

                            // only report this error once (but don't bother setting fValid to false
                            // because this isn't an error bad enough to stop compilation at this
                            // point)
                            break;
                            }
                        }
                    }
                }
            }

        if (catchall != null)
            {
            StatementBlock catchallNew = (StatementBlock) catchall.validate(ctx, errs);
            if (catchallNew == null)
                {
                fValid = false;
                }
            else
                {
                catchall = catchallNew;
                }
            }

        if (resources != null)
            {
            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                ctx = ctx.exit();
                }
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
        Register[]     aRegClose     = null;
        if (resources != null)
            {
            // the first resource is declared outside of any try/finally block, but it is not
            // visible beyond this statement
            code.add(new Enter());

            int c = resources.size();
            aFinallyClose = new FinallyStart[c];
            for (int i = 0; i < c; ++i)
                {
                fCompletes = resources.get(i).completes(ctx, fCompletes, code, errs);
                FinallyStart opFinally = new FinallyStart();
                aFinallyClose[i] = opFinally;
                code.add(new GuardAll(opFinally));
                }
            }

        // try..finally
        FinallyStart opFinallyBlock = null;
        if (catchall != null)
            {
            opFinallyBlock = new FinallyStart();
            code.add(new GuardAll(opFinallyBlock));
            }

        // try..catch
        CatchStart[] aCatchStart = null;
        if (catches != null)
            {
            int c = catches.size();
            aCatchStart = new CatchStart[c];
            for (int i = 0; i < c; ++i)
                {
                aCatchStart[i] = catches.get(i).ensureCatchStart();
                }

            // single "try" for all of the catches
            code.add(new GuardStart(aCatchStart));
            }

        // the "guarded" body of the using/try statement
        boolean fBlockCompletes = block.completes(ctx, fCompletes, code, errs);

        Label labelGuardEnd = new Label();
        code.add(new GuardEnd(labelGuardEnd));

        // the "catch" blocks
        boolean fAnyCatchCompletes = false;
        if (catches != null)
            {
            int c = catches.size();
            for (int i = 0; i < c; ++i)
                {
                fAnyCatchCompletes |= catches.get(i).completes(ctx, fCompletes, code, errs);
                }
            }

        // the "finally" block
        boolean fFinallyCompletes = true;
        if (catchall != null)
            {
            code.add(opFinallyBlock);
            fFinallyCompletes = catchall.completes(ctx, fCompletes, code, errs);
            code.add(new FinallyEnd());
            }

        if (resources != null)
            {
            // ...
            // FINALLY (e)
            // GUARD
            // # if (x.is(Closeable)) { x.close(); }
            // JMP_NTYPE skip_close
            // NVOK_00 x Closeable.close
            // skip_close: GUARD_E
            // CATCH Exception e_close
            // # if e == null throw e_close
            // JMP_NNULL e skip
            // throw e_close
            // skip_throw: CATCH_E
            // FINALLY_E
            // EXIT
            TypeConstant   typeCloseable = pool().typeCloseable();
            MethodConstant methodClose   = typeCloseable.ensureTypeInfo(errs)
                    .findMethods("close", 0, MethodType.Method).iterator().next();
            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                code.add(aFinallyClose[i]);
                Register regException = code.lastRegister();       // TODO implement this: type "Exception?"

                Register       regCatch  = new Register(pool().typeException());
                StringConstant constName = pool().ensureStringConstant("(close-exception)");
                CatchStart     opCatch   = new CatchStart(regCatch, constName);
                code.add(new GuardStart(new CatchStart[] {opCatch}));

                Argument argResource    = resources.get(i).getLValueExpression().generateArgument(ctx, code, false, false, errs);
                Label    labelSkipClose = new Label("skip_close");
                if (!argResource.getType().isA(typeCloseable))
                    {
                    code.add(new JumpNType(argResource, typeCloseable, labelSkipClose));
                    }
                code.add(new Invoke_00(argResource, methodClose));
                code.add(labelSkipClose);

                code.add(opCatch);
                Label labelSkipThrow   = new Label("skip_throw");
                Label labelFallThrough = new Label();
                code.add(new JumpNotNull(regException, labelSkipThrow));
                code.add(new Throw(regException));
                code.add(new CatchEnd(labelFallThrough));
                code.add(labelFallThrough);
                code.add(new FinallyEnd());
                }

            // no resources remain in scope after the try/using statement
            code.add(new Exit());
            }

        code.add(labelGuardEnd);

        return (fBlockCompletes | fAnyCatchCompletes) & fFinallyCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (resources != null)
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
