package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo.MethodKind;

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
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try" or "using" statement.
 */
public class TryStatement
        extends Statement
        implements LabelAble
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


    // ----- LabelAble methods ---------------------------------------------------------------------

    @Override
    public boolean hasLabelVar(String sName)
        {
        return sName.equals("exception") &&
                (m_ctxValidatingFinally != null || m_regFinallyException != null);
        }

    @Override
    public Register getLabelVar(String sName)
        {
        assert hasLabelVar(sName);

        Register reg = m_regFinallyException;
        if (reg == null)
            {
            String sLabel = ((LabeledStatement) getParent()).getName();
            Token  tok    = new Token(keyword.getStartPosition(), keyword.getEndPosition(),
                    Id.IDENTIFIER, sLabel + '.' + sName);

            m_regFinallyException = reg = new Register(pool().typeException१());
            m_ctxValidatingFinally.registerVar(tok, reg, m_errsValidatingFinally);
            }

        return reg;
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

        Context ctxOrig  = ctx;
        int     cCatches = catches == null ? 0 : catches.size();

        // validate the "try" block
        ctx = ctxOrig.enter();
        block.validate(ctx, errs);

        if (cCatches > 0)
            {
            // instead of exiting the context, we simply collect all of the impact from the end of the
            // "try" block, together with all of the impact from the end of each "catch" block
            List<Map<String, Assignment>> listAsn    = new ArrayList<>(1 + cCatches);
            boolean                       fReachable = ctx.isReachable();

            listAsn.add(ctx.prepareJump(ctxOrig));
            ctx.discard();

            for (int i = 0; i < cCatches; ++i)
                {
                ctx = ctxOrig.enter();

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

                fReachable |= ctx.isReachable();
                listAsn.add(ctx.prepareJump(ctxOrig));
                ctx.discard();
                }

            // collect the assignment impacts from the end of the "try" block and from the end of each
            // "catch" block
            ctx = ctxOrig;
            if (fReachable)
                {
                for (Map<String, Assignment> mapAsn : listAsn)
                    {
                    ctx.merge(mapAsn);
                    }
                }
            ctx.setReachable(fReachable);
            }

        if (catchall != null)
            {
            // the context for finally clause is a continuation of the context prior to "try"
            m_ctxValidatingFinally  = ctxOrig; // TODO CP defNasn
            m_errsValidatingFinally = errs;
            StatementBlock catchallNew = (StatementBlock) catchall.validate(ctxOrig, errs);
            m_ctxValidatingFinally  = null;
            m_errsValidatingFinally = null;

            if (catchallNew == null)
                {
                fValid = false;
                }
            else
                {
                catchall = catchallNew;
                }
            }

        if (cCatches == 0)
            {
            ctx = ctx.exit();
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
        boolean      fCompletes = fReachable;
        ConstantPool pool       = pool();

        // using() or try()-with-resources
        FinallyStart[] aFinallyClose = null;
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
                FinallyStart opFinally = new FinallyStart(new Register(pool.typeException१()));
                aFinallyClose[i] = opFinally;
                code.add(new GuardAll(opFinally));
                }
            }

        // try..finally
        FinallyStart opFinallyBlock = null;
        Label        labelCatchEnd  = getEndLabel();
        if (catchall != null)
            {
            Register regFinallyException = m_regFinallyException == null
                    ? new Register(pool.typeException१())
                    : m_regFinallyException;
            opFinallyBlock = new FinallyStart(regFinallyException);
            labelCatchEnd  = new Label();
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
        block.suppressScope();
        boolean fBlockCompletes = block.completes(ctx, fCompletes, code, errs);

        // the "catch" blocks
        boolean fAnyCatchCompletes = false;
        if (catches != null)
            {
            code.add(new GuardEnd(labelCatchEnd));

            int c = catches.size();
            for (int i = 0; i < c; ++i)
                {
                CatchStatement stmtCatch = catches.get(i);
                stmtCatch.setCatchLabel(labelCatchEnd);
                fAnyCatchCompletes |= stmtCatch.completes(ctx, fCompletes, code, errs);
                }
            }

        // the "finally" block
        boolean fTryCompletes = fBlockCompletes | fAnyCatchCompletes;
        if (catchall != null)
            {
            // the finally clause gets wrapped in FINALLY / FINALLY_E ops, which imply an enter/exit
            catchall.suppressScope();

            code.add(labelCatchEnd); // the normal flow is to jump to the "FinallyStart" op
            code.add(opFinallyBlock);
            boolean fFinallyCompletes = catchall.completes(ctx, fCompletes, code, errs);

            fTryCompletes &= fFinallyCompletes;

            FinallyEnd opFinallyEnd = new FinallyEnd();
            opFinallyEnd.setCompletes(fTryCompletes);
            code.add(opFinallyEnd);
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
            TypeConstant   typeCloseable = pool.typeCloseable();
            MethodConstant methodClose   = typeCloseable.ensureTypeInfo(errs)
                    .findMethods("close", 0, MethodKind.Method).iterator().next();
            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                code.add(aFinallyClose[i]);
                Register regException = code.lastRegister();

                Register       regCatch  = new Register(pool.typeException());
                StringConstant constName = pool.ensureStringConstant("(close-exception)");
                CatchStart     opCatch   = new CatchStart(regCatch, constName);
                code.add(new GuardStart(new CatchStart[] {opCatch}));

                Argument argResource    = resources.get(i).getLValueExpression()
                                          .generateArgument(ctx, code, false, false, errs);
                Label    labelSkipClose = new Label("skip_close");
                if (!argResource.getType().isA(typeCloseable))
                    {
                    code.add(new JumpNType(argResource, typeCloseable, labelSkipClose));
                    }
                code.add(new Invoke_00(argResource, methodClose));
                code.add(labelSkipClose);
                code.add(new GuardEnd(getEndLabel()));

                code.add(opCatch);
                Label labelSkipThrow   = new Label("skip_throw");
                Label labelFallThrough = new Label();
                code.add(new JumpNotNull(regException, labelSkipThrow));
                code.add(new Throw(regCatch));
                code.add(labelSkipThrow);
                code.add(new CatchEnd(labelFallThrough));
                code.add(labelFallThrough);

                FinallyEnd opFinallyEnd = new FinallyEnd();
                opFinallyEnd.setCompletes(fTryCompletes);
                code.add(opFinallyEnd);
                }

            // no resources remain in scope after the try/using statement
            code.add(new Exit());
            }

        return fTryCompletes;
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

        if (catches != null)
            {
            for (CatchStatement catchone : catches)
                {
                sb.append('\n')
                  .append(catchone);
                }
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

    private transient Context       m_ctxValidatingFinally;
    private transient ErrorListener m_errsValidatingFinally;
    private transient Register      m_regFinallyException;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TryStatement.class,
            "resources", "block", "catches", "catchall");
    }
