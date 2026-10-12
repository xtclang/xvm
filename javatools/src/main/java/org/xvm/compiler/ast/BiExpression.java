package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import org.xvm.compiler.Token;

/**
 * Generic expression for something that follows the pattern "expression operator expression".
 *
 * <ul>
 * <li><code>COLON:      ":"</code> - an "else" for nullability checks</li>
 * <li><code>COND_ELSE:  "?:"</code> - the "elvis" operator</li>
 * <li><code>COND_OR:    "||"</code> - </li>
 * <li><code>COND_XOR:   "^^"</code> - </li>
 * <li>{@code COND_AND:   "&&"} - </li>
 * <li><code>BIT_OR:     "|"</code> - </li>
 * <li><code>BIT_XOR:    "^"</code> - </li>
 * <li>{@code BIT_AND:    "&"} - </li>
 * <li><code>COMP_EQ:    "=="</code> - </li>
 * <li><code>COMP_NEQ:   "!="</code> - </li>
 * <li>{@code COMP_LT:    "<"} - </li>
 * <li><code>COMP_GT:    ">"</code> - </li>
 * <li>{@code COMP_LTEQ:  "<="} - </li>
 * <li><code>COMP_GTEQ:  ">="</code> - </li>
 * <li>{@code COMP_ORD:   "<=>"} - </li>
 * <li><code>AS:         "as"</code> - </li>
 * <li><code>IS:         "is"</code> - </li>
 * <li><code>DOTDOT:     ".."</code> - </li>
 * <li>{@code SHL:        "<<"} - </li>
 * <li><code>SHR:        ">>"</code> - </li>
 * <li>{@code USHR:       ">>>"} - </li>
 * <li><code>ADD:        "+"</code> - </li>
 * <li><code>SUB:        "-"</code> - </li>
 * <li><code>MUL:        "*"</code> - </li>
 * <li><code>DIV:        "/"</code> - </li>
 * <li><code>MOD:        "%"</code> - </li>
 * <li><code>DIVREM:     "/%"</code> - </li>
 * </ul>
 */
public abstract class BiExpression
        extends Expression {
    // ----- constructors --------------------------------------------------------------------------

    public BiExpression(Expression expr1, Token operator, Expression expr2) {
        this.expr1    = expr1;
        this.operator = operator;
        this.expr2    = expr2;
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the first sub-expression of the bi-expression
     */
    public Expression getExpression1() {
        return expr1;
    }

    /**
     * @return the operator for the bi-expression
     */
    public Token getOperator() {
        return operator;
    }

    /**
     * @return the second sub-expression of the bi-expression
     */
    public Expression getExpression2() {
        return expr2;
    }

    @Override
    public long getStartPosition() {
        return expr1.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return expr2.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isShortCircuiting() {
        return expr1.isShortCircuiting() || expr2.isShortCircuiting();
    }

    @Override
    public boolean isCompletable() {
        return expr1.isCompletable() && expr2.isCompletable();
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return String.valueOf(expr1) + ' ' + operator.getId().TEXT + ' ' + expr2;
    }

    @Override
        public String getDumpDesc() {
        return toString();
    }

    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr1;
    protected Token      operator;
    protected Expression expr2;

    private static final Field[] CHILD_FIELDS = fieldsForNames(BiExpression.class, "expr1", "expr2");
}
