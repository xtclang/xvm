package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnionTypeConstant;


/**
 * Used for parenthesized expressions.
 */
public class ParenthesizedExpression
        extends DelegatingExpression {
    // ----- constructors --------------------------------------------------------------------------

    public ParenthesizedExpression(Expression expr, long lStartPos, long lEndPos) {
        super(expr);

        m_lStartPos = lStartPos;
        m_lEndPos   = lEndPos;
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "expr" - deep copied by AstNode.clone()</li>
     *   <li>No transient fields in this class</li>
     * </ul>
     *
     * @param original  the ParenthesizedExpression to copy from
     */
    protected ParenthesizedExpression(@NotNull ParenthesizedExpression original) {
        super(Objects.requireNonNull(original));

        // Copy non-child structural fields
        this.m_lStartPos = original.m_lStartPos;
        this.m_lEndPos   = original.m_lEndPos;
    }

    @Override
    public ParenthesizedExpression copy() {
        return new ParenthesizedExpression(this);
    }


    // ----- visitor pattern -----------------------------------------------------------------------

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visit(this);
    }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive,
                           ErrorListener errs) {
        TypeFit fitTuple = testTupleFit(ctx, typeRequired, fExhaustive, null);
        TypeFit fitValue = super.testFit(ctx, typeRequired, fExhaustive, null);
        return fitValue.betterOf(fitTuple);
    }

    /**
     * Given a required type, determine the fit of this expression if it were a tuple literal.
     *
     * @param typeRequired the required type, or null
     *
     * @return the fit of this expression assuming it were a tuple literal
     */
    private TypeFit testTupleFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive,
                                 ErrorListener errs) {
        TypeConstant typeTuple = mightBeTuple(typeRequired);
        if (typeTuple != null) {
            // if the use of this expression needs a Tuple, then the parentheses can be assumed to
            // define a Tuple literal of a single element
            List<TypeConstant> listElements = typeTuple.getTupleParamTypes();
            if (listElements == null || listElements.isEmpty()) {
                // the required type is `Tuple` or `Tuple<>`, which any Tuple will satisfy
                return TypeFit.Fit;
            } else if (listElements.size() == 1) {
                // the parenthesized expression `expr` would be the single element of the tuple
                TypeConstant typeElement = listElements.getFirst();
                return expr.testFit(ctx, typeElement, fExhaustive, errs);
            }
        }
        return TypeFit.NoFit;
    }

    /**
     * Given a required type, determine if a tuple could fit the required type.
     *
     * @param typeRequired the required type, or null
     *
     * @return the tuple type, or null
     */
    private static TypeConstant mightBeTuple(TypeConstant typeRequired) {
        if (typeRequired == null) {
            return null;
        }

        // this logic mirrors the logic in TupleExpression
        TypeConstant typeTuple = null;
        if (typeRequired.isTuple()) {
            typeTuple = typeRequired;
        } else if (typeRequired instanceof UnionTypeConstant typeUnion) {
            typeTuple = typeUnion.extractTuple();
        }

        if (typeTuple != null) {
            List<TypeConstant> elementTypes = typeTuple.getTupleParamTypes();
            if (elementTypes == null || elementTypes.size() <= 1) {
                return typeTuple;
            }
        }
        return null;
    }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs) {
        return atypeRequired.length == 1
                ? testFit(ctx, atypeRequired[0], fExhaustive, errs)
                : super.testFitMulti(ctx, atypeRequired, fExhaustive, errs);
    }

    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        if (typeRequired != null) {
            TypeFit fitTuple = testTupleFit(ctx, typeRequired, true, null);
            TypeFit fitValue = super.testFit(ctx, typeRequired, true, null);
            if (fitTuple.betterThan(fitValue)) {
                // replace this parenthesized expression with an actual tuple expression containing
                // the one element `(expr)`
                TupleExpression exprTuple = new TupleExpression(null, new ArrayList<>(List.of(expr)),
                        getStartPosition(), getEndPosition());
                exprTuple.adopt(expr);
                replaceThisWith(exprTuple);
                return exprTuple.validate(ctx, typeRequired, errs);
            }
        }

        return super.validate(ctx, typeRequired, errs);
    }

    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs) {
        if (atypeRequired != null && atypeRequired.length == 1) {
            // handle the possible tuple
            return validate(ctx, atypeRequired[0], errs);
        }

        return super.validateMulti(ctx, atypeRequired, errs);
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition() {
        return m_lStartPos;
    }

    @Override
    public long getEndPosition() {
        return m_lEndPos;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    @Override
    public TypeExpression toTypeExpression() {
        return expr.toTypeExpression();
    }

    @Override
    public boolean isStandalone() {
        return expr.isStandalone();
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return "(" + expr + ")";
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The start and end positions.
     */
    private final long m_lStartPos;
    private final long m_lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ParenthesizedExpression.class, "expr");
}