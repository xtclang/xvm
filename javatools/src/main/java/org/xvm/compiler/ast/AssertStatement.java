package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.AssertM;
import org.xvm.asm.op.AssertV;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNCond;
import org.xvm.asm.op.JumpNFirst;
import org.xvm.asm.op.JumpNSample;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.New_N;
import org.xvm.asm.op.Throw;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * An assert statement.
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, Expression exprInterval, List<AstNode> conds, Expression exprMsg, long lEndPos)
        {
        switch (keyword.getId())
            {
            case ASSERT:
            case ASSERT_RND:
            case ASSERT_ARG:
            case ASSERT_BOUNDS:
            case ASSERT_TODO:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                break;

            default:
                throw new IllegalArgumentException("keyword=" + keyword);
            }

        this.keyword  = keyword;
        this.interval = exprInterval;
        this.conds    = conds == null ? Collections.emptyList() : conds;
        this.message  = exprMsg;
        this.lEndPos  = lEndPos;
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
        return lEndPos;
        }

    /**
     * @return the number of conditions
     */
    public int getConditionCount()
        {
        return conds.size();
        }

    /**
     * @param i  a value between 0 and {@link #getConditionCount()}-1
     *
     * @return the condition, which is either an Expression or an AssignmentStatement
     */
    public AstNode getCondition(int i)
        {
        return conds.get(i);
        }

    /**
     * @param nodeChild  an expression (or AssignmentStatement) that is a child of this statement
     *
     * @return the index of the expression in the list of conditions within this statement, or -1
     */
    public int findCondition(AstNode nodeChild)
        {
        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            if (conds.get(i) == nodeChild)
                {
                return i;
                }
            }
        return -1;
        }

    /**
     * @return true iff the assertion occurs explicitly within conditional "debug" mode
     */
    public boolean isDebugOnly()
        {
        return keyword.getId() == Id.ASSERT_DBG;
        }

    /**
     * @return true iff the assertion occurs explicitly within conditional "test" mode
     */
    public boolean isTestOnly()
        {
        return keyword.getId() == Id.ASSERT_TEST;
        }

    /**
     * @return true iff the assertion occurs explicitly within a conditional mode
     */
    public boolean isLinktimeConditional()
        {
        return isDebugOnly() | isTestOnly();
        }

    /**
     * @return true iff the assertion is executed only the first time that the execution reaches it
     */
    public boolean isOnlyOnce()
        {
        return keyword.getId() == Id.ASSERT_ONCE;
        }

    /**
     * @return true iff the assertion is executed as if it were statistically sampling
     */
    public boolean isSampling()
        {
        return keyword.getId() == Id.ASSERT_RND;
        }

    /**
     * @return true iff the assertion does not occur each time the execution reaches it
     */
    public boolean isNotAlways()
        {
        return isOnlyOnce() | isSampling();
        }

    /**
     * @return true iff the assertion is not skipped either by sampling, only-once execution, or by
     *         being discardable at link-time
     */
    public boolean alwaysEvaluated()
        {
        return !isNotAlways() && !isLinktimeConditional();
        }

    /**
     * @return the inverse rate of assertion evaluation; for example "5" means (on average) 1/5 of
     *         the time
     */
    public Expression getSampleInterval()
        {
        return interval;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return true;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // break apart complex conditions if possible; this allows the auto-generated messages to be
        // more precise in the state that they select to display
        if (message == null)
            {
            demorgan();
            }

        if (interval != null)
            {
            Expression exprNew = interval.validate(ctx, pool().typeInt(), errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                interval = exprNew;
                if (!interval.isRuntimeConstant())
                    {
                    interval.log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                    }
                }
            }

        Expression            exprMessage    = message;
        Map<String, Argument> mapMessageArgs = null;

        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            AstNode cond = getCondition(i);
            // the condition is either a boolean expression or an assignment statement whose R-value
            // is a multi-value with the first value being a boolean
            if (cond instanceof AssignmentStatement stmtOld)
                {
                if (stmtOld.isNegated())
                    {
                    ctx = ctx.enterNot();
                    }

                AssignmentStatement stmtNew = (AssignmentStatement) stmtOld.validate(ctx, errs);

                if (stmtOld.isNegated())
                    {
                    ctx = ctx.exit();
                    }

                if (stmtNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (stmtNew != stmtOld)
                        {
                        cond = stmtNew;
                        conds.set(i, cond);
                        }
                    }
                }
            else if (cond instanceof Expression exprOld)
                {
                ctx = new AssertContext(ctx);

                Expression exprNew = exprOld.validate(ctx, pool().typeBoolean(), errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        cond = exprNew;
                        conds.set(i, cond);
                        }

                    if (exprNew.isConstantFalse() && alwaysEvaluated())
                        {
                        ctx.setReachable(false);
                        }
                    }

                if (exprMessage != null)
                    {
                    mapMessageArgs = ((AssertContext) ctx).mergeMessageContext(mapMessageArgs);
                    }
                ctx = ctx.exit();
                }
            }

        if (exprMessage != null)
            {
            Context ctxMsg = ctx.enter();
            if (mapMessageArgs != null)
                {
                for (Map.Entry<String, Argument> entry : mapMessageArgs.entrySet())
                    {
                    ctxMsg.replaceArgument(entry.getKey(), Context.Branch.Always, entry.getValue());
                    }
                }

            Expression exprNew = exprMessage.validate(ctxMsg, pool().typeString(), errs);
            if (exprNew != exprMessage)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    message = exprNew;
                    }
                }
            ctxMsg.discard();
            }

        // empty condition is like having a single "False" condition
        if (conds.isEmpty() && alwaysEvaluated())
            {
            ctx.setReachable(false);
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool pool = pool();

        if (isLinktimeConditional())
            {
            // for "assert:debug", the assertion only is evaluated if the "debug" named condition
            // exists; similarly, for "assert:test", it is evaluated only if "test" is defined
            String sCond = isDebugOnly() ? "debug" : "test";
            code.add(new JumpNCond(pool.ensureNamedCondition(sCond), getEndLabel()));
            }

        if (isNotAlways())
            {
            code.add(isOnlyOnce()
                    ? new JumpNFirst(getEndLabel())
                    : new JumpNSample(interval.generateArgument(ctx, code, true, true, errs), getEndLabel()));
            }

        String sThrow = switch (keyword.getId())
            {
            default                                   -> "IllegalState"; // ASSERT
            case ASSERT_ARG                           -> "IllegalArgument";
            case ASSERT_BOUNDS                        -> "OutOfBounds";
            case ASSERT_TODO                          -> "UnsupportedOperation";
            case ASSERT_ONCE, ASSERT_RND, ASSERT_TEST -> "Assertion";
            case ASSERT_DBG                           -> null;
            };

        ClassConstant  constEx  = sThrow == null ? null : pool.ensureEcstasyClassConstant(sThrow);
        MethodConstant constNew = sThrow == null ? null : findExceptionConstructor(pool, sThrow, errs);

        int cConds = getConditionCount();
        if (cConds == 0)
            {
            if (message == null || isDebugOnly())
                {
                code.add(new Assert(pool.valFalse(), constNew));
                }
            else
                {
                assert constNew != null;

                // throw new {sThrow}(message, null)
                Argument argEx  = code.createRegister(constEx.getType());
                Argument argMsg = message.generateArgument(ctx, code, true, true, errs);
                code.add(new New_N(constNew, new Argument[]{argMsg, pool.valNull()}, argEx));
                code.add(new Throw(argEx));
                }

            return !alwaysEvaluated();
            }

        boolean fCompletes   = fReachable;
        Label   labelMessage = new Label("CustomMessage");
        for (int i = 0; i < cConds; ++i)
            {
            AstNode cond = getCondition(i);
            StringConstant constText = null;
            Map<String, Expression> mapTrace = null;
            if (message == null)
                {
                String sCond = m_listTexts.get(i);
                int cTrace = 0;

                // add traces (if anything is interesting to trace)
                mapTrace = new ListMap<>();
                cond.selectTraceableExpressions(mapTrace);
                if (!mapTrace.isEmpty())
                    {
                    StringBuilder sb = new StringBuilder()
                            .append('"')
                            .append(sCond)
                            .append('"');
                    boolean fFirst = true;
                    for (Map.Entry<String, Expression> entry : mapTrace.entrySet())
                        {
                        Expression expr = entry.getValue();
                        expr.requireTrace();

                        sb.append(fFirst ? ": " : ", ")
                          .append(entry.getKey())
                          .append('=');
                        fFirst = false;

                        TypeConstant[] aTypes = expr.getTypes();
                        int cTypes = aTypes.length;
                        if (cTypes != 1)
                            {
                            sb.append('(');
                            }

                        for (int iType = 0; iType < cTypes; ++iType)
                            {
                            if (iType > 0)
                                {
                                sb.append(", ");
                                }

                            sb.append('{')
                              .append(cTrace++)
                              .append('}');
                            }

                        if (cTypes != 1)
                            {
                            sb.append(')');
                            }
                        }
                    sCond = sb.toString();
                    }
                constText = pool.ensureStringConstant(sCond);

                // it is possible that the condition was modified by the addition of traces
                cond = getCondition(i);
                }

            Argument argCond;
            boolean  fNegated = false;
            if (cond instanceof AssignmentStatement stmtCond)
                {
                fNegated    = stmtCond.isNegated();
                fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);
                argCond = stmtCond.getConditionRegister();
                }
            else
                {
                Expression exprCond = (Expression) cond;

                // "assert False" always asserts
                if (exprCond.isConstantFalse())
                    {
                    code.add(message == null
                            ? new Assert(pool.valFalse(), constNew)
                            : new Jump(labelMessage));
                    fCompletes = false;
                    continue;
                    }

                // "assert True" is a no-op
                if (exprCond.isConstantTrue())
                    {
                    continue;
                    }

                fCompletes &= exprCond.isCompletable();
                argCond = exprCond.generateArgument(ctx, code, true, true, errs);
                }

            if (message == null)
                {
                if (mapTrace.isEmpty())
                    {
                    code.add(new AssertM(argCond, constNew, constText));
                    }
                else
                    {
                    List<Argument> argV = new ArrayList<>();
                    for (Expression expr : mapTrace.values())
                        {
                        Argument[] aArgs = ((TraceExpression) expr.getParent()).getArguments();
                        Collections.addAll(argV, aArgs);
                        }
                    code.add(new AssertV(
                            argCond, constNew, constText, argV.toArray(Expression.NO_RVALUES)));
                    }
                }
            else
                {
                if (i == cConds - 1)
                    {
                    // last one, so get out of the assertion if everything was true
                    code.add(fNegated
                        ? new JumpFalse(argCond, getEndLabel())
                        : new JumpTrue(argCond, getEndLabel()));
                    }
                else
                    {
                    // in the middle, so check for the current condition to have failed
                    code.add(fNegated
                            ? new JumpTrue(argCond, labelMessage)
                            : new JumpFalse(argCond, labelMessage));
                    }
                }
            }

        if (message != null)
            {
            // throw new {sThrow}(message, null)
            code.add(labelMessage);
            Argument argEx  = code.createRegister(constEx.getType());
            Argument argMsg = message.generateArgument(ctx, code, true, true, errs);
            code.add(new New_N(constNew, new Argument[]{argMsg, pool.valNull()}, argEx));
            code.add(new Throw(argEx));
            }

        return fCompletes;
        }

    /**
     * Obtain the "takes one parameter, a String message" constructor for the specified Ecstasy
     * exception class.
     *
     * @param pool   the ConstantPool
     * @param sName  the name of the Exception class
     * @param errs   the ErrorListener to log to
     *
     * @return the desired constructor MethodConstant
     */
    public static MethodConstant findExceptionConstructor(ConstantPool pool, String sName, ErrorListener errs)
        {
        return pool.ensureEcstasyTypeConstant(sName).ensureTypeInfo(errs).findConstructor(null);
        }

    /**
     * Re-arrange the conditions, if possible, to split them into smaller chunks by applying the
     * rules of De Morgan.
     */
    protected void demorgan()
        {
        if (!conds.isEmpty())
            {
            List<AstNode> listOldConds = conds;
            conds       = new ArrayList<>(conds.size());
            m_listTexts = new ArrayList<>(conds.size());
            for (AstNode cond : listOldConds)
                {
                demorgan(cond);
                }
            }
        }

    private void demorgan(AstNode cond)
        {
        String sCond = Handy.appendString(new StringBuilder(),
                cond.getSource().toString(cond.getStartPosition(), cond.getEndPosition()))
                .toString();

        if (cond instanceof UnaryComplementExpression exprNot)
            {
            // demorgan
            Expression exprSub = exprNot.expr;
            if (exprSub instanceof BiExpression exprOr
                    && exprOr.operator.getId() == Id.COND_OR)
                {
                UnaryComplementExpression exprNot2 = (UnaryComplementExpression) exprNot.clone();

                exprNot .expr = exprOr.expr1;
                exprNot2.expr = exprOr.expr2;
                demorgan(exprNot);
                demorgan(exprNot2);
                exprOr.discard(false);
                return;
                }
            }
        else if (cond instanceof BiExpression exprAnd
                    && exprAnd.operator.getId() == Id.COND_AND)
            {
            demorgan(exprAnd.expr1);
            demorgan(exprAnd.expr2);
            exprAnd.discard(false);
            return;
            }

        conds.add(cond);
        m_listTexts.add(sCond);
        }

    /**
     * A custom context implementation to provide type-narrowing as a natural side-effect of an
     * assertion.
     */
    static class AssertContext
            extends Context
        {
        public AssertContext(Context outer)
            {
            super(outer, true);
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            return asnInner.whenTrue();
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            super.promoteNarrowedType(sName, arg, branch);

            // promote our "true" into the parent's "always" branch
            if (branch == Branch.WhenTrue)
                {
                getOuterContext().replaceArgument(sName, Branch.Always, arg);
                }
            }

        @Override
        protected void promoteNarrowedGenericType(FormalConstant constFormal, TypeConstant typeNarrowed,
                                                  Branch branch)
            {
            super.promoteNarrowedGenericType(constFormal, typeNarrowed, branch);

            // promote our "true" into the parent's "always" branch
            if (branch == Branch.WhenTrue)
                {
                getOuterContext().replaceGenericType(constFormal, Branch.Always, typeNarrowed);
                }
            }

        /**
         * Collect the narrowed arguments from the "else" branch of the assert expression and merge
         * them with the previously collected map of narrowed arguments.
         *
         * @param map  (optional) map of previously collected arguments
         *
         * @return the merged map
         */
        protected Map<String, Argument> mergeMessageContext(Map<String, Argument> map)
            {
            Map<String, Argument> mapThis = getNarrowingMap(false);
            if (map == null || map.isEmpty())
                {
                return mapThis.isEmpty() ? null : new HashMap<>(mapThis);
                }

            map.keySet().retainAll(mapThis.keySet()); // only keep common arguments
            for (Map.Entry<String, Argument> entry : map.entrySet())
                {
                String   sName   = entry.getKey();
                Argument argPrev = entry.getValue();
                Argument argThis = mapThis.get(sName);

                TypeConstant typePrev = argPrev.getType();
                TypeConstant typeThis = argThis.getType();

                TypeConstant typeJoin = typeThis.union(pool(), typePrev);
                if (!typeJoin.equals(typePrev))
                    {
                    if (argPrev instanceof Register regPrev)
                        {
                        map.put(sName, regPrev.narrowType(typeJoin));
                        }
                    else
                        {
                        map.remove(sName);
                        }
                    }
                }
            return map;
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);
        if (interval != null)
            {
            sb.append('(')
              .append(interval)
              .append(')');
            }

        if (!conds.isEmpty())
            {
            sb.append(' ')
              .append(conds.get(0));
            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
                }
            }

        sb.append(';');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected Expression    interval;
    protected List<AstNode> conds;
    protected Expression    message;
    protected long          lEndPos;

    private List<String> m_listTexts;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssertStatement.class, "interval", "conds", "message");
    }