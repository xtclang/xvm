package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

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
    public TypeConstant getImplicitType(Context ctx)
        {
        assert m_typeRequired == null && m_listRetTypes == null;
        if (isValidated())
            {
            return getType();
            }

        // clone the body (to avoid damaging the original) and validate it to calculate its type
        StatementBlock blockTemp = (StatementBlock) body.clone();

        // the resulting returned types come back in m_listRetTypes
        if (blockTemp.validate(ctx.createCaptureContext(blockTemp), ErrorListener.BLACKHOLE) == null
                || m_listRetTypes == null)
            {
            return null;
            }

        TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
        m_listRetTypes = null;
        return ListExpression.inferCommonType(pool(), aTypes);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        assert m_typeRequired == null && m_listRetTypes == null;
        if (isValidated())
            {
            return getType().isA(typeRequired)
                    ? TypeFit.Fit
                    : TypeFit.NoFit;
            }

        // clone the body and validate it using the requested type to test if that type will work
        m_typeRequired = typeRequired;
        ((StatementBlock) body.clone()).validate(ctx.createCaptureContext(body), ErrorListener.BLACKHOLE);
        m_typeRequired = null;

        // the resulting returned types come back in m_listRetTypes
        if (m_listRetTypes == null)
            {
            return TypeFit.NoFit;
            }
        else
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;

            // calculate the resulting type
            TypeConstant typeResult = ListExpression.inferCommonType(pool(), aTypes);
            return typeResult != null && typeResult.isA(typeRequired)
                    ? TypeFit.Fit
                    : TypeFit.NoFit;
            }
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        assert m_typeRequired == null && m_listRetTypes == null;
        m_typeRequired = typeRequired;

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

        TypeConstant typeActual = null;
        if (m_listRetTypes != null)
            {
            TypeConstant[] aTypes = m_listRetTypes.toArray(new TypeConstant[m_listRetTypes.size()]);
            m_listRetTypes = null;
            typeActual = ListExpression.inferCommonType(pool(), aTypes);
            }
        if (typeActual == null)
            {
            fit = TypeFit.NoFit;
            }

        return finishValidation(typeRequired, typeActual, fit, null, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        m_LVal = LVal;
        if (body.completes(ctx, true, code, errs))
            {
            errs.log(Severity.ERROR, org.xvm.compiler.Compiler.RETURN_REQUIRED, null, getSource(),
                    getEndPosition(), getEndPosition());
            }
        }


    // ----- compilation helpers -------------------------------------------------------------------

    /**
     * @return the required type, which is the specified required type during validation, or the
     *         actual type once the expression is validatd
     */
    TypeConstant getRequiredType()
        {
        return isValidated() ? getType() : m_typeRequired;
        }

    void addReturnType(TypeConstant typeRet)
        {
        List<TypeConstant> list = m_listRetTypes;
        if (list == null)
            {
            m_listRetTypes = list = new ArrayList<>();
            }
        list.add(typeRet);
        }

    Assignable getAssignable()
        {
        return m_LVal;
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

    private transient TypeConstant       m_typeRequired;
    private transient List<TypeConstant> m_listRetTypes;
    private transient Assignable         m_LVal;

    private static final Field[] CHILD_FIELDS = fieldsForNames(StatementExpression.class, "body");
    }
