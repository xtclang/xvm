package org.xvm.compiler.ast;


import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Op;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_IN;
import org.xvm.asm.op.Var_N;

import org.xvm.asm.op.Var_TN;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A variable declaration statement specifies a type and a simply name for a variable, with an
 * optional initial value.
 *
 * Additionally, this can represent the combination of a variable "conditional declaration".
 */
public class VariableDeclarationStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public VariableDeclarationStatement(TypeExpression type, Token name, Expression value)
        {
        this(type, name, null, value, true);
        }

    public VariableDeclarationStatement(TypeExpression type, Token name, Token op, Expression value)
        {
        this(type, name, op, value, false);
        }

    private VariableDeclarationStatement(TypeExpression type, Token name, Token op, Expression value, Boolean standalone)
        {
        this.name  = name;
        this.type  = type;
        this.value = value;
        this.cond  = op != null && op.getId() == Token.Id.COLON;
        this.term  = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isConditional()
        {
        return cond;
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return value == null ? name.getEndPosition() : value.getEndPosition();
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
        // TODO verify conditional usage (right hand side must have a conditional return?)
        // TODO peel ref-specific annotations off of the type (e.g. "@Future")

        boolean fValid = type.validate(ctx, errs);
        if (value != null)
            {
            fValid &= value.validate(ctx, errs);
            }
        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // TODO conditional support
        // TODO DVAR support

        TypeConstant   typeV     = type.ensureTypeConstant();
        StringConstant constName = getConstantPool().ensureStringConstant((String) name.getValue());
        if (value == null)
            {
            code.add(new Var_N(typeV, constName));
            return true;
            }

        boolean fCompletes = value.canComplete();

        // tuple or array initialization: use this for NON-constant values, since with constant
        // values, we can just use VAR_IN and point to the constant itself
        if (typeV.isParamsSpecified() && !value.isConstant())
            {
            int nOp = -1;
            if (typeV.isTuple() && value instanceof TupleExpression)
                {
                // VAR_TN TYPE, STRING, #values:(rvalue)
                nOp = Op.OP_VAR_TN;
                }
            else if ((typeV.isArray() || typeV.isA(getConstantPool().ensureEcstasyTypeConstant("collections.Sequence"))) && value instanceof ListExpression)
                {
                // VAR_SN TYPE, STRING, #values:(rvalue)
                nOp = Op.OP_VAR_SN;
                }

            if (nOp >= 0)
                {
                // TODO
                }
            }

        && ( || ))
            {
            code.add(new Var_IN(typeV, constName, value.generateArgument(code, typeV, false, errs)));
            }

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append(' ')
          .append(name.getValue() == null ? name.getId().TEXT : name.getValue());

        if (value != null)
            {
            sb.append(' ')
            .append(cond ? ':' : '=')
            .append(' ')
            .append(value);
            }

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

    protected TypeExpression type;
    protected Token          name;
    protected Expression     value;
    protected boolean        cond;
    protected boolean        term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
