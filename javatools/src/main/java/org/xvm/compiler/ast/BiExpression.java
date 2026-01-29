package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ast.BiExprAST.Operator;
import org.xvm.asm.ast.CondOpExprAST;
import org.xvm.asm.ast.DivRemExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.RelOpExprAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;


/**
 * Generic expression for something that follows the pattern "expression operator expression".
 *
 * <ul>
 * <li><tt>COLON:      ":"</tt> - an "else" for nullability checks</li>
 * <li><tt>COND_ELSE:  "?:"</tt> - the "elvis" operator</li>
 * <li><tt>COND_OR:    "||"</tt> - </li>
 * <li><tt>COND_XOR:   "^^"</tt> - </li>
 * <li><tt>COND_AND:   "&&"</tt> - </li>
 * <li><tt>BIT_OR:     "|"</tt> - </li>
 * <li><tt>BIT_XOR:    "^"</tt> - </li>
 * <li><tt>BIT_AND:    "&"</tt> - </li>
 * <li><tt>COMP_EQ:    "=="</tt> - </li>
 * <li><tt>COMP_NEQ:   "!="</tt> - </li>
 * <li><tt>COMP_LT:    "<"</tt> - </li>
 * <li><tt>COMP_GT:    ">"</tt> - </li>
 * <li><tt>COMP_LTEQ:  "<="</tt> - </li>
 * <li><tt>COMP_GTEQ:  ">="</tt> - </li>
 * <li><tt>COMP_ORD:   "<=><tt>"</tt> - </li>
 * <li><tt>AS:         "as"</tt> - </li>
 * <li><tt>IS:         "is"</tt> - </li>
 * <li><tt>DOTDOT:     ".."</tt> - </li>
 * <li><tt>SHL:        "<<"</tt> - </li>
 * <li><tt>SHR:        ">>"</tt> - </li>
 * <li><tt>USHR:       ">>><tt>"</tt> - </li>
 * <li><tt>ADD:        "+"</tt> - </li>
 * <li><tt>SUB:        "-"</tt> - </li>
 * <li><tt>MUL:        "*"</tt> - </li>
 * <li><tt>DIV:        "/"</tt> - </li>
 * <li><tt>MOD:        "%"</tt> - </li>
 * <li><tt>DIVREM:     "/%"</tt> - </li>
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

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "expr1", "expr2" - deep copied by AstNode.clone()</li>
     *   <li>No transient fields in this class</li>
     * </ul>
     * <p>
     * Order matches master clone(): all non-child fields FIRST, then children.
     *
     * @param original  the BiExpression to copy from
     */
    protected BiExpression(@NotNull BiExpression original) {
        super(Objects.requireNonNull(original));

        // Step 1: Copy non-child fields FIRST (Token is immutable, safe to share)
        this.operator = original.operator;

        // Step 2: Deep copy children explicitly (CHILD_FIELDS: expr1, expr2)
        this.expr1 = original.expr1 == null ? null : original.expr1.copy();
        this.expr2 = original.expr2 == null ? null : original.expr2.copy();

        // Step 3: Adopt copied children
        if (this.expr1 != null) {
            this.expr1.setParent(this);
        }
        if (this.expr2 != null) {
            this.expr2.setParent(this);
        }
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

    @Override
    public ExprAST getExprAST(Context ctx) {
        ExprAST ast1 = expr1.getExprAST(ctx);
        ExprAST ast2 = expr2.getExprAST(ctx);

        Operator op;
        switch (operator.getId()) {
        case DIVREM:
            return new DivRemExprAST(getTypes(), ast1, ast2);

        case COND_XOR:
            return new CondOpExprAST(ast1, Operator.CondXor, ast2);

        case COLON:
            op = Operator.Else;
            break;
        case COND_ELSE:
            op = Operator.CondElse;
            break;
        case BIT_OR:
            op = Operator.BitOr;
            break;
        case BIT_XOR:
            op = Operator.BitXor;
            break;
        case BIT_AND:
            op = Operator.BitAnd;
            break;
        case I_RANGE_I:
            op = Operator.RangeII;
            break;
        case E_RANGE_I:
            op = Operator.RangeEI;
            break;
        case I_RANGE_E:
            op = Operator.RangeIE;
            break;
        case E_RANGE_E:
            op = Operator.RangeEE;
            break;
        case SHL:
            op = Operator.Shl;
            break;
        case SHR:
            op = Operator.Shr;
            break;
        case USHR:
            op = Operator.Ushr;
            break;
        case ADD:
            op = Operator.Add;
            break;
        case SUB:
            op = Operator.Sub;
            break;
        case MUL:
            op = Operator.Mul;
            break;
        case DIV:
            op = Operator.Div;
            break;
        case MOD:
            op = Operator.Mod;
            break;
        case AS:
            op = Operator.As;
            break;
        default:
            throw new UnsupportedOperationException(operator.getValueText());
        }

        TypeConstant typeResult = getType();
        if (typeResult == null) {
            typeResult = pool().typeObject();
        }
        return new RelOpExprAST(ast1, op, ast2, typeResult);
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