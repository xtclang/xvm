package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.RegAllocAST;
import org.xvm.asm.ast.StmtBlockAST;
import org.xvm.asm.ast.TryCatchStmtAST;

import org.xvm.asm.ast.TryFinallyStmtAST;
import org.xvm.asm.constants.FormalConstant;
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
import org.xvm.asm.op.Invoke_10;
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

    public TryStatement(Token keyword, List<AssignmentStatement> resources, StatementBlock block,
                        List<CatchStatement> catches, StatementBlock catchall)
        {
        assert block != null;

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
    public Register getLabelVar(Context ctx, String sName)
        {
        assert hasLabelVar(sName);

        Register reg = m_regFinallyException;
        if (reg == null)
            {
            String sLabel   = ((LabeledStatement) getParent()).getName();
            String sRegName = sLabel + '.' + sName;
            Token  tok      = new Token(keyword.getStartPosition(), keyword.getEndPosition(),
                    Id.IDENTIFIER, sRegName);

            m_regFinallyException = reg = ctx.createRegister(pool().typeException१(), sRegName);
            m_ctxValidatingFinally.registerVar(tok, reg, m_errsValidatingFinally);
            }

        return reg;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        if (resources == null)
            {
            if (catches == null && catchall == null)
                {
                log(errs, Severity.ERROR, Compiler.TRY_WITHOUT_CATCH);
                fValid = false;
                }
            }
        else
            {
            // the using/try-with-resources section provides a context to the rest of the
            // statement (it is the outermost layer of the "onion")
            ctx = ctx.enter();

            for (int i = 0, c = resources.size(); i < c; ++i)
                {
                AssignmentStatement useOld = resources.get(i);
                AssignmentStatement useNew = (AssignmentStatement) useOld.validate(ctx, errs);
                if (useNew == null)
                    {
                    fValid = false;
                    }
                else if (useNew != useOld)
                    {
                    resources.set(i, useNew);
                    }

                // each using/try-with-resources is a nested try-finally
                ctx = ctx.enter();
                }
            }

        Context ctxOrig     = ctx;
        Context ctxCatchAll = null;
        int     cCatches    = catches == null ? 0 : catches.size();

        // validate the "try" block
        TryContext ctxTryBlock = new TryContext(ctxOrig);
        block.validate(ctxTryBlock, errs);

        Map<String, Assignment> mapTryAsn = ctxTryBlock.prepareJump(ctxOrig);
        if (catchall != null)
            {
            ctxCatchAll = ctxOrig.enter();
            ctxCatchAll.merge(mapTryAsn);
            ctxCatchAll.setReachable(true);
            }

        if (cCatches > 0)
            {
            // now we combine the impact from the end of the "try" block with the impacts from
            // the end of each "catch" block, which is semantically the same as the following "if"
            // ladder:
            //
            //    catch (e1)          if (e1())
            //        {                   {
            //        ...                 ...
            //        }                   }
            //    catch (e2)          else if (e2())
            //        {                   {
            //        ...                 ...
            //        }                   }
            //                        else
            //                            {
            //                            // unreachable else clause
            //                            throw ...
            //                            }
            // the only difference is that the top context is an extension of the IfContext that
            // prevents any narrowing assumptions from "catch" scopes percolating up to the original
            // context

            Context ctxNext = new Context.IfContext(ctxOrig)
                {
                @Override
                protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
                    {
                    }

                @Override
                protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant type,
                                                          Branch branch)
                    {
                    }
                };

            boolean fReachable = ctxTryBlock.isReachable();
            if (fReachable)
                {
                ctxNext.merge(mapTryAsn);
                ctxNext.setReachable(true);
                }

            for (int i = 0; i < cCatches; ++i)
                {
                Context ctxCatch = ctxNext.enterFork(true);

                CatchStatement catchOld = catches.get(i);
                CatchStatement catchNew = (CatchStatement) catchOld.validate(ctxCatch, errs);

                fReachable |= ctxCatch.isReachable();
                ctxNext     = ctxCatch.exit();

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

                ctxNext = ctxNext.enterFork(false);
                if (i == cCatches - 1)
                    {
                    // the last one - mark unreachable and exit all the way up
                    ctxNext.setReachable(false);
                    while (i-- >= 0)
                        {
                        // exit enterFork(false); exit enterIf();
                        ctxNext = ctxNext.exit().exit();
                        }
                    break;
                    }
                else
                    {
                    ctxNext = ctxNext.enterIf();
                    }
                }

            assert ctxNext == ctxOrig;
            if (ctxTryBlock.isReachable())
                {
                ctxTryBlock.exitReachable();
                }
            else
                {
                ctxTryBlock.discard();
                }

            ctxOrig.setReachable(fReachable);
            }
        else
            {
            ctx = ctxTryBlock.exit();
            assert ctx == ctxOrig;
            }

        if (catchall != null)
            {
            // the context for finally clause is a continuation of the context prior to "try"
            // plus the "worst case scenario" of the try-catch block, but we only need to
            // promote the information gathered by the finally block
            Context ctxFinally = ctxCatchAll.enter();

            m_ctxValidatingFinally  = ctxFinally;
            m_errsValidatingFinally = errs;
            StatementBlock catchallNew = (StatementBlock) catchall.validate(ctxFinally, errs);
            m_ctxValidatingFinally  = null;
            m_errsValidatingFinally = null;

            ctxFinally.promoteAssignments(ctxOrig);

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
        boolean      fCompletes = fReachable;
        ConstantPool pool       = pool();
        AstHolder    holder     = ctx.getHolder();

        ExprAST<Constant>[]   aAstResources = null;
        BinaryAST<Constant>[] aAstCatches   = null; // TODO GG what is this? the "catch()" or the "{...}" body thereof? because we need BOTH
        BinaryAST<Constant>   astCatchAll   = null;
        BinaryAST<Constant>   astBlock;

        // using() or try()-with-resources
        FinallyStart[] aFinallyClose = null;
        if (resources != null)
            {
            // the first resource is declared outside of any try/finally block, but it is not
            // visible beyond this statement
            code.add(new Enter());

            int c = resources.size();
            aFinallyClose = new FinallyStart[c];
            aAstResources = new ExprAST[c];
            for (int i = 0; i < c; ++i)
                {
                Statement stmt = resources.get(i);
                fCompletes = stmt.completes(ctx, fCompletes, code, errs);
                aAstResources[i] = (ExprAST<Constant>) holder.getAst(stmt);

                FinallyStart opFinally = new FinallyStart(code.createRegister(pool.typeException१()));
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
                    ? code.createRegister(pool.typeException१())
                    : m_regFinallyException;
            opFinallyBlock = new FinallyStart(regFinallyException);
            labelCatchEnd  = new Label();
            code.add(new GuardAll(opFinallyBlock));
            }

        // try..catch
        RegAllocAST<Constant>[] aAllocCatch = null;
        if (catches != null)
            {
            int          cCatches    = catches.size();
            CatchStart[] aCatchStart = new CatchStart[cCatches];

            aAllocCatch = new RegAllocAST[cCatches];
            for (int i = 0; i < cCatches; ++i)
                {
                CatchStatement stmt = catches.get(i);
                aCatchStart[i] = stmt.ensureCatchStart();
                aAllocCatch[i] = stmt.getCatchRegister().getRegAllocAST();
                }

            // single "try" for all of the catches
            code.add(new GuardStart(aCatchStart));
            }

        // the "guarded" body of the using/try statement
        block.suppressScope();
        boolean fBlockCompletes = block.completes(ctx, fCompletes, code, errs);

        astBlock = holder.getAst(block);

        // the "catch" blocks
        boolean fAnyCatchCompletes = false;
        if (catches != null)
            {
            code.add(new GuardEnd(labelCatchEnd));

            int c = catches.size();
            aAstCatches = new BinaryAST[c];
            for (int i = 0; i < c; ++i)
                {
                CatchStatement stmtCatch = catches.get(i);
                stmtCatch.setCatchLabel(labelCatchEnd);
                fAnyCatchCompletes |= stmtCatch.completes(ctx, fCompletes, code, errs);

                aAstCatches[i] = new StmtBlockAST<>(
                    new BinaryAST[] {aAllocCatch[i], holder.getAst(stmtCatch)}, true);
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

            astCatchAll = holder.getAst(catchall);

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
            // NVOK_10 x Closeable.close
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
                    .findMethods("close", 1, MethodKind.Method).iterator().next();
            for (int i = resources.size() - 1; i >= 0; --i)
                {
                code.add(aFinallyClose[i]);
                Register regException = code.lastRegister();

                Register       regCatch  = code.createRegister(pool.typeException());
                StringConstant constName = pool.ensureStringConstant("(close-exception)");
                CatchStart     opCatch   = new CatchStart(regCatch, constName);
                code.add(new GuardStart(new CatchStart[] {opCatch}));

                Argument argResource      = resources.get(i).getLValueExpression()
                                          .generateArgument(ctx, code, false, false, errs);
                Label    labelSkipClose   = new Label("skip_close");
                Label    labelFallThrough = new Label();
                if (!argResource.getType().isA(typeCloseable))
                    {
                    code.add(new JumpNType(argResource, typeCloseable, labelSkipClose));
                    }
                code.add(new Invoke_10(argResource, methodClose, regException));
                code.add(labelSkipClose);
                code.add(new GuardEnd(labelFallThrough));

                code.add(opCatch);
                Label labelSkipThrow = new Label("skip_throw");
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

        holder.setAst(this, astCatchAll == null
                ? new TryCatchStmtAST<>(aAstResources, astBlock, aAstCatches)
                : new TryFinallyStmtAST<>(aAstResources, astBlock, aAstCatches,
                        m_regFinallyException == null ? null : m_regFinallyException.getRegAllocAST(),
                        astCatchAll));
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
            for (CatchStatement catchOne : catches)
                {
                sb.append('\n')
                  .append(catchOne);
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


    // ----- inner classes -------------------------------------------------------------------------

    /**
     * A custom "try" block context.
     */
    static protected class TryContext
            extends Context
        {
        protected TryContext(Context ctxOuter)
            {
            super(ctxOuter, true);
            }

        /**
         * Merge the information from this context into the outer when it's reachable.
         */
        protected void exitReachable()
            {
            Context ctxOuter = getOuterContext();
            ctxOuter.merge(getDefiniteAssignments());
            ctxOuter.setReachable(true);

            promoteNarrowedTypes();
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            if (branch == Branch.Always && !isVarDeclaredInThisScope(sName))
                {
                Context  ctxOuter = getOuterContext();
                Argument argOrig  = ctxOuter.getVar(sName);
                assert argOrig != null;

                TypeConstant typeArg  = arg.getType();
                TypeConstant typeOrig = argOrig.getType();
                if (!typeArg.isA(typeOrig))
                    {
                    // this can only happen if the original type was already a shadow
                    assert argOrig instanceof Register;

                    ctxOuter.replaceArgument(sName, branch, ((Register) argOrig).restoreType());
                    }
                }
            }
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