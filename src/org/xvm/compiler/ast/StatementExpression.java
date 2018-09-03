package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.ast.Statement.Context;

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
        StatementBlock blockTemp = (StatementBlock) body.clone();

        // the resulting returned types come back in the type collector
        ctx       = ctx.enterCapture(blockTemp, null, null);
        blockTemp = (StatementBlock) blockTemp.validate(ctx, ErrorListener.BLACKHOLE);
        ctx       = ctx.exitScope();

        if (blockTemp == null || m_collector == null)
            {
            m_collector = null;
            return null;
            }

        TypeConstant[] aTypes = m_collector.inferMulti(); // TODO isConditional
        m_collector = null;
        return aTypes;
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
        {
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
                if (!aActualTypes[i].isA(atypeRequired[i]))
                    {
                    return TypeFit.NoFit;
                    }
                }
            return TypeFit.Fit;
            }

        // clone the body and validate it using the requested type to test if that type will work
        m_atypeRequired = atypeRequired;
        ctx = ctx.enterCapture(body, null, null);
        ((StatementBlock) body.clone()).validate(ctx, ErrorListener.BLACKHOLE);
        ctx = ctx.exitScope();
        m_atypeRequired = null;

        // the resulting returned types come back in m_listRetTypes
        if (m_collector == null)
            {
            return TypeFit.NoFit;
            }
        else
            {
            // calculate the resulting type
            TypeConstant[] aActualTypes = m_collector.inferMulti();  // TODO isConditional
            m_collector = null;
            return calcFitMulti(ctx, aActualTypes, atypeRequired);
            }
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        assert m_atypeRequired == null && m_collector == null;
        m_atypeRequired = atypeRequired;

        TypeFit        fit     = TypeFit.Fit;
        StatementBlock bodyOld = body;
        StatementBlock bodyNew = (StatementBlock) bodyOld.validate(ctx, errs);
        if (bodyNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            body = bodyNew;
            }

        TypeConstant[] atypeActual = null;
        if (m_collector != null)
            {
            atypeActual = m_collector.inferMulti(); // TODO conditional
            m_collector = null;
            }
        if (atypeActual == null)
            {
            fit = TypeFit.NoFit;
            }

        return finishValidations(atypeRequired, atypeActual, fit, null, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        m_aLVal = aLVal;
        if (body.completes(ctx, true, code, errs))
            {
            errs.log(Severity.ERROR, org.xvm.compiler.Compiler.RETURN_REQUIRED, null, getSource(),
                    getEndPosition(), getEndPosition());
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    @Override
    public TypeConstant[] getRequiredTypes()
        {
        return isValidated() ? getTypes() : m_atypeRequired;
        }

    @Override
    public void addReturnTypes(TypeConstant[] atypeRet)
        {
        TypeCollector collector = m_collector;
        if (collector == null)
            {
            m_collector = collector = new TypeCollector();
            }
        collector.add(atypeRet);
        }

    Assignable[] getAssignables()
        {
        return m_aLVal;
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
