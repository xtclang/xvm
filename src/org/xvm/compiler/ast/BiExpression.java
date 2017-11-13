package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


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
public class BiExpression
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
    public TypeExpression toTypeExpression()
        {
        switch (operator.getId())
            {
            case ADD:
            case BIT_OR:
                return new BiTypeExpression(expr1.toTypeExpression(), operator, expr2.toTypeExpression());

            default:
                return super.toTypeExpression();
            }
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        switch (operator.getId())
            {
            case BIT_AND:
            case COND_AND:
            case BIT_OR:
            case COND_OR:
                return expr1.validateCondition(errs) && expr2.validateCondition(errs);

            default:
                return super.validateCondition(errs);
            }
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        switch (operator.getId())
            {
            case BIT_AND:
            case COND_AND:
                return expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant());

            case BIT_OR:
            case COND_OR:
                return expr1.toConditionalConstant().addOr(expr2.toConditionalConstant());

            default:
                return super.toConditionalConstant();
            }
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
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = expr1.validate(ctx, errs)
                       & expr2.validate(ctx, errs);

        // only bother checking the operation itself and calculating the "real" implicit type if
        // the sub-expressions did not have any errors
        if (fValid)
            {
            ConstantPool pool = pool();
            TypeConstant type1 = expr1.getImplicitType();
            TypeConstant type2 = expr2.getImplicitType();

            // validation of a constant expression is simpler, so do it first 
            if (isConstant())
                {
                switch (  type1.getEcstasyClassName()
                        + operator.getId().TEXT
                        + type2.getEcstasyClassName() )
                    {
                    case "Char+Char":
                    case "Char+String":
                        m_constType = pool.typeString();
                        m_constVal  = expr1.toConstant().apply(operator.getId(), expr2.toConstant());
                        return true;

                    case "String+String":
                    case "String+Char":

                    case "IntLiteral+IntLiteral":
                    case "IntLiteral-IntLiteral":
                    case "IntLiteral*IntLiteral":
                    case "IntLiteral/IntLiteral":
                    case "IntLiteral%IntLiteral":
                    case "IntLiteral&IntLiteral":
                    case "IntLiteral|IntLiteral":
                    case "IntLiteral^IntLiteral":
                    case "IntLiteral<<IntLiteral":
                    case "IntLiteral>>IntLiteral":
                    case "IntLiteral>>>IntLiteral":

                    case "Int8+IntLiteral":
                    case "Int8-IntLiteral":
                    case "Int8*IntLiteral":
                    case "Int8/IntLiteral":
                    case "Int8%IntLiteral":
                    case "Int8&IntLiteral":
                    case "Int8|IntLiteral":
                    case "Int8^IntLiteral":
                    case "Int8<<IntLiteral":
                    case "Int8>>IntLiteral":
                    case "Int8>>>IntLiteral":

                    case "UInt8+IntLiteral":
                    case "UInt8-IntLiteral":
                    case "UInt8*IntLiteral":
                    case "UInt8/IntLiteral":
                    case "UInt8%IntLiteral":
                    case "UInt8&IntLiteral":
                    case "UInt8|IntLiteral":
                    case "UInt8^IntLiteral":
                    case "UInt8<<IntLiteral":
                    case "UInt8>>IntLiteral":
                    case "UInt8>>>IntLiteral":

                    case "Int16+IntLiteral":
                    case "Int16-IntLiteral":
                    case "Int16*IntLiteral":
                    case "Int16/IntLiteral":
                    case "Int16%IntLiteral":
                    case "Int16&IntLiteral":
                    case "Int16|IntLiteral":
                    case "Int16^IntLiteral":
                    case "Int16<<IntLiteral":
                    case "Int16>>IntLiteral":
                    case "Int16>>>IntLiteral":

                    case "UInt16+IntLiteral":
                    case "UInt16-IntLiteral":
                    case "UInt16*IntLiteral":
                    case "UInt16/IntLiteral":
                    case "UInt16%IntLiteral":
                    case "UInt16&IntLiteral":
                    case "UInt16|IntLiteral":
                    case "UInt16^IntLiteral":
                    case "UInt16<<IntLiteral":
                    case "UInt16>>IntLiteral":
                    case "UInt16>>>IntLiteral":

                    case "Int32+IntLiteral":
                    case "Int32-IntLiteral":
                    case "Int32*IntLiteral":
                    case "Int32/IntLiteral":
                    case "Int32%IntLiteral":
                    case "Int32&IntLiteral":
                    case "Int32|IntLiteral":
                    case "Int32^IntLiteral":
                    case "Int32<<IntLiteral":
                    case "Int32>>IntLiteral":
                    case "Int32>>>IntLiteral":

                    case "UInt32+IntLiteral":
                    case "UInt32-IntLiteral":
                    case "UInt32*IntLiteral":
                    case "UInt32/IntLiteral":
                    case "UInt32%IntLiteral":
                    case "UInt32&IntLiteral":
                    case "UInt32|IntLiteral":
                    case "UInt32^IntLiteral":
                    case "UInt32<<IntLiteral":
                    case "UInt32>>IntLiteral":
                    case "UInt32>>>IntLiteral":

                    case "Int64+IntLiteral":
                    case "Int64-IntLiteral":
                    case "Int64*IntLiteral":
                    case "Int64/IntLiteral":
                    case "Int64%IntLiteral":
                    case "Int64&IntLiteral":
                    case "Int64|IntLiteral":
                    case "Int64^IntLiteral":
                    case "Int64<<IntLiteral":
                    case "Int64>>IntLiteral":
                    case "Int64>>>IntLiteral":

                    case "UInt64+IntLiteral":
                    case "UInt64-IntLiteral":
                    case "UInt64*IntLiteral":
                    case "UInt64/IntLiteral":
                    case "UInt64%IntLiteral":
                    case "UInt64&IntLiteral":
                    case "UInt64|IntLiteral":
                    case "UInt64^IntLiteral":
                    case "UInt64<<IntLiteral":
                    case "UInt64>>IntLiteral":
                    case "UInt64>>>IntLiteral":

                    case "Int128+IntLiteral":
                    case "Int128-IntLiteral":
                    case "Int128*IntLiteral":
                    case "Int128/IntLiteral":
                    case "Int128%IntLiteral":
                    case "Int128&IntLiteral":
                    case "Int128|IntLiteral":
                    case "Int128^IntLiteral":
                    case "Int128<<IntLiteral":
                    case "Int128>>IntLiteral":
                    case "Int128>>>IntLiteral":

                    case "UInt128+IntLiteral":
                    case "UInt128-IntLiteral":
                    case "UInt128*IntLiteral":
                    case "UInt128/IntLiteral":
                    case "UInt128%IntLiteral":
                    case "UInt128&IntLiteral":
                    case "UInt128|IntLiteral":
                    case "UInt128^IntLiteral":
                    case "UInt128<<IntLiteral":
                    case "UInt128>>IntLiteral":
                    case "UInt128>>>IntLiteral":

                    case "VarInt+IntLiteral":
                    case "VarInt-IntLiteral":
                    case "VarInt*IntLiteral":
                    case "VarInt/IntLiteral":
                    case "VarInt%IntLiteral":
                    case "VarInt&IntLiteral":
                    case "VarInt|IntLiteral":
                    case "VarInt^IntLiteral":
                    case "VarInt<<IntLiteral":
                    case "VarInt>>IntLiteral":
                    case "VarInt>>>IntLiteral":

                    case "VarUInt+IntLiteral":
                    case "VarUInt-IntLiteral":
                    case "VarUInt*IntLiteral":
                    case "VarUInt/IntLiteral":
                    case "VarUInt%IntLiteral":
                    case "VarUInt&IntLiteral":
                    case "VarUInt|IntLiteral":
                    case "VarUInt^IntLiteral":
                    case "VarUInt<<IntLiteral":
                    case "VarUInt>>IntLiteral":
                    case "VarUInt>>>IntLiteral":

                    case "Int8+Int8":
                    case "Int8-Int8":
                    case "Int8*Int8":
                    case "Int8/Int8":
                    case "Int8%Int8":
                    case "Int8&Int8":
                    case "Int8|Int8":
                    case "Int8^Int8":
                    case "Int8<<Int64":
                    case "Int8>>Int64":
                    case "Int8>>>Int64":

                    case "UInt8+UInt8":
                    case "UInt8-UInt8":
                    case "UInt8*UInt8":
                    case "UInt8/UInt8":
                    case "UInt8%UInt8":
                    case "UInt8&UInt8":
                    case "UInt8|UInt8":
                    case "UInt8^UInt8":
                    case "UInt8<<Int64":
                    case "UInt8>>Int64":
                    case "UInt8>>>Int64":

                    case "Int16+Int16":
                    case "Int16-Int16":
                    case "Int16*Int16":
                    case "Int16/Int16":
                    case "Int16%Int16":
                    case "Int16&Int16":
                    case "Int16|Int16":
                    case "Int16^Int16":
                    case "Int16<<Int64":
                    case "Int16>>Int64":
                    case "Int16>>>Int64":

                    case "UInt16+UInt16":
                    case "UInt16-UInt16":
                    case "UInt16*UInt16":
                    case "UInt16/UInt16":
                    case "UInt16%UInt16":
                    case "UInt16&UInt16":
                    case "UInt16|UInt16":
                    case "UInt16^UInt16":
                    case "UInt16<<Int64":
                    case "UInt16>>Int64":
                    case "UInt16>>>Int64":

                    case "Int32+Int32":
                    case "Int32-Int32":
                    case "Int32*Int32":
                    case "Int32/Int32":
                    case "Int32%Int32":
                    case "Int32&Int32":
                    case "Int32|Int32":
                    case "Int32^Int32":
                    case "Int32<<Int64":
                    case "Int32>>Int64":
                    case "Int32>>>Int64":

                    case "UInt32+UInt32":
                    case "UInt32-UInt32":
                    case "UInt32*UInt32":
                    case "UInt32/UInt32":
                    case "UInt32%UInt32":
                    case "UInt32&UInt32":
                    case "UInt32|UInt32":
                    case "UInt32^UInt32":
                    case "UInt32<<Int64":
                    case "UInt32>>Int64":
                    case "UInt32>>>Int64":

                    case "Int64+Int64":
                    case "Int64-Int64":
                    case "Int64*Int64":
                    case "Int64/Int64":
                    case "Int64%Int64":
                    case "Int64&Int64":
                    case "Int64|Int64":
                    case "Int64^Int64":
                    case "Int64<<Int64":
                    case "Int64>>Int64":
                    case "Int64>>>Int64":

                    case "UInt64+UInt64":
                    case "UInt64-UInt64":
                    case "UInt64*UInt64":
                    case "UInt64/UInt64":
                    case "UInt64%UInt64":
                    case "UInt64&UInt64":
                    case "UInt64|UInt64":
                    case "UInt64^UInt64":
                    case "UInt64<<Int64":
                    case "UInt64>>Int64":
                    case "UInt64>>>Int64":

                    case "Int128+Int128":
                    case "Int128-Int128":
                    case "Int128*Int128":
                    case "Int128/Int128":
                    case "Int128%Int128":
                    case "Int128&Int128":
                    case "Int128|Int128":
                    case "Int128^Int128":
                    case "Int128<<Int64":
                    case "Int128>>Int64":
                    case "Int128>>>Int64":

                    case "UInt128+UInt128":
                    case "UInt128-UInt128":
                    case "UInt128*UInt128":
                    case "UInt128/UInt128":
                    case "UInt128%UInt128":
                    case "UInt128&UInt128":
                    case "UInt128|UInt128":
                    case "UInt128^UInt128":
                    case "UInt128<<Int64":
                    case "UInt128>>Int64":
                    case "UInt128>>>Int64":

                    case "VarInt+VarInt":
                    case "VarInt-VarInt":
                    case "VarInt*VarInt":
                    case "VarInt/VarInt":
                    case "VarInt%VarInt":
                    case "VarInt&VarInt":
                    case "VarInt|VarInt":
                    case "VarInt^VarInt":
                    case "VarInt<<Int64":
                    case "VarInt>>Int64":
                    case "VarInt>>>Int64":

                    case "VarUInt+VarUInt":
                    case "VarUInt-VarUInt":
                    case "VarUInt*VarUInt":
                    case "VarUInt/VarUInt":
                    case "VarUInt%VarUInt":
                    case "VarUInt&VarUInt":
                    case "VarUInt|VarUInt":
                    case "VarUInt^VarUInt":
                    case "VarUInt<<Int64":
                    case "VarUInt>>Int64":
                    case "VarUInt>>>Int64":

                    case "FPLiteral+IntLiteral":
                    case "FPLiteral-IntLiteral":
                    case "FPLiteral*IntLiteral":
                    case "FPLiteral/IntLiteral":
                    case "FPLiteral+FPLiteral":
                    case "FPLiteral-FPLiteral":
                    case "FPLiteral*FPLiteral":
                    case "FPLiteral/FPLiteral":

                    case "Float16+IntLiteral":
                    case "Float16-IntLiteral":
                    case "Float16*IntLiteral":
                    case "Float16/IntLiteral":
                    case "Float16+FPLiteral":
                    case "Float16-FPLiteral":
                    case "Float16*FPLiteral":
                    case "Float16/FPLiteral":
                    case "Float16+Float16":
                    case "Float16-Float16":
                    case "Float16*Float16":
                    case "Float16/Float16":

                    case "Float32+IntLiteral":
                    case "Float32-IntLiteral":
                    case "Float32*IntLiteral":
                    case "Float32/IntLiteral":
                    case "Float32+FPLiteral":
                    case "Float32-FPLiteral":
                    case "Float32*FPLiteral":
                    case "Float32/FPLiteral":
                    case "Float32+Float32":
                    case "Float32-Float32":
                    case "Float32*Float32":
                    case "Float32/Float32":

                    case "Float64+IntLiteral":
                    case "Float64-IntLiteral":
                    case "Float64*IntLiteral":
                    case "Float64/IntLiteral":
                    case "Float64+FPLiteral":
                    case "Float64-FPLiteral":
                    case "Float64*FPLiteral":
                    case "Float64/FPLiteral":
                    case "Float64+Float64":
                    case "Float64-Float64":
                    case "Float64*Float64":
                    case "Float64/Float64":

                    case "Float128+IntLiteral":
                    case "Float128-IntLiteral":
                    case "Float128*IntLiteral":
                    case "Float128/IntLiteral":
                    case "Float128+FPLiteral":
                    case "Float128-FPLiteral":
                    case "Float128*FPLiteral":
                    case "Float128/FPLiteral":
                    case "Float128+Float128":
                    case "Float128-Float128":
                    case "Float128*Float128":
                    case "Float128/Float128":

                    case "VarFloat+IntLiteral":
                    case "VarFloat-IntLiteral":
                    case "VarFloat*IntLiteral":
                    case "VarFloat/IntLiteral":
                    case "VarFloat+FPLiteral":
                    case "VarFloat-FPLiteral":
                    case "VarFloat*FPLiteral":
                    case "VarFloat/FPLiteral":
                    case "VarFloat+VarFloat":
                    case "VarFloat-VarFloat":
                    case "VarFloat*VarFloat":
                    case "VarFloat/VarFloat":

                    case "Dec32+IntLiteral":
                    case "Dec32-IntLiteral":
                    case "Dec32*IntLiteral":
                    case "Dec32/IntLiteral":
                    case "Dec32+FPLiteral":
                    case "Dec32-FPLiteral":
                    case "Dec32*FPLiteral":
                    case "Dec32/FPLiteral":
                    case "Dec32+Dec32":
                    case "Dec32-Dec32":
                    case "Dec32*Dec32":
                    case "Dec32/Dec32":

                    case "Dec64+IntLiteral":
                    case "Dec64-IntLiteral":
                    case "Dec64*IntLiteral":
                    case "Dec64/IntLiteral":
                    case "Dec64+FPLiteral":
                    case "Dec64-FPLiteral":
                    case "Dec64*FPLiteral":
                    case "Dec64/FPLiteral":
                    case "Dec64+Dec64":
                    case "Dec64-Dec64":
                    case "Dec64*Dec64":
                    case "Dec64/Dec64":

                    case "Dec128+IntLiteral":
                    case "Dec128-IntLiteral":
                    case "Dec128*IntLiteral":
                    case "Dec128/IntLiteral":
                    case "Dec128+FPLiteral":
                    case "Dec128-FPLiteral":
                    case "Dec128*FPLiteral":
                    case "Dec128/FPLiteral":
                    case "Dec128+Dec128":
                    case "Dec128-Dec128":
                    case "Dec128*Dec128":
                    case "Dec128/Dec128":

                    case "VarDec+IntLiteral":
                    case "VarDec-IntLiteral":
                    case "VarDec*IntLiteral":
                    case "VarDec/IntLiteral":
                    case "VarDec+FPLiteral":
                    case "VarDec-FPLiteral":
                    case "VarDec*FPLiteral":
                    case "VarDec/FPLiteral":
                    case "VarDec+VarDec":
                    case "VarDec-VarDec":
                    case "VarDec*VarDec":
                    case "VarDec/VarDec":
                        m_constType = type1;
                        m_constVal  = expr1.toConstant();
                        try
                            {
                            m_constVal = m_constVal.apply(operator.getId(), expr2.toConstant());
                            return true;
                            }
                        catch (ArithmeticException e)
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, m_constType,
                                    getSource().toString(getStartPosition(), getEndPosition()));
                            return false;
                            }
                        
                    case "IntLiteral+FPLiteral":
                    case "IntLiteral+Int8":
                    case "IntLiteral+Int16":
                    case "IntLiteral+Int32":
                    case "IntLiteral+Int64":
                    case "IntLiteral+Int128":
                    case "IntLiteral+VarInt":
                    case "IntLiteral+UInt8":
                    case "IntLiteral+UInt16":
                    case "IntLiteral+UInt32":
                    case "IntLiteral+UInt64":
                    case "IntLiteral+UInt128":
                    case "IntLiteral+VarUInt":
                    case "IntLiteral+Float16":
                    case "IntLiteral+Float32":
                    case "IntLiteral+Float64":
                    case "IntLiteral+Float128":
                    case "IntLiteral+VarFloat":
                    case "IntLiteral+Dec32":
                    case "IntLiteral+Dec64":
                    case "IntLiteral+Dec128":
                    case "IntLiteral+VarDec":
                    case "FPLiteral+Float16":
                    case "FPLiteral+Float32":
                    case "FPLiteral+Float64":
                    case "FPLiteral+Float128":
                    case "FPLiteral+VarFloat":
                    case "FPLiteral+Dec32":
                    case "FPLiteral+Dec64":
                    case "FPLiteral+Dec128":
                    case "FPLiteral+VarDec":
                        m_constType = type2;
                        m_constVal  = expr2.toConstant();
                        try
                            {
                            m_constVal = expr1.toConstant().apply(operator.getId(), m_constVal);
                            return true;
                            }
                        catch (ArithmeticException e)
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, m_constType,
                                    getSource().toString(getStartPosition(), getEndPosition()));
                            return false;
                            }

                    case "FPLiteral==FPLiteral":
                    case "FPLiteral!=FPLiteral":
                    case "FPLiteral<FPLiteral":
                    case "FPLiteral<=FPLiteral":
                    case "FPLiteral>FPLiteral":
                    case "FPLiteral>=FPLiteral":

                    case "FPLiteral==IntLiteral":
                    case "FPLiteral!=IntLiteral":
                    case "FPLiteral<IntLiteral":
                    case "FPLiteral<=IntLiteral":
                    case "FPLiteral>IntLiteral":
                    case "FPLiteral>=IntLiteral":

                    case "IntLiteral==IntLiteral":
                    case "IntLiteral!=IntLiteral":
                    case "IntLiteral<IntLiteral":
                    case "IntLiteral<=IntLiteral":
                    case "IntLiteral>IntLiteral":
                    case "IntLiteral>=IntLiteral":
                        
                    case "IntLiteral==FPLiteral":
                    case "IntLiteral!=FPLiteral":
                    case "IntLiteral<FPLiteral":
                    case "IntLiteral<=FPLiteral":
                    case "IntLiteral>FPLiteral":
                    case "IntLiteral>=FPLiteral":
                        // TODO boolean result

                    case "FPLiteral<=>FPLiteral":
                    case "FPLiteral<=>IntLiteral":
                    case "IntLiteral<=>IntLiteral":
                    case "IntLiteral<=>FPLiteral":
                        // TODO ordered result


                        
//                  case "TerminalType":
//                  case "ImmutableType":
//                  case "AccessType":
//                  case "AnnotatedType":
//                  case "ParameterizedType":
//                  case "UnionType":
//                  case "IntersectionType":
//                  case "DifferenceType":
//                      // TODO case "type + type"
//                      // - type<t1> + type<t2> = type<t1+t2>

                    // case "t:t":
                    // the types have to be equal, or the right expression must be assignable to the
                    // type of the left expression, otherwise we cannot determine an "implicit type"
                    // (the error is deferred until the compilation stage, so if the type is pushed
                    // to this expression, it can use that, i.e. type inference)

                    // case "t?:t":
                    // the left side must be nullable, and the right expression must be assignable
                    // to the non-nullable type of the left expression, otherwise we cannot
                    // determine an "implicit type" (the error is deferred until the compilation
                    // stage, so if the type is pushed to this expression, it can use that)

                    // case "t||t":
                    // case "t&&t":
                    // case "t|t":
                    // case "t^t":
                    // case "t&t":
                    // case "t<<t":
                    // case "t>>t":
                    // case "t>>>t":

                    default:
                        operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
                        return false;
                    }
                }
            
            // TODO non-constants
            }


//                                case "FPLiteral":
//                                case "IntLiteral":
//                                    switch (sConvertTo)
//                                        {
//                                        case "Int8":
//                                            m_constType = type2;
//                                            try
//                                                {
//                                                m_constVal = ((LiteralConstant) expr1.toConstant()).toInt8Constant()
//                                                         .add((Int8Constant) expr2.toConstant());
//                                                }
//                                            catch (ArithmeticException e)
//                                                {
//                                                fValid = false;
//                                                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
//                                                        getSource().toString(getStartPosition(), getEndPosition()));
//
//                                                // use a zero value, since it's an error anyways
//                                                m_constVal = pool.ensureInt8Constant(0);
//                                                }
//                                            break;
//
//                                        case "UInt8":
//                                            m_constType = type2;
//                                            try
//                                                {
//                                                m_constVal = ((LiteralConstant) expr1.toConstant()).toUInt8Constant()
//                                                         .add((UInt8Constant) expr2.toConstant());
//                                                }
//                                            catch (ArithmeticException e)
//                                                {
//                                                fValid = false;
//                                                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
//                                                        getSource().toString(getStartPosition(), getEndPosition()));
//
//                                                // use a zero value, since it's an error anyways
//                                                m_constVal = pool.ensureUInt8Constant(0);
//                                                }
//                                            break;
//
//                                        case "Int16":
//                                        case "Int32":
//                                        case "Int64":
//                                        case "Int128":
//                                        case "VarInt":
//                                        case "UInt16":
//                                        case "UInt32":
//                                        case "UInt64":
//                                        case "UInt128":
//                                        case "VarUInt":
//                                            m_constType = type2;
//                                            try
//                                                {
//                                                LiteralConstant const1 = (LiteralConstant) expr1.toConstant();
//                                                IntConstant     const2 = (IntConstant    ) expr2.toConstant();
//                                                m_constVal = const1.toIntConstant(const2.getFormat()).add(
//                                                        const2);
//                                                }
//                                            catch (ArithmeticException e)
//                                                {
//                                                fValid = false;
//                                                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
//                                                        getSource().toString(getStartPosition(), getEndPosition()));
//
//                                                // use a zero value, since it's an error anyways
//                                                m_constVal = pool.ensureIntConstant(PackedInteger.ZERO,
//                                                        expr1.toConstant().getFormat());
//                                                }
//                                            break;
//
//                                        }
//                                    break;
//
//                                case "Int8":
//                                    if (sConvertTo.equals("IntLiteral"))
//                                        {
//                                        m_constType = type1;
//                                        try
//                                            {
//                                            m_constVal = ((Int8Constant) expr1.toConstant())
//                                                    .add((Int8Constant) expr2.toConstant());
//                                            }
//                                        catch (ArithmeticException e)
//                                            {
//                                            fValid = false;
//                                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
//                                                    type1,
//                                                    getSource().toString(getStartPosition(),
//                                                            getEndPosition()));
//
//                                            // use a zero value, since it's an error anyways
//                                            m_constVal = pool.ensureInt8Constant(0);
//                                            }
//                                        }
//                                    break;
//
//                                case "UInt8":
//                                    m_constType = type1;
//                                    try
//                                        {
//                                        m_constVal = ((UInt8Constant) expr1.toConstant())
//                                                .add((UInt8Constant) expr2.toConstant());
//                                        }
//                                    catch (ArithmeticException e)
//                                        {
//                                        fValid = false;
//                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
//                                                getSource().toString(getStartPosition(), getEndPosition()));
//
//                                        // use a zero value, since it's an error anyways
//                                        m_constVal = pool.ensureUInt8Constant(0);
//                                        }
//                                    break;
//
//                                case "Int16":
//                                case "Int32":
//                                case "Int64":
//                                case "Int128":
//                                case "VarInt":
//                                case "UInt16":
//                                case "UInt32":
//                                case "UInt64":
//                                case "UInt128":
//                                case "VarUInt":
//                                    m_constType = type1;
//                                    try
//                                        {
//                                        m_constVal = ((IntConstant) expr1.toConstant())
//                                                .add((IntConstant) expr2.toConstant());
//                                        }
//                                    catch (ArithmeticException e)
//                                        {
//                                        fValid = false;
//                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
//                                                getSource().toString(getStartPosition(), getEndPosition()));
//
//                                        // use a zero value, since it's an error anyways
//                                        m_constVal = pool.ensureIntConstant(PackedInteger.ZERO,
//                                                expr1.toConstant().getFormat());
//                                        }
//                                    break;
//
//
//                                }
//
//                            // if the constant result wasn't calculated, that is an error, but if
//                            // fValid is already set to false, that means that the error was logged
//                            if (fValid && m_constVal == null)
//                                {
//                                fValid = false;
//                                log(errs, Severity.ERROR, Compiler.INVALID_OPERATION);
//                                }
//

                // case "t-t":
                // case "t*t":
                // case "t/t":
                // case "t%t":
                // case "t/%t":
                // left-associative operators require that the operator (@Op <name>) be present
                // on the type of the left side expression.

                // case "t=t":
                // case "t!=t":
                // case "t<t":
                // case "t>t":
                // case "t<=t":
                // case "t>=t":
                // case "t<=>t":
                // comparison operators require the types to be the same

                //case "t.as(t)":
                //case "t.is(t)":
                //case "t.instanceof(t)":
                //case "t..t":

//                default:
//                    operator.log(errs, getSource(), Severity.ERROR, Compiler.FATAL_ERROR);
//                    fValid = false;
//                    break;
//                }
//            }

        return fValid;
        }

    @Override
    public int getValueCount()
        {
        // the "/%" operator results in two values
        return operator.getId() == Id.DIVMOD ? 2 : 1;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        switch (operator.getId())
            {
            case COMP_EQ:
            case COMP_NEQ:
            case COMP_LT:
            case COMP_GT:
            case COMP_LTEQ:
            case COMP_GTEQ:
            case AS:
            case IS:
            case INSTANCEOF:
                return pool().typeBoolean();

            case COMP_ORD:
                return pool().typeOrdered();

            case COLON:
                return expr1.getImplicitType();

            case COND_ELSE:
                {
                // TODO a?.b?.c : d
                return expr1.getImplicitType().nonNullable();
                }

            case DOTDOT:
                // Sequential: Range<expr1.getImplicitType()>
                // Orderable: Interval<expr1.getImplicitType()>
                // otherwise: will be detected as compiler error by validate(), so assume Orderable
                {
                ConstantPool pool = pool();
                TypeConstant typeElement  = expr1.getImplicitType();
                TypeConstant typeInterval = typeElement.isA(pool.typeSequential())
                        ? pool.typeRange()
                        : pool.typeInterval();
                return pool.ensureParameterizedTypeConstant(typeInterval,
                        new TypeConstant[] {typeElement});
                }

            case COND_OR:
            case COND_AND:
            case BIT_OR:
            case BIT_XOR:
            case BIT_AND:
            case SHL:
            case SHR:
            case USHR:
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
                // TODO examine expr1's type's methods for @Op's and determine result type from that
                return expr1.getImplicitType();

            case DIVMOD:
            default:
                throw new IllegalStateException(operator.toString());
            }
        }

    @Override
    public TypeConstant[] getImplicitTypes()
        {
        if (operator.getId() == Id.DIVMOD)
            {
            // TODO examine expr1's type's methods for @Op's and determine result types from that
            TypeConstant type = expr1.getImplicitType();
            return new TypeConstant[] {type, type};
            }

        return super.getImplicitTypes();
        }

    @Override
    public boolean isConstant()
        {
        if (expr1.isConstant() && expr2.isConstant())
            {
            return true;
            }

        if (expr1.isConstant())
            {
            switch (operator.getId())
                {
                case COLON:
                    // if the thing on the left of the colon evaluates to a constant value, then
                    // that is the result
                    return true;

                case COND_ELSE:
                    // as long as the thing on the left of the colon is not null, then the result
                    // is the thing on the left of the colon, which is constant
                    return !expr1.isConstantNull();

                case COND_OR:
                    // short circuit logic
                    return expr1.isConstantTrue();

                case COND_AND:
                    // short circuit logic
                    return expr1.isConstantFalse();

                default:
                    // - each of these could have side-effects from the right hand side, so they are
                    //   not considered to be constant, even if we know what the resulting value is
                    // - or they are comparisons, so we'd have to know both the left AND right hand
                    //   side values
                    // - or interval/range can't be constant if we don't know the upper limit
                    return false;
                }
            }

        return false;
        }

    @Override
    public Constant generateConstant(Code code, TypeConstant type, ErrorListener errs)
        {
        if (isConstant())
            {
            ConstantPool pool = pool();
            switch (operator.getId())
                {
                case COLON:
                    // TODO
                    throw new UnsupportedOperationException();

                case COND_ELSE:
                    return (expr1.isConstantNull() ? expr2 : expr1).generateConstant(code, type, errs);

                case BIT_OR:
                    if (type.equals(pool.typeIntLiteral()))
                        {
                        Argument arg1 = expr1.generateConstant(code, type, errs);
                        Argument arg2 = expr1.generateConstant(code, type, errs);
                        if (arg1 instanceof LiteralConstant && arg2 instanceof LiteralConstant)
                            {
                            PackedInteger pi1      = ((LiteralConstant) arg1).getPackedInteger();
                            PackedInteger pi2      = ((LiteralConstant) arg2).getPackedInteger();
                            int           radix    = ((LiteralConstant) arg1).getIntRadix();
                            PackedInteger piResult = pi1.isBig() || pi2.isBig()
                                    ? new PackedInteger(pi1.getBigInteger().or(pi2.getBigInteger()))
                                    : PackedInteger.valueOf(pi1.getLong() | pi2.getLong());
                            return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(radix));
                            }
                        }
                    else if (type.equals(pool.typeInt()))
                        {
                        Argument arg1 = expr1.generateConstant(code, type, errs);
                        Argument arg2 = expr1.generateConstant(code, type, errs);
                        if (arg1 instanceof LiteralConstant && arg2 instanceof LiteralConstant)
                            {
                            PackedInteger pi1      = ((LiteralConstant) arg1).getPackedInteger();
                            PackedInteger pi2      = ((LiteralConstant) arg2).getPackedInteger();
                            int           radix    = ((LiteralConstant) arg1).getIntRadix();
                            PackedInteger piResult = pi1.isBig() || pi2.isBig()
                                    ? new PackedInteger(pi1.getBigInteger().or(pi2.getBigInteger()))
                                    : PackedInteger.valueOf(pi1.getLong() | pi2.getLong());
                            return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(radix));
                            }
                        }
                    // else if (...) TODO Int and UInt 8-128 and Var length
                    // else if (constType.equals())     TODO type | type

                    // fall through for logical boolean "or"
                case COND_OR:
                    if (type.equals(pool.typeBoolean()))
                        {
                        // if the first expression is a boolean true, then the result is a boolean
                        // true;  otherwise if the second expression is a boolean true, then the
                        // result is a boolean true; otherwise the result is a boolean false
                        Constant constVal = expr1.generateConstant(code, type, errs);
                        return pool.valTrue().equals(constVal)
                                ? constVal
                                : expr2.generateConstant(code, type, errs);
                        }
                    break;

                case BIT_AND:
                    // TODO integer

                    // fall through for logical boolean "and"
                case COND_AND:
                    if (type.equals(pool.typeBoolean()))
                        {
                        // if the first expression is a boolean false, then the result is a boolean
                        // false;  otherwise if the second expression is a boolean true, then the
                        // result is a boolean true; otherwise the result is a boolean false
                        Constant constVal = expr1.generateConstant(code, type, errs);
                        return pool.valFalse().equals(constVal)
                                ? constVal
                                : expr2.generateConstant(code, type, errs);
                        }
                    break;

                case BIT_XOR:
                case COMP_EQ:
                case COMP_NEQ:
                case COMP_LT:
                case COMP_GT:
                case COMP_LTEQ:
                case COMP_GTEQ:
                case COMP_ORD:
                case AS:
                case IS:
                case INSTANCEOF:
                case DOTDOT:
                case SHL:
                case SHR:
                case USHR:

                case ADD:
                    // applies to:
                    // Int8/16/32/64/128, VarInt
                    // UInt8/16/32/64/128, VarUInt
                    // Dec32/64/128, VarDec
                    // Float16/32/64/128, VarFloat
                    // String
                    // Type

                case SUB:
                case MUL:

                case DIVMOD:
                    // TODO same as DIV? or a Tuple result? and if so, then shouldn't all support that?
                    // fall through
                case DIV:

                case MOD:
                }
            }

        return super.generateConstant(code, type, errs);
        }

//            switch (operator.getId())
//                {
//                case COLON:
//                case COND_ELSE:
//                case COND_OR:
//                case COND_AND:
//                case BIT_OR:
//                case BIT_XOR:
//                case BIT_AND:
//                case COMP_EQ:
//                case COMP_NEQ:
//                case COMP_LT:
//                case COMP_GT:
//                case COMP_LTEQ:
//                case COMP_GTEQ:
//                case COMP_ORD:
//                case AS:
//                case IS:
//                case INSTANCEOF:
//                case DOTDOT:
//                case SHL:
//                case SHR:
//                case USHR:
//                case ADD:
//                case SUB:
//                case MUL:
//                case DIV:
//                case MOD:
//                case DIVMOD:
//                }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr1)
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(expr2);

        return sb.toString();
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

    /**
     * Cached type, post-validate.
     */
    private TypeConstant m_constType;
    /**
     * Cached constant, post-validate.
     */
    private Constant m_constVal;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(BiExpression.class, "expr1", "expr2");
    }
