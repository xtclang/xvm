package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;
import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.OpTest;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.asm.ast.CmpChainExprAST;
import org.xvm.asm.ast.ExprAST;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
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
import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;


/**
 * Comparison over a chain of expressions.
 *
 * <pre><code>
 *     if (a == b == c) {...}
 *     if (a != b != c) {...}
 *     if (a < b <= c) {...}
 *     if (a > b >= c) {...}
 * </code></pre>
 *
 * @see CmpExpression
 */
public class CmpChainExpression
        extends Expression {
    // ----- constructors --------------------------------------------------------------------------

    public CmpChainExpression(@NotNull List<Expression> expressions, @NotNull List<Token> operators) {
        this.expressions = Objects.requireNonNull(expressions);
        this.operators   = Objects.requireNonNull(operators);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression uses a type composition's equals() function, or false iff the
     *         expression uses a type composition's compare() function
     */
    public boolean usesEquals() {
        return switch (operators.getFirst().getId()) {
            case COMP_EQ, COMP_NEQ -> true;
            default -> false;
        };
    }

    @SuppressWarnings("unused")
    public boolean isAscending() {
        return switch (operators.getFirst().getId()) {
            case COMP_LT, COMP_LTEQ -> true;
            default -> false;
        };
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
        ConstantPool pool     = ctx.pool();
        boolean      fOrdered = !usesEquals();

        // Phase 1: Determine common type and validate first expression if needed
        TypeConstant typeCommon     = chooseCommonType(ctx, false);
        boolean      fValidateFirst = typeCommon == null;
        if (fValidateFirst) {
            TypeConstant typeHint = fOrdered ? pool.typeOrderable() : null;
            if (!validateExpression(ctx, 0, typeHint, errs)) {
                return null;
            }
            typeCommon = chooseCommonType(ctx, false);
        }

        // Phase 2: Validate remaining expressions with type inference if possible
        boolean fInfer = typeCommon != null;
        if (fInfer) {
            ctx = ctx.enterInferring(typeCommon);
        } else {
            typeCommon = fOrdered ? pool.typeOrderable() : null;
        }

        int     startIndex = fValidateFirst ? 1 : 0;
        boolean fValid     = validateExpressions(ctx, startIndex, typeCommon, errs);

        if (fInfer) {
            ctx = ctx.exit();
        } else {
            typeCommon = resolveTypeWithConversions(ctx, pool, fOrdered, errs);
            if (typeCommon == null) {
                fValid = false;
            }
        }

        // Phase 3: Resolve comparison method
        if (fValid) {
            MethodInfo infoCmp = resolveComparisonMethod(pool, typeCommon, fOrdered, errs);
            if (infoCmp == null) {
                fValid = false;
            } else {
                m_typeCommon = typeCommon;
                m_idCmp      = infoCmp.getIdentity();
            }
        }

        // Phase 4: Check type compatibility, evaluate constants, and check null comparisons
        // For this to be a constant expression, either all sub-expressions are constant and the
        // result is calculated from that, or left-to-right, enough expressions are constant that
        // the result can be proven to always be false; for "==", see if there is a constant
        // expression that everything else can be compared to
        Constant constVal = null;
        if (fValid) {
            if (!checkTypeCompatibility(typeCommon, errs)) {
                return null;
            }
            constVal = evaluateConstantResult(typeCommon);
            if (m_constEq != null && m_constEq.equals(pool.valNull()) && !checkNullComparison(ctx, errs)) {
                fValid = false;
            }
        }

        return finishValidation(ctx, typeRequired, getImplicitType(ctx),
                fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
    }

    /**
     * Validate a single expression at the given index.
     *
     * @return true if validation succeeded
     */
    private boolean validateExpression(Context ctx, int index, TypeConstant typeHint, ErrorListener errs) {
        Expression exprOld = expressions.get(index);
        Expression exprNew = exprOld.validate(ctx, typeHint, errs);
        if (exprNew == null) {
            return false;
        }
        if (exprNew != exprOld) {
            expressions.set(index, exprNew);
        }
        return true;
    }

    /**
     * Validate expressions from startIndex to the end of the list.
     *
     * @return true if all validations succeeded
     */
    private boolean validateExpressions(Context ctx, int startIndex, TypeConstant typeCommon, ErrorListener errs) {
        boolean fValid = true;
        for (int i = startIndex; i < expressions.size(); i++) {
            Expression exprOld = expressions.get(i);
            Expression exprNew = exprOld.validate(ctx, typeCommon, errs);
            if (exprNew == null) {
                fValid = false;
            } else if (exprNew != exprOld) {
                expressions.set(i, exprNew);
            }
        }
        return fValid;
    }

    /**
     * Attempt to find a common type and apply necessary conversions.
     * This is used when type inference was not available.
     *
     * @return the resolved common type, or null if resolution failed
     */
    private TypeConstant resolveTypeWithConversions(
            Context ctx, ConstantPool pool, boolean fOrdered, ErrorListener errs) {
        TypeConstant typeCommon = chooseCommonType(ctx, true);

        if (typeCommon != null) {
            Map<Integer, MethodConstant> conversions = findRequiredConversions(typeCommon);
            if (conversions == null) {
                typeCommon = null;  // conversion not possible
            } else {
                applyConversions(conversions, errs);
            }
        }

        if (typeCommon == null) {
            if (fOrdered) {
                // TODO need a better error
                log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
                        expressions.getFirst().getType().getValueString(), "...");
                return null;
            }
            // for equality, just use Object
            return pool.typeObject();
        }

        return typeCommon;
    }

    /**
     * Find the conversions required to convert all expressions to the target type.
     *
     * @return a map of expression index to conversion method, or null if any conversion is impossible
     */
    private Map<Integer, MethodConstant> findRequiredConversions(TypeConstant typeTarget) {
        Map<Integer, MethodConstant> conversions = new HashMap<>();
        for (int i = 0; i < expressions.size(); i++) {
            TypeConstant typePre = expressions.get(i).getType();
            if (!typePre.isA(typeTarget)) {
                MethodConstant method = typePre.getConverterTo(typeTarget);
                if (method == null) {
                    return null;  // conversion not possible
                }
                conversions.put(i, method);
            }
        }
        return conversions;
    }

    /**
     * Apply the conversion methods to the expressions.
     */
    private void applyConversions(Map<Integer, MethodConstant> conversions, ErrorListener errs) {
        conversions.forEach((i, method) ->
                expressions.set(i, new ConvertExpression(
                        expressions.get(i), new MethodConstant[] {method}, errs)));
    }

    /**
     * Resolve the comparison method (equals or compare) for the common type.
     *
     * @return the method info, or null if not found
     */
    private MethodInfo resolveComparisonMethod(
            ConstantPool pool, TypeConstant typeCommon, boolean fOrdered, ErrorListener errs) {
        SignatureConstant sigCmp  = fOrdered ? pool.sigCompare() : pool.sigEquals();
        MethodInfo        infoCmp = typeCommon.ensureTypeInfo(errs).getMethodBySignature(sigCmp);
        if (infoCmp == null) {
            log(errs, Severity.ERROR, Compiler.MISSING_METHOD, sigCmp.getName(), typeCommon.getValueString());
        }
        return infoCmp;
    }

    /**
     * Check that all expression types are compatible with the comparison operation.
     *
     * @return true if all types are compatible
     */
    private boolean checkTypeCompatibility(TypeConstant typeCommon, ErrorListener errs) {
        boolean fEquality = operators.getFirst().getId() == Id.COMP_EQ;

        for (Expression expr : expressions) {
            TypeConstant type   = expr.getType();
            boolean      fConst = expr.isConstant();

            boolean fSupported = fEquality
                    ? typeCommon.supportsEquals(type, fConst)
                    : typeCommon.supportsCompare(type, fConst);

            if (!fSupported) {
                log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
                        typeCommon.getValueString(), type.getValueString());
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluate constant expressions to determine if the result can be computed at compile time.
     * Also records a constant for equality optimization if found.
     *
     * @return the constant result if determinable, or null otherwise
     */
    private Constant evaluateConstantResult(TypeConstant typeCommon) {
        ConstantPool pool      = pool();
        boolean      fEquality = operators.getFirst().getId() == Id.COMP_EQ;
        Constant     FALSE     = pool.valFalse();
        Constant     constVal  = null;
        Constant     constPrev = null;
        boolean      fAllConst = true;

        for (int i = 0; i < expressions.size(); i++) {
            Expression expr   = expressions.get(i);
            boolean    fConst = expr.isConstant();

            if (!fConst) {
                fAllConst = false;
                constPrev = null;
                continue;
            }

            Constant constCur = expr.toConstant();

            // Record first constant for equality optimization
            if (fEquality && m_constEq == null) {
                m_constEq = constCur;
            }

            // Try to evaluate the chain at compile time
            if (fAllConst && i > 0 && constPrev != null) {
                constVal = applyComparison(constPrev, operators.get(i - 1).getId(), constCur);
                if (constVal == null) {
                    fAllConst = false;
                } else if (constVal.equals(FALSE)) {
                    return constVal;  // short-circuit: proven false
                }
            }
            constPrev = constCur;
        }

        return constVal;
    }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs) {
        if (!LVal.isLocalArgument()) {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
        }

        ConstantPool pool       = pool();
        int          cExprs     = expressions.size();
        Constant     constVal   = m_constEq;
        TypeConstant typeCmp    = m_typeCommon;
        Argument     argResult  = LVal.getLocalArgument();

        var labelFalse = new Label("not_eq");
        var labelEnd   = new Label("end_eq");

        // evaluate all the sub-expressions upfront to ensure point-in-time semantics
        var args = generateArguments(ctx, code, errs);

        if (constVal == null) {
            // everything has to get compared, left to right
            int cOps = operators.size();
            for (int iCmp = 0; iCmp < cOps; iCmp++) {
                Id      id    = operators.get(iCmp).getId();
                var     arg1  = args.get(iCmp);
                var     arg2  = args.get(iCmp + 1);
                boolean fLast = iCmp == cOps - 1;

                code.add(fLast
                        ? createTestOp(id, typeCmp, arg1, arg2, argResult)
                        : createJumpOp(id, typeCmp, arg1, arg2, labelFalse));
            }
        } else {
            // optimize equality comparisons to a constant, especially to null
            boolean fNull = constVal.equals(pool.valNull());

            // all but last: jump to false if not equal
            args.subList(0, cExprs - 1).forEach(arg -> code.add(fNull
                    ? new JumpNotNull(arg, labelFalse)
                    : new JumpNotEq(typeCmp, arg, constVal, labelFalse)));

            // last: test and store result
            var argLast = args.getLast();
            code.add(fNull
                    ? new IsNull(argLast, argResult)
                    : new IsEq(typeCmp, argLast, constVal, argResult));
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

        // optimize equality comparisons to a constant, especially to null
        var constVal = m_constEq;
        var typeCmp  = m_typeCommon;
        var args     = generateArguments(ctx, code, errs);

        if (constVal != null) {
            boolean fNull = constVal.equals(pool().valNull());
            args.forEach(arg -> code.add(fNull
                    ? new JumpNull(arg, label)
                    : new JumpEq(typeCmp, arg, constVal, label)));
            return;
        }

        var labelFalse = fWhenTrue ? new Label("not_eq") : label;

        // otherwise, everything has to get compared, left to right
        int cOps = operators.size();
        for (int iCmp = 0; iCmp < cOps; ++iCmp) {
            code.add(createJumpOp(operators.get(iCmp).getId(), typeCmp,
                    args.get(iCmp), args.get(iCmp + 1), labelFalse));
        }

        if (fWhenTrue) {
            code.add(new Jump(label));
            code.add(labelFalse);
        }
    }

    @Override
    public ExprAST getExprAST(Context ctx) {
        return new CmpChainExprAST(
                expressions.stream().map(expr -> expr.getExprAST(ctx)).toList(),
                operators.stream().map(tok -> toOperator(tok.getId())).toList(),
                m_idCmp);
    }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isShortCircuiting() {
        return expressions.stream().anyMatch(Expression::isShortCircuiting);
    }

    @Override
    public boolean isCompletable() {
        return expressions.getFirst().isCompletable() && expressions.get(1).isCompletable();
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Choose a common type for all the specified expressions.
     *
     * @param ctx     the context
     * @param fCheck  unused
     *
     * @return the selected common type, or null
     */
    protected TypeConstant chooseCommonType(Context ctx, @SuppressWarnings("unused") boolean fCheck) {
        ConstantPool      pool     = pool();
        boolean           fEqual   = usesEquals();
        var setTried = new HashSet<TypeConstant>();

        for (int i = 0, c = expressions.size(); i < c; i++) {
            Expression   expr1 = expressions.get(i);
            TypeConstant type1 = expr1.getImplicitType(ctx);
            if (type1 != null) {
                ctx = ctx.enterInferring(type1);
                for (int j = 0; j < c; j++) {
                    if (j == i) {
                        continue;
                    }

                    Expression   expr2 = expressions.get(j);
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
        var inferCtx = ctx.enterInferring(type);
        // NOTE: no need to exit/discard an inferring context
        return expressions.stream().allMatch(expr -> expr.testFit(inferCtx, type, false, null).isFit());
    }

    boolean checkNullComparison(@SuppressWarnings("unused") Context ctx, ErrorListener errs) {
        TypeConstant typeNull = pool().typeNull();
        var nonNullable = expressions.stream()
                .map(Expression::getType)
                .filter(type -> !type.isNullable() && !typeNull.isA(type.resolveConstraints()))
                .findFirst();

        if (nonNullable.isPresent()) {
            log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE, nonNullable.get().getValueString());
            return false;
        }
        // TODO GG: implement type narrowing for null comparisons (see CmpExpression)
        return true;
    }

    /**
     * Evaluate all sub-expressions and generate their arguments, ensuring point-in-time semantics.
     */
    private List<Argument> generateArguments(Context ctx, Code code, ErrorListener errs) {
        return IntStream.range(0, expressions.size())
                .mapToObj(i -> {
                    var expr = expressions.get(i);
                    var arg  = expr.generateArgument(ctx, code, false, errs);
                    return expr.ensurePointInTime(code, arg, expressions, i);
                })
                .toList();
    }

    /**
     * Attempt to apply a comparison operation between two constants at compile time.
     *
     * @param left   the left-hand constant
     * @param op     the comparison operator
     * @param right  the right-hand constant
     *
     * @return the result constant (typically Boolean), or null if the operation is not supported
     */
    private static Constant applyComparison(Constant left, Id op, Constant right) {
        try {
            return left.apply(op, right);
        } catch (UnsupportedOperationException | ArithmeticException e) {
            // TODO Add some kind of debug logging, or make sure error cases are at least registered in listeners.
            return null;
        }
    }

    /**
     * Mapping from comparison token to the corresponding ops and AST operator.
     */
    private record CmpOps(
            TestOpFactory  testOp,
            JumpOpFactory  jumpOp,
            Operator       astOp) {

        @FunctionalInterface interface TestOpFactory {
            OpTest create(TypeConstant type, Argument a1, Argument a2, Argument result);
        }
        @FunctionalInterface interface JumpOpFactory {
            Op create(TypeConstant type, Argument a1, Argument a2, Label label);
        }
    }

    private static OpTest createTestOp(Id id, TypeConstant typeCmp,
            Argument arg1, Argument arg2, Argument argResult) {
        return getCmpOps(id).testOp().create(typeCmp, arg1, arg2, argResult);
    }

    private static Op createJumpOp(Id id, TypeConstant typeCmp,
            Argument arg1, Argument arg2, Label labelFalse) {
        return getCmpOps(id).jumpOp().create(typeCmp, arg1, arg2, labelFalse);
    }

    private static Operator toOperator(Id id) {
        return getCmpOps(id).astOp();
    }

    private static CmpOps getCmpOps(Id id) {
        var ops = CMP_OPS.get(id);
        if (ops == null) {
            throw new IllegalStateException("Unexpected comparison: " + id);
        }
        return ops;
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return expressions.getFirst() + IntStream.range(0, operators.size())
                .mapToObj(i -> " " + operators.get(i).getId().TEXT + " " + expressions.get(i + 1))
                .collect(Collectors.joining());
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }

    // ----- fields --------------------------------------------------------------------------------

    private static final EnumMap<Id, CmpOps> CMP_OPS = new EnumMap<>(Map.of(
            Id.COMP_EQ,   new CmpOps(IsEq::new,    JumpNotEq::new, Operator.CompEq),
            Id.COMP_NEQ,  new CmpOps(IsNotEq::new, JumpEq::new,    Operator.CompNeq),
            Id.COMP_LT,   new CmpOps(IsLt::new,    JumpGte::new,   Operator.CompLt),
            Id.COMP_GT,   new CmpOps(IsGt::new,    JumpLte::new,   Operator.CompGt),
            Id.COMP_LTEQ, new CmpOps(IsLte::new,   JumpGt::new,    Operator.CompLtEq),
            Id.COMP_GTEQ, new CmpOps(IsGte::new,   JumpLt::new,    Operator.CompGtEq)));

    // TODO these fields CANNOT be final because AstNode.clone() uses reflection to copy them.
    //      This is a fundamental design problem with the AST framework:
    //      1. Immutability cannot be enforced - any field can be mutated via reflection
    //      2. Thread safety is impossible - no happens-before guarantees with reflection writes
    //      3. The framework mixes mutable "validation state" (transient fields) with structural
    //         data, making it hard to reason about what can change and when
    //      4. Parallel compilation of sub-AST nodes would require deep defensive copies
    //      To fix this properly, the AST should be redesigned with:
    //      - Immutable structural nodes (expressions, operators) set only via constructors
    //      - Separate mutable compilation context passed through methods, not stored in fields
    //      - Explicit copy/transform methods instead of reflection-based cloning
    protected @NotNull List<Expression> expressions;
    protected @NotNull List<Token>      operators;

    /**
     * The common type used for the comparison.
     */
    private transient TypeConstant m_typeCommon;

    /**
     * The method used for the comparison.
     */
    protected transient MethodConstant m_idCmp;

    /**
     * The constant value that all other expressions are compared to for equality; often
     * {@link ConstantPool#valNull()}.
     */
    private transient Constant m_constEq;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CmpChainExpression.class, "expressions");
}
