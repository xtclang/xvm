package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Expression.Assignable;

import org.xvm.util.Severity;


/**
 * An assignment statement specifies an l-value, an assignment operator, and an r-value.
 *
 * Additionally, this can represent the assignment portion of a "conditional declaration".
 */
public class AssignmentStatement
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssignmentStatement(AstNode lvalue, Token op, Expression rvalue)
        {
        this(lvalue, op, rvalue, true);
        }

    public AssignmentStatement(AstNode lvalue, Token op, Expression rvalue, boolean standalone)
        {
        this.lvalue = lvalue;
        this.op     = op;
        this.rvalue = rvalue;
        this.cond   = op.getId() == Token.Id.COLON;
        this.term   = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the assignment statement uses the "=" operator and is a single assignment
     */
    public boolean isSimple()
        {
        return lvalue.isSingle() && op.getId() == Token.Id.ASN;
        }

    /**
     * @return true iff the assignment statement uses the ":" operator
     */
    public boolean isConditional()
        {
        return op.getId() == Token.Id.COLON;
        }

    public Expression getLValue()
        {
        return lvalue;
        }

    public Expression getRValue()
        {
        return rvalue;
        }

    @Override
    public long getStartPosition()
        {
        return lvalue.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return rvalue.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- ConditionalStatement methods ----------------------------------------------------------

    @Override
    protected void split(Context ctx, ErrorListener errs)
        {
        // there's no declaration portion, so just use a "no-op" for that statement
        long      lPos    = getStartPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(stmtNOP, this, errs);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean isRValue(Expression exprChild)
        {
        return exprChild != lvalue;
        }

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // REVIEW does this have to support multiple assignment? (I think that it does...)

        Expression lvalueNew = lvalue.validate(ctx, null, errs);
        if (lvalueNew != lvalue)
            {
            fValid &= lvalueNew != null;
            if (lvalueNew != null)
                {
                lvalue = lvalueNew;
                }
            }

        // provide the l-value's type to the r-value so that it can "infer" its type as necessary,
        // and can validate that assignment can occur
        TypeConstant typeLeft = lvalue.getType();
        boolean      fInfer   = typeLeft != null;
        if (fInfer)
            {
            // allow the r-value to resolve names based on the l-value type's contributions
            ctx = ctx.enterInferring(typeLeft);
            }

        Expression rvalueNew = isConditional()
            ? rvalue.validateMulti(ctx, new TypeConstant[] {pool().typeBoolean(), typeLeft}, errs)
            : rvalue.validate(ctx, typeLeft, errs);
        if (rvalueNew != rvalue)
            {
            fValid &= rvalueNew != null;
            if (rvalueNew != null)
                {
                rvalue = rvalueNew;
                }
            }

        if (fInfer)
            {
            ctx = ctx.exitScope();
            }

        if (lvalue.isVoid())
            {
            lvalue.log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY,
                    Math.max(1, rvalue.getValueCount()), 0);
            }
        else
            {
            int cValues = lvalue.getValueCount();
            if (isConditional())
                {
                cValues++;
                }
            if (cValues == rvalue.getValueCount())
                {
                lvalue.requireAssignable(ctx, errs);
                }
            else
                {
                rvalue.log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY,
                    cValues, rvalue.getValueCount());
                }
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        switch (getUsage())
            {
            case While:
            case If:
            case For:
            case Switch:
                // TODO
                throw notImplemented();

            case Standalone:
               break;
            }

        if (isSimple())
            {
            boolean    fCompletes = fReachable;
            Assignable asnL       = lvalue.generateAssignable(ctx, code, errs);
            if (fCompletes &= !lvalue.isAborting())
                {
                rvalue.generateAssignment(ctx, code, asnL, errs);
                fCompletes &= !rvalue.isAborting();
                }

            return fCompletes;
            }

        // REVIEW what is not implemented? multi-assignment?
        throw notImplemented();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(lvalue)
          .append(' ')
          .append(op.getId().TEXT)
          .append(' ')
          .append(rvalue);

        if (term)
            {
            sb.append(';');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /*
        enum Scenario {DeclareOnly, DeclareAssign, FromCondValue, FromIterator, FromIterable}
    private transient Scenario m_scenario;
    if (value != null)
    {
    sb.append(' ')
            .append(isConditional() ? ':' : '=')
            .append(' ')
            .append(value);
    }

    // ----- ConditionalStatement methods ----------------------------------------------------------

    @Override
    protected void split(Context ctx, ErrorListener errs)
        {
        // TODO for now pretend that this only declares but does not assign
        long      lPos    = getEndPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(this, stmtNOP, errs);
        }

        // right hand side must have a value if this is not a standalone declaration
        if (getUsage() != Usage.Standalone && value == null)
            {
            log(errs, Severity.ERROR, Compiler.VALUE_REQUIRED);
            fValid = false;
            }


        Expression   valueNew = null;
        boolean      fInfer   = true;   // TODO false if "var" or "val"
        if (fInfer)
            {
            // allow the r-value to resolve names based on the l-value type's contributions
            ctx = ctx.enterInferring(typeVar);
            }

        if (isConditional())
            {
            // TODO fAssigned / fFinal (when true/false)
            fAssigned = true;

            // what it means to be conditional:
            // 1. there is a boolean value that the RVal expression must yield that satisfies the
            //    "conditional" portion of the statement
            // 2. there is at least one additional value that the RVal expression must yield that
            //    satisfies the typeVar constraint, i.e. that is assigned to the variable
            // 3. the RVal expression is fairly flexible in what we will accept, specifically:
            // 3.1 an expression that yields multiple values of which the first is a boolean and the
            //     second is compatible with typeVar:
            //          while (Person p : iter.next())
            // 3.2 an expression that yields a tuple, of whose fields the first is a boolean and the
            //     second is compatible with typeVar:
            //          if (Person p : (True, person))
            // 3.3 an expression that yields an Iterator:
            //          while (Person p : iter)
            // 3.4 an expression that yields an Iterable:
            //          while (Person p : peopleList)
            TypeConstant[] atypeRequired = new TypeConstant[2];
            atypeRequired[0] = pool.typeBoolean();
            atypeRequired[1] = typeVar;

            TypeConstant typeRequired;
            if (value.testFitMulti(ctx, atypeRequired).isFit())
                {
                // validate scenario 3.1
                valueNew   = value.validateMulti(ctx, atypeRequired, errs);
                m_scenario = Scenario.FromCondValue;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeRequired)).isFit())
                {
                // validate scenario 3.2
                valueNew   = value.validate(ctx, typeRequired, errs);
                if (valueNew != null)
                    {
                    valueNew = new MultiValueExpression(valueNew.unpackedExpressions(errs), errs);
                    }
                m_scenario = Scenario.FromCondValue;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeVar)).isFit())
                {
                // validate scenario 3.3
                valueNew   = value.validate(ctx, typeRequired, errs);
                m_scenario = Scenario.FromIterator;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeIterable(), typeVar)).isFit())
                {
                // validate scenario 3.4
                valueNew   = value.validate(ctx, typeRequired, errs);
                m_scenario = Scenario.FromIterable;
                }
            else
                {
                // nothing fits, so force the value to do the assumed default path
                valueNew = value.validateMulti(ctx, atypeRequired, errs);
                m_scenario = Scenario.FromCondValue;
                }
            }
        else if (value != null)
            {
            // what it means to be an assignment:
            // 1. there is a value that the RVal expression must yield that satisfies the type of
            //    the variable
            //          Int i = 0;
            //          Tuple<Int, String, Int> t3 = (0, "hello", 1);
            // 2. there is a value of type tuple that the RVal expression must yield, whose first
            //    field satisfies the type of the variable
            //          Int i = t3;
            //    This is supported to be consistent with multi variable declaration, e.g.
            //          (Int i, String s) = t3;
            TypeConstant typeRequired;
            if (value.testFit(ctx, typeVar).isFit())
                {
                valueNew   = value.validate(ctx, typeVar, errs);
                m_scenario = Scenario.DeclareAssign;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeVar)).isFit())
                {
                valueNew = value.validate(ctx, typeRequired, errs);
                if (valueNew != null)
                    {
                    valueNew = valueNew.unpackedExpressions(errs)[0];
                    }
                m_scenario = Scenario.DeclareAssign;
                }
            else
                {
                // nothing fits, so force the value to do the assumed default path
                valueNew   = value.validate(ctx, typeVar, errs);
                m_scenario = Scenario.DeclareAssign;
                }

            if (fInjected)
                {
                // TODO log error
                }

            fAssigned = true;
            }
        else
            {
            m_scenario = Scenario.DeclareOnly;
            }

        if (valueNew != value)
            {
            fValid &= valueNew != null && valueNew.getTypeFit().isFit();
            if (valueNew != null)
                {
                value = valueNew;
                }
            }

        // back out of the inferring scope into the declaring scope
        if (fInfer)
            {
            ctx = ctx.exitScope();
            }

        if (fAssigned)
            {
            ctx.markVarWrite(name, errs);
            }

        if (fFinal)
            {
            m_reg.markEffectivelyFinal();
            }

        if (m_scenario != Scenario.DeclareOnly)
            {
            switch (getUsage())
                {
                case While:
                case If:
                    if (m_scenario == Scenario.FromIterator)
                        {
                        // TODO: according to the new spec, this scenario will be removed
                        notImplemented();
                        }
                    // in the form "Type var : conditional"
                    // first, declare an unnamed Boolean variable that will hold the conditional result
                    code.add(new Var(pool.typeBoolean()));
                    Register regCond = code.lastRegister();
                    // next, declare the named variable
                    code.add(new Var_N(m_reg, pool.ensureStringConstant((String) name.getValue())));
                    // next, assign the r-value to the two variables
                    value.generateAssignments(ctx, code, new Assignable[]
                            {value.new Assignable(regCond), value.new Assignable(m_reg)}, errs);
                    code.add(getUsage() == Usage.If
                            ? new JumpFalse(regCond, getLabel())
                            : new JumpTrue (regCond, getLabel()));
                    return fCompletes;

                case For:
                    // in the form "Type var : Iterable"
                    // TODO
                    notImplemented();
                    break;

                case Switch:
                    // TODO - this one might just be the same as non-conditional usage
                    // fall through
                default:
                    break;
                }
            }

        if (m_listRefAnnotations == null && value != null)
            {
            // constant value: declare and initialize named var
            if (value.isConstant())
                {
                Constant constVal = null;
                switch (m_scenario)
                    {
                    case DeclareAssign:
                        constVal = value.toConstant();
                        break;

                    case FromCondValue:
                        {
                        Constant[] aconst = value.toConstants();
                        if (aconst[0].equals(pool.valTrue()))
                            {
                            constVal = aconst[1];
                            }
                        break;
                        }

                    case FromIterable:
                    case FromIterator:
                        notImplemented(); // is it possible to have a constant iterator ?
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                if (constVal != null)
                    {
                    code.add(new Var_IN(m_reg, constName, constVal));
                    }
                return fCompletes;
                }

            TypeConstant typeVar = m_reg.getType();

            // an optimization for a list assignment
            if (m_scenario == Scenario.DeclareAssign &&
                    value instanceof ListExpression && typeVar.isA(pool.typeSequence()))
                {
                // even though we validated the ListExpression to give us a single list value, it is
                // tolerant of us asking for the values as individual values
                List<Expression> listVals = ((ListExpression) value).getExpressions();
                int cVals = listVals.size();
                Argument[] aArgs = new Argument[cVals];
                for (int i = 0; i < cVals; ++i)
                    {
                    aArgs[i] = listVals.get(i).generateArgument(ctx, code, false, false, errs);
                    }
                code.add(new Var_SN(m_reg, constName, aArgs));
                return fCompletes;
                }
            }

        boolean      fCompletes = fReachable && (value == null || !value.isAborting());
        switch (m_scenario)
            {
            case DeclareAssign:
                value.generateAssignment(ctx, code, value.new Assignable(m_reg), errs);
                break;

            case FromCondValue:
            case FromIterable:
            case FromIterator:
                notImplemented(); // is it possible to have a constant iterator ?
                break;

            case DeclareOnly:
                // all done
                break;

            default:
                throw new IllegalStateException();
            }


     */
    protected AstNode    lvalue;
    protected Token      op;
    protected Expression rvalue;
    protected boolean    cond;
    protected boolean    term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssignmentStatement.class, "lvalue", "rvalue");
    }
