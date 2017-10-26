package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import java.util.function.Function;

import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;
import org.xvm.asm.op.Var_IN;
import org.xvm.asm.op.Var_N;
import org.xvm.asm.op.Var_SN;
import org.xvm.asm.op.Var_TN;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;


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
    public void markAsIfCondition(Label labelElse)
        {
        assert !m_fForCond && !m_fWhileCond;
        m_fIfCond = true;
        m_label   = labelElse;
        }

    @Override
    public void markAsWhileCondition(Label labelRepeat)
        {
        assert !m_fForCond && !m_fIfCond;
        m_fWhileCond = true;
        m_label      = labelRepeat;
        }

    @Override
    public void markAsForCondition(Label labelExit)
        {
        assert !m_fIfCond && !m_fWhileCond;
        m_fForCond = true;
        m_label    = labelExit;
        }

    @Override
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        // TODO verify conditional usage (right hand side must have a conditional return?)
        // TODO peel ref-specific annotations off of the type (e.g. "@Future")
        // TODO make sure that # exprs == # type fields for tuple type
        // TODO make sure that # type fields == 1 for sequence type

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
        // TODO conditional support  - m_fIfCond m_fForCond m_label
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
            int                             nOp    = -1;
            List<Expression>                vals   = null;
            Function<Integer, TypeConstant> typeOf = null;
            if (value instanceof TupleExpression && typeV.isTuple())
                {
                // VAR_TN TYPE, STRING, #values:(rvalue)
                nOp    = Op.OP_VAR_TN;
                vals   = ((TupleExpression) value).getExpressions();
                List<TypeConstant> types = typeV.getParamTypes();
                typeOf = i -> types.get(i);
                }
            else if (value instanceof ListExpression && typeV.isA(getConstantPool()
                    .ensureEcstasyTypeConstant("collections.Sequence")))
                {
                // VAR_SN TYPE, STRING, #values:(rvalue)
                nOp    = Op.OP_VAR_SN;
                vals   = ((ListExpression) value).getExpressions();
                TypeConstant typeElement = typeV.getParamTypes().get(0);
                typeOf = i -> typeElement;
                }

            if (nOp >= 0)
                {
                int        cArgs = vals.size();
                Argument[] aArgs = new Argument[cArgs];
                for (int i = 0; i < cArgs; ++i)
                    {
                    aArgs[i] = vals.get(i).generateArgument(code, typeOf.apply(i), false, errs);
                    }
                code.add(nOp == Op.OP_VAR_TN
                        ? new Var_TN(typeV, constName, aArgs)
                        : new Var_SN(typeV, constName, aArgs));
                return fCompletes;
                }
            }

        // declare and initialize named var
        code.add(new Var_IN(typeV, constName, value.generateArgument(code, typeV, false, errs)));
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

    private boolean m_fIfCond;
    private boolean m_fWhileCond;
    private boolean m_fForCond;
    private Label   m_label;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
