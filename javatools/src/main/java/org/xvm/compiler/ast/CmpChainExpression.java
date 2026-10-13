package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.OpCondJump;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.IsGt;
import org.xvm.asm.op.IsGte;
import org.xvm.asm.op.IsLt;
import org.xvm.asm.op.IsLte;
import org.xvm.asm.op.IsNotEq;
import org.xvm.asm.op.IsNull;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpEq;
import org.xvm.asm.op.JumpGt;
import org.xvm.asm.op.JumpGte;
import org.xvm.asm.op.JumpLt;
import org.xvm.asm.op.JumpLte;
import org.xvm.asm.op.JumpNotEq;
import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/**
 * Comparison over a chain of expressions.
 *
 * <pre>{@code
 *     if (a == b == c) {...}
 *     if (a != b != c) {...}
 *     if (a < b <= c) {...}
 *     if (a > b >= c) {...}
 * }</pre>
 *
 * @see CmpExpression
 */
public class CmpChainExpression
        extends Expression {
    // ----- constructors --------------------------------------------------------------------------

    public CmpChainExpression(List<Expression> expressions, Token[] operators) {
        this.expressions = expressions;
        this.operators   = operators;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression uses a type composition's equals() function, or false iff the
     *         expression uses a type composition's compare() function
     */
    public boolean usesEquals() {
        Id id = operators[0].getId();
        return id == Id.COMP_EQ | id == Id.COMP_NEQ;
    }

    @Override
    public long getStartPosition() {
        return expressions.getFirst().getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return expressions.getLast().getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx) {
        return ctx.pool().typeBoolean();
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        boolean fValid = true;

        // find the common type across all the expressions, hopefully before having to validate
        // any of the expressions
        ConstantPool     pool      = ctx.pool();
        boolean          fOrdered  = !usesEquals();
        List<Expression> listExprs = expressions;
        int              cExprs    = listExprs.size();

        TypeConstant typeCommon  = chooseCommonType(ctx);
        boolean      fForceFirst = typeCommon == null;
        if (fForceFirst) {
            Expression exprOld = listExprs.getFirst();
            Expression exprNew = exprOld.validate(ctx, fOrdered ? pool.typeOrderable() : null, errs);
            if (exprNew == null) {
                return null;
            }
            if (exprNew != exprOld) {
                listExprs.set(0, exprNew);
            }
            typeCommon = chooseCommonType(ctx);
        }

        // allow the expressions to resolve names based on the requested type
        boolean fInfer = typeCommon != null;
        if (fInfer) {
            ctx = ctx.enterInferring(typeCommon);
        } else {
            typeCommon = fOrdered ? pool.typeOrderable() : null;
        }

        for (int i = fForceFirst ? 1 : 0; i < cExprs; ++i) {
            Expression exprOld = listExprs.get(i);
            Expression exprNew = listExprs.get(i).validate(ctx, typeCommon, errs);
            if (exprNew == null) {
                fValid = false;
            } else if (exprNew != exprOld) {
                listExprs.set(i, exprNew);
            }
        }

        if (fInfer) {
            ctx = ctx.exit();
        } else {
            typeCommon = chooseCommonType(ctx);
            CheckConversions: if (typeCommon != null) {
                // test if we can convert all the expressions to the decided-upon type
                MethodConstant[] aConvMethod = new MethodConstant[cExprs];
                for (int i = 0; i < cExprs; ++i) {
                    TypeConstant typePre = listExprs.get(i).getType();
                    if (!typePre.isA(typeCommon)) {
                        MethodConstant method = typePre.getConverterTo(typeCommon);
                        if (method == null) {
                            typeCommon = null;
                            break CheckConversions;
                        } else {
                            aConvMethod[i] = method;
                        }
                    }
                }

                for (int i = 0; i < cExprs; ++i) {
                    MethodConstant method = aConvMethod[i];
                    if (method != null) {
                        listExprs.set(i, new ConvertExpression(listExprs.get(i),
                                                new MethodConstant[]{method}, errs));
                    }
                }
            }

            if (typeCommon == null) {
                if (fOrdered) {
                    // TODO need a better error
                    log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
                                listExprs.getFirst().getType().getValueString(), "...");
                    fValid = false;
                } else {
                    // for equality, just use Object
                    typeCommon = pool.typeObject();
                }
            }
        }

        // store the resulting common type to compare
        if (fValid) {
            assert typeCommon != null;

            m_idCmp = CmpExpression.findComparisonMethod(this, typeCommon, !fOrdered, errs);
            fValid  = m_idCmp != null;
            if (fValid) {
                m_typeCommon = typeCommon;
            }
        }

        // check every operand, even when a constant prefix already determines the result
        Constant constVal = null;
        if (fValid) {
            boolean fEquality = operators[0].getId() == Id.COMP_EQ;
            for (Expression expr : listExprs) {
                boolean      fConst = expr.isConstant();
                TypeConstant type   = expr.getType();
                if (fEquality
                        ? !typeCommon.supportsEquals (type, fConst)
                        : !typeCommon.supportsCompare(type, fConst)) {
                    log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE, typeCommon.getValueString(), type.getValueString());
                    return null;
                }
                if (fEquality && fConst && m_constEq == null) {
                    m_constEq = expr.toConstant();
                }
            }

            constVal = foldConstants();
            if (m_constEq != null && m_constEq.equals(pool.valNull())) {
                fValid = checkNullComparison(errs);
            }
        }

        return finishValidation(ctx, typeRequired, getImplicitType(ctx),
                fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
    }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs) {
        if (!LVal.isLocalArgument()) {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
        }

        ConstantPool pool       = pool();
        Argument[]   aArgs      = generateComparisonArguments(ctx, code, errs);
        int          cExprs     = aArgs.length;
        Constant     constVal   = m_constEq;
        TypeConstant typeCmp    = m_typeCommon;
        Argument     argResult  = LVal.getLocalArgument();
        Label        labelFalse = new Label("not_eq");
        Label        labelEnd   = new Label("end_eq");

        if (constVal == null) {
            // everything has to get compared, left to right
            Token[] aTokOp = operators;
            for (int iCmp = 0, cOps = aTokOp.length; iCmp < cOps; iCmp++) {
                Token    tok  = aTokOp[iCmp];
                Argument arg1 = aArgs[iCmp];
                Argument arg2 = aArgs[iCmp + 1];

                if (iCmp == cOps - 1) { // last
                    code.add(switch (tok.getId()) {
                        case COMP_EQ   -> new IsEq(typeCmp, arg1, arg2, argResult);
                        case COMP_NEQ  -> new IsNotEq(typeCmp, arg1, arg2, argResult);
                        case COMP_LT   -> new IsLt(typeCmp, arg1, arg2, argResult);
                        case COMP_GT   -> new IsGt(typeCmp, arg1, arg2, argResult);
                        case COMP_LTEQ -> new IsLte(typeCmp, arg1, arg2, argResult);
                        case COMP_GTEQ -> new IsGte(typeCmp, arg1, arg2, argResult);
                        default        -> throw new IllegalStateException();
                    });
                } else {
                    code.add(comparisonFailure(tok.getId(), arg1, arg2, labelFalse));
                }
            }
        } else {
            // optimize equality comparisons to a constant, especially to null
            boolean fNull = constVal.equals(pool.valNull());
            for (int iExpr = 0; iExpr < cExprs; ++iExpr) {
                Argument arg = aArgs[iExpr];
                if (iExpr == cExprs - 1) { // last
                    OpTest opTest = fNull
                            ? new IsNull(arg, argResult)
                            : new IsEq(typeCmp, arg, constVal, argResult);
                    code.add(opTest);
                } else {
                    OpCondJump opCondJump = fNull
                            ? new JumpNotNull(arg, labelFalse)
                            : new JumpNotEq(typeCmp, arg, constVal, labelFalse);
                    code.add(opCondJump);
                }
            }
        }

        code.add(new Jump(labelEnd))
            .add(labelFalse)
            .add(new Move(pool.valFalse(), argResult))
            .add(labelEnd);
    }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs) {
        // constant expressions can be handled by the default implementation
        if (isConstant()) {
            super.generateConditionalJump(ctx, code, label, fWhenTrue, errs);
            return;
        }

        Argument[] aArgs      = generateComparisonArguments(ctx, code, errs);
        Constant   constVal   = m_constEq;
        Label      labelFalse = fWhenTrue ? new Label("not_eq") : label;

        if (constVal == null) {
            for (int i = 0; i < operators.length; ++i) {
                code.add(comparisonFailure(operators[i].getId(), aArgs[i], aArgs[i + 1], labelFalse));
            }
        } else {
            boolean fNull = constVal.equals(pool().valNull());
            for (Argument arg : aArgs) {
                code.add(fNull ? new JumpNotNull(arg, labelFalse)
                        : new JumpNotEq(m_typeCommon, arg, constVal, labelFalse));
            }
        }

        if (fWhenTrue) {
            code.add(new Jump(label));
            code.add(labelFalse);
        }
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isShortCircuiting() {
        for (Expression expr : expressions) {
            if (expr.isShortCircuiting()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCompletable() {
        return expressions.getFirst().isCompletable() && expressions.get(1).isCompletable();
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Choose a common type for all the specified expressions.
     *
     * @param ctx  the context
     *
     * @return the selected common type, or null
     */
    protected TypeConstant chooseCommonType(Context ctx) {
        ConstantPool      pool      = pool();
        boolean           fEqual    = usesEquals();
        List<Expression>  listExprs = expressions;
        Set<TypeConstant> setTried  = new HashSet<>();

        for (int i = 0, c = listExprs.size(); i < c; i++) {
            Expression   expr1 = listExprs.get(i);
            TypeConstant type1 = expr1.getImplicitType(ctx);
            if (type1 != null) {
                ctx = ctx.enterInferring(type1);
                for (int j = 0; j < c; j++) {
                    if (j == i) {
                        continue;
                    }

                    Expression   expr2 = listExprs.get(j);
                    TypeConstant type2 = expr2.getImplicitType(ctx);
                    TypeConstant typeC = CmpExpression.chooseCommonType(pool, fEqual, type1, type2);

                    if (typeC != null && !setTried.contains(typeC)) {
                        ctx = ctx.exit(); // inferring type1
                        if (testCommonType(ctx, typeC)) {
                            // no need to exit/discard an inferring context
                            return typeC;
                        }
                        setTried.add(typeC);
                        ctx = ctx.enterInferring(type1);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Test the specified type for "fit" against all the expression.
     *
     * @param ctx   the compilation context
     * @param type  the type to test
     *
     * @return true iff all the expressions "fit"
     */
    protected boolean testCommonType(Context ctx, TypeConstant type) {
        ctx = ctx.enterInferring(type);

        for (Expression expr : expressions) {
            if (!expr.testFit(ctx, type, false, silent(PROBE)).isFit()) {
                return false;
            }
        }

        // there is no need to exit/discard an inferring context
        return true;
    }

    private boolean checkNullComparison(ErrorListener errs) {
        TypeConstant typeNull = pool().typeNull();
        for (Expression expr : expressions) {
            TypeConstant type = expr.getType();
            if (!type.isNullable() && !typeNull.isA(type.resolveConstraints())) {
                log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE, type.getValueString());
                return false;
            }
        }

        return true;
    }

    /** Fold only complete constant chains so runtime operand evaluation is never discarded. */
    private Constant foldConstants() {
        if (!expressions.stream().allMatch(Expression::isConstant)) {
            return null;
        }
        Constant previous = expressions.getFirst().toConstant();
        Constant result   = null;
        try {
            for (int i = 0; i < operators.length; ++i) {
                Constant next = expressions.get(i + 1).toConstant();
                result = previous.apply(operators[i].getId(), next);
                if (result == null || result.equals(pool().valFalse())) {
                    return result;
                }
                previous = next;
            }
            return result;
        } catch (RuntimeException e) {
            // unsupported constant operations must be evaluated at runtime
            return null;
        }
    }

    /** Evaluate each operand once, preserving its value before later operands can change it. */
    private Argument[] generateComparisonArguments(Context ctx, Code code, ErrorListener errs) {
        return generateElementArguments(ctx, code, expressions, false, errs);
    }

    /** Jump when one link in the comparison chain fails, for both value and branch emission. */
    private OpCondJump comparisonFailure(Id operator, Argument first, Argument second, Label target) {
        return switch (operator) {
            case COMP_EQ   -> new JumpNotEq(m_typeCommon, first, second, target);
            case COMP_NEQ  -> new JumpEq(m_typeCommon, first, second, target);
            case COMP_LT   -> new JumpGte(m_typeCommon, first, second, target);
            case COMP_GT   -> new JumpLte(m_typeCommon, first, second, target);
            case COMP_LTEQ -> new JumpGt(m_typeCommon, first, second, target);
            case COMP_GTEQ -> new JumpLt(m_typeCommon, first, second, target);
            default        -> throw new IllegalStateException();
        };
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(expressions.getFirst());
        for (int i = 0, c = operators.length; i < c; ++i) {
            sb.append(' ')
              .append(operators[i].getId().TEXT)
              .append(' ')
              .append(expressions.get(i + 1));
        }
        return sb.toString();
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }

    // ----- fields --------------------------------------------------------------------------------

    protected List<Expression> expressions;
    protected Token[]          operators;

    /**
     * The common type used for the comparison.
     */
    private TypeConstant m_typeCommon;
    /**
     * The method used for the comparison.
     */
    protected transient MethodConstant m_idCmp;

    /**
     * The constant value that all other expressions are compared to for equality; often
     * {@link ConstantPool#valNull()}.
     */
    private Constant m_constEq;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CmpChainExpression.class, "expressions");
}
