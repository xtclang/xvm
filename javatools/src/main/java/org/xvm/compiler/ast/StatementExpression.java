package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.StmtExprAST;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * Statement expression is conceptually similar to a lambda, except that it does not require an
 * actual function, and it behaves as if it is executed at the point in the code where it is
 * encountered. In other words, these two are conceptually quite similar:
 *
 * <code><pre>
 *   x = () -> {2 + 2}();   // note the trailing "call"
 * </pre></code>
 * and:
 * <code><pre>
 *   x = {return 2 + 2;}
 * </pre></code>
 * <p/>
 * To determine the type of the StatementExpression, the one or more required "return" statements
 * need to be analyzed to determine their types.
 *
 * <p/>REVIEW this expression could theoretically support a multi value
 * <p/>REVIEW this expression could theoretically support a conditional return
 * <p/>REVIEW this expression could theoretically calculate to a constant value
 */
public class StatementExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a StatementExpression.
     *
     * @param body  the
     */
    public StatementExpression(StatementBlock body)
        {
        this.body = body;
        }

    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return body.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return body.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- code container methods ----------------------------------------------------------------

    @Override
    public TypeConstant[] getReturnTypes()
        {
        return isValidated() ? getTypes() : m_atypeRequired;
        }

    @Override
    public boolean isReturnConditional()
        {
        // this is called during validation, when we're still trying to figure out what exactly does
        // get returned
        return false;
        }

    @Override
    public void collectReturnTypes(TypeConstant[] atypeRet)
        {
        TypeCollector collector = m_collector;
        if (collector == null)
            {
            m_collector = collector = new TypeCollector(pool());
            }
        collector.add(atypeRet);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean hasSingleValueImpl()
        {
        return false;
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        assert m_atypeRequired == null && m_collector == null;
        if (isValidated())
            {
            return getTypes();
            }

        // clone the body (to avoid damaging the original) and validate it to calculate its type
        StatementBlock blockTempOld = (StatementBlock) body.clone();
        blockTempOld.suppressScope();
        ctx = enterStatementContext(ctx);

        // the resulting returned types come back in the type collector
        StatementBlock blockTempNew = (StatementBlock) blockTempOld.validate(ctx, ErrorListener.BLACKHOLE);
        ctx = ctx.exit();

        // extract the type information (if everything validated ok)
        TypeConstant[] aTypes = null;
        if (blockTempNew != null && m_collector != null)
            {
            aTypes = m_collector.inferMulti(null); // TODO isConditional
            }

        // clean up temporary stuff
        m_collector = null;
        blockTempOld.discard(true);

        return aTypes;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs)
        {
        if (atypeRequired != null && atypeRequired.length == 0)
            {
            return TypeFit.Fit;
            }

        assert m_atypeRequired == null && m_collector == null;
        if (isValidated())
            {
            TypeConstant[] aActualTypes   = getTypes();
            int            cActualTypes   = aActualTypes.length;
            int            cRequiredTypes = atypeRequired.length;
            if (cRequiredTypes > cActualTypes)
                {
                return TypeFit.NoFit;
                }
            for (int i = 0; i < cRequiredTypes; ++i)
                {
                if (!isA(ctx, aActualTypes[i], atypeRequired[i]))
                    {
                    return TypeFit.NoFit;
                    }
                }
            return TypeFit.Fit;
            }

        m_atypeRequired = atypeRequired;

        // clone the body and validate it using the requested type to test if that type will work
        StatementBlock blockTempOld = (StatementBlock) body.clone();
        blockTempOld.suppressScope();
        ctx = enterStatementContext(ctx);

        StatementBlock blockTempNew = (StatementBlock) blockTempOld.validate(ctx, ErrorListener.BLACKHOLE);
        ctx = ctx.exit();

        TypeFit fit = TypeFit.NoFit;
        if (blockTempNew != null && m_collector != null)
            {
            // calculate the resulting type
            TypeConstant[] aActualTypes = m_collector.inferMulti(atypeRequired);  // TODO isConditional
            fit = calcFitMulti(ctx, aActualTypes, atypeRequired);
            }

        // clean up temporary stuff
        m_atypeRequired = null;
        m_collector     = null;
        blockTempOld.discard(true);

        return fit;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        assert m_atypeRequired == null && m_collector == null;
        m_atypeRequired = atypeRequired;

        TypeFit        fit         = TypeFit.Fit;
        TypeConstant[] atypeActual = null;
        StatementBlock bodyOld     = body;

        bodyOld.suppressScope();
        ctx = enterStatementContext(ctx);

        StatementBlock bodyNew = (StatementBlock) bodyOld.validate(ctx, errs);

        ctx = ctx.exit();

        if (bodyNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            body = bodyNew;

            if (m_collector == null)
                {
                if (atypeRequired != null && atypeRequired.length == 0)
                    {
                    // void is a fit
                    atypeActual = atypeRequired;
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.RETURN_REQUIRED);
                    fit = TypeFit.NoFit;
                    }
                }
            else
                {
                atypeActual = m_collector.inferMulti(atypeRequired); // TODO conditional
                m_collector = null;

                if (atypeActual == null)
                    {
                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            atypeRequired == null || atypeRequired.length == 0 ? "void"
                                    : atypeRequired[0].getValueString(), "undefined");
                    fit = TypeFit.NoFit;
                    }
                }
            }

        return finishValidations(ctx, atypeRequired, atypeActual, fit, null, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        m_aLVal = aLVal;
        if (body.completes(ctx, true, code, errs) &&
                m_atypeRequired != null && m_atypeRequired.length > 0)
            {
            errs.log(Severity.ERROR, Compiler.RETURN_REQUIRED, null, getSource(),
                    getEndPosition(), getEndPosition());
            }
        m_astBody = ctx.getHolder().getAst(body);
        }

    @Override
    public ExprAST<Constant> getExprAST()
        {
        return new StmtExprAST<>(m_astBody, getTypes());
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * @return the array of Assignable object representing the L-Value(s) emitted by this
     *         expression
     */
    Assignable[] getAssignables()
        {
        return m_aLVal;
        }

    /**
     * Create a custom statement expression context.
     */
    protected StatementExpressionContext enterStatementContext(Context ctx)
        {
        return new StatementExpressionContext(ctx);
        }

    /**
     * A custom context implementation for statement expression.
     */
    static protected class StatementExpressionContext
            extends Context
        {
        public StatementExpressionContext(Context ctxOuter)
            {
            super(ctxOuter, true);
            }

        @Override
        public Context exit()
            {
            // the "statement expression" is an "inline lambda", that represents a conceptually
            // nested unit of compilation that produces one or more values which become the value(s)
            // of the expression; as a result, the "return" statement inside of the statement
            // expression is simply the means to yield the value(s), and does not exit the
            // containing method (as "return" normally would), so the control flow is simply
            // returned to the enclosing context, and as such, the current point in this context is
            // still considered to be reachable (unlike after a normal method "return")
            setReachable(true);

            return super.exit();
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return body.toString();
        }

    @Override
    public String toDumpString()
        {
        return body.toDumpString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected StatementBlock body;

    private transient TypeConstant[]      m_atypeRequired;
    private transient TypeCollector       m_collector;
    private transient Assignable[]        m_aLVal;
    private transient BinaryAST<Constant> m_astBody;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementExpression.class, "body");
    }