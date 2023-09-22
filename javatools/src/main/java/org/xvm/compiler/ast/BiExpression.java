package org.xvm.compiler.ast;


import java.lang.reflect.Field;

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
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BiExpression(Expression expr1, Token operator, Expression expr2)
        {
        this.expr1    = expr1;
        this.operator = operator;
        this.expr2    = expr2;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the first sub-expression of the bi-expression
     */
    public Expression getExpression1()
        {
        return expr1;
        }

    /**
     * @return the operator for the bi-expression
     */
    public Token getOperator()
        {
        return operator;
        }

    /**
     * @return the second sub-expression of the bi-expression
     */
    public Expression getExpression2()
        {
        return expr2;
        }

    @Override
    public long getStartPosition()
        {
        return expr1.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr2.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isShortCircuiting()
        {
        return expr1.isShortCircuiting() || expr2.isShortCircuiting();
        }

    @Override
    public boolean isCompletable()
        {
        return expr1.isCompletable() && expr2.isCompletable();
        }

    @Override
    public ExprAST getExprAST()
        {
        ExprAST ast1 = expr1.getExprAST();
        ExprAST ast2 = expr2.getExprAST();

        Operator op;
        switch (operator.getId())
            {
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
        if (typeResult == null)
            {
            typeResult = pool().typeObject();
            }
        return new RelOpExprAST(ast1, op, ast2, typeResult);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return String.valueOf(expr1) + ' ' + operator.getId().TEXT + ' ' + expr2;
        }

    @Override
        public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr1;
    protected Token      operator;
    protected Expression expr2;

    private static final Field[] CHILD_FIELDS = fieldsForNames(BiExpression.class, "expr1", "expr2");
    }