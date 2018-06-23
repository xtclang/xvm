package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.compiler.Token;


/**
 * Generic expression for something that follows the pattern "expression operator expression".
 *
 * <ul>
 * <li><tt>COLON:      ":"</tt> - an "else" for nullability checks</li>
 * <li><tt>COND_ELSE:  "?:"</tt> - the "elvis" operator</li>
 * <li><tt>COND_OR:    "||"</tt> - </li>
 * <li><tt>COND_AND:   "&&"</tt> - </li>
 * <li><tt>BIT_OR:     "|"</tt> - </li>
 * <li><tt>BIT_XOR:    "^"</tt> - </li>
 * <li><tt>BIT_AND:    "&"</tt> - </li>
 * <li><tt>COMP_EQ:    "=="</tt> - </li>
 * <li><tt>COMP_NEQ:   "!="</tt> - </li>
 * <li><tt>COMP_LT:    "<"</tt> - </li>
 * <li><tt>COMP_GT:    "><tt>"</tt> - </li>
 * <li><tt>COMP_LTEQ:  "<="</tt> - </li>
 * <li><tt>COMP_GTEQ:  ">="</tt> - </li>
 * <li><tt>COMP_ORD:   "<=><tt>"</tt> - </li>
 * <li><tt>AS:         "as"</tt> - </li>
 * <li><tt>IS:         "is"</tt> - </li>
 * <li><tt>INSTANCEOF: "instanceof"</tt> - </li>
 * <li><tt>DOTDOT:     ".."</tt> - </li>
 * <li><tt>SHL:        "<<"</tt> - </li>
 * <li><tt>SHR:        ">><tt>"</tt> - </li>
 * <li><tt>USHR:       ">>><tt>"</tt> - </li>
 * <li><tt>ADD:        "+"</tt> - </li>
 * <li><tt>SUB:        "-"</tt> - </li>
 * <li><tt>MUL:        "*"</tt> - </li>
 * <li><tt>DIV:        "/"</tt> - </li>
 * <li><tt>MOD:        "%"</tt> - </li>
 * <li><tt>DIVMOD:     "/%"</tt> - </li>
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
    public boolean isAborting()
        {
        return expr1.isAborting() || expr2.isAborting();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given two types that should have some point of immediate commonality, select a target type.
     *
     * @param type1  the first type
     * @param type2  the second type
     * @param errs   an error list
     *
     * @return a target type or null
     */
    public static TypeConstant selectType(TypeConstant type1, TypeConstant type2, ErrorListener errs)
        {
        if (type1 == null && type2 == null)
            {
            return null;
            }

        if (type1 != null && type2 != null)
            {
            if (type2.isAssignableTo(type1))
                {
                return type1;
                }

            if (type1.isAssignableTo(type2))
                {
                return type2;
                }

            TypeInfo info1 = type1.ensureTypeInfo(errs);
            if (info1.getFormat() == Format.ENUMVALUE && type2.isAssignableTo(info1.getExtends()))
                {
                return info1.getExtends();
                }

            TypeInfo info2 = type2.ensureTypeInfo(errs);
            if (info2.getFormat() == Format.ENUMVALUE && type1.isAssignableTo(info2.getExtends()))
                {
                return info2.getExtends();
                }

            return null;
            }

        TypeConstant typeResult = type1 == null ? type2 : type1;
        TypeInfo     typeinfo   = typeResult.ensureTypeInfo(errs);
        return typeinfo.getFormat() == Format.ENUMVALUE
                ? typeinfo.getExtends()
                : typeResult;
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
