package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.compiler.ast.LambdaExpression.LambdaContext;

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

        // the resulting returned types come back in the type collector
        ctx = enterCapture(ctx, blockTempOld);
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
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
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

        // clone the body and validate it using the requested type to test if that type will work
        StatementBlock blockTempOld = (StatementBlock) body.clone();
        ctx = enterCapture(ctx, blockTempOld);

        m_atypeRequired = atypeRequired;
        StatementBlock blockTempNew = (StatementBlock) blockTempOld.validate(ctx, ErrorListener.BLACKHOLE);
        ctx = ctx.exit();

        TypeFit fit = TypeFit.NoFit;
        if (blockTempNew != null && m_collector != null)
            {
            // calculate the resulting type
            TypeConstant[] aActualTypes = m_collector.inferMulti(atypeRequired);  // TODO isConditional
            fit = calcFitMulti(ctx, aActualTypes, atypeRequired);
            }

        // clean up temprary stuff
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
        StatementBlock bodyNew     = (StatementBlock) bodyOld.validate(ctx, errs);
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
        if (body.completes(ctx, true, code, errs))
            {
            errs.log(Severity.ERROR, Compiler.RETURN_REQUIRED, null, getSource(),
                    getEndPosition(), getEndPosition());
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * TODO
     *
     * @return
     */
    Assignable[] getAssignables()
        {
        return m_aLVal;
        }

    /**
     * Create a context that bridges from the current context into a special compilation mode in
     * which the values (or references / variables) of the outer context can be <i>captured</i>.
     *
     * @param ctx   the current (soon to be outer) context
     * @param body  the StatementBlock of the lambda, anonymous inner class, or statement expression
     *
     * @return a capturing context
     */
    protected LambdaContext enterCapture(Context ctx, StatementBlock body)
        {
        // TODO don't use LambdaContext? use a similar class specific to the StatementExpression?
        return LambdaExpression.enterCapture(ctx, body, null, null);
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

    private transient TypeConstant[] m_atypeRequired;
    private transient TypeCollector  m_collector;
    private transient Assignable[]   m_aLVal;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementExpression.class, "body");
    }
