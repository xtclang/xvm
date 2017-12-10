package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import java.util.function.Function;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Var;
import org.xvm.asm.op.Var_IN;
import org.xvm.asm.op.Var_N;
import org.xvm.asm.op.Var_SN;
import org.xvm.asm.op.Var_TN;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Expression.Assignable;

import org.xvm.util.Severity;


/**
 * A variable declaration statement specifies a type and a simply name for a variable, with an
 * optional initial value.
 *
 * Additionally, this can represent the combination of a variable "conditional declaration".
 */
public class VariableDeclarationStatement
        extends ConditionalStatement
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
        this.op    = op;
        this.term  = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the operator is ':'
     */
    public boolean isConditional()
        {
        return op != null && op.getId() == Token.Id.COLON;
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


    // ----- ConditionalStatement methods ----------------------------------------------------------

    @Override
    protected void split()
        {
        if (value == null)
            {
            // this already declares and does not assign, so the split is already effectively done
            long      lPos    = getEndPosition();
            Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
            configureSplit(this, stmtNOP);
            }
        else
            {
            // actually split this declaration statement into separate declaration and assignment
            Statement stmtDecl = new VariableDeclarationStatement(type, name, null, null, false);
            Statement stmtAsn  = new AssignmentStatement(new NameExpression(name), op, value, false);
            configureSplit(stmtDecl, stmtAsn);
            }
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        if (getUsage() != Usage.Standalone && value == null)
            {
            // right hand side must have a conditional return
            log(errs, Severity.ERROR, Compiler.VALUE_REQUIRED);
            fValid = false;
            }

        // TODO peel ref-specific annotations off of the type (e.g. "@Future")
        // TODO make sure that # exprs == # type fields for tuple type
        // TODO make sure that # type fields == 1 for sequence type

        fValid &= type.validate(ctx, null, errs);

        TypeConstant typeVar = type.ensureTypeConstant();
        if (value != null)
            {
            fValid &= value.validate(ctx, typeVar, errs);
            }

        m_reg = new Register(typeVar);
        ctx.registerVar(name, m_reg, errs);

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable && (value == null || value.isCompletable());

        switch (getUsage())
            {
            case While:
            case If:
                // in the form "Type varname : conditional"
                // first, declare an unnamed Boolean variable that will hold the conditional result
                code.add(new Var(pool().typeBoolean()));
                Register regCond = code.lastRegister();
                // next, declare the named variable
                code.add(new Var_N(m_reg, pool().ensureStringConstant((String) name.getValue())));
                // next, assign the r-value to the two variables
                value.generateAssignments(code, new Assignable[]
                        {value.new Assignable(regCond), value.new Assignable(m_reg)}, errs);
                code.add(getUsage() == Usage.If
                        ? new JumpFalse(regCond, getLabel())
                        : new JumpTrue (regCond, getLabel()));
                return fCompletes;

            case For:
                // in the form "Type varname : Iterable"
                // TODO
                throw new UnsupportedOperationException();

            case Switch:
                // TODO - this one might just be the same as non-conditional usage
                // fall through
            default:
                break;
            }

        // TODO DVAR support

        StringConstant constName = pool().ensureStringConstant((String) name.getValue());
        if (value == null)
            {
            code.add(new Var_N(m_reg, constName));
            return fCompletes;
            }

        // tuple or array initialization: use this for NON-constant values, since with constant
        // values, we can just use VAR_IN and point to the constant itself
        TypeConstant typeV = type.ensureTypeConstant();
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
                typeOf = types::get;
                }
            else if (value instanceof ListExpression && typeV.isA(pool().typeSequence()))
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
                        ? new Var_TN(m_reg, constName, aArgs)
                        : new Var_SN(m_reg, constName, aArgs));
                return fCompletes;
                }
            }

        // declare and initialize named var
        if (value.isConstant())
            {
            code.add(new Var_IN(m_reg, constName, value.generateConstant(code, typeV, errs)));
            }
        else
            {
            code.add(new Var_N(m_reg, constName));
            value.generateAssignment(code, value.new Assignable(m_reg), errs);
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
              .append(isConditional() ? ':' : '=')
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
    protected Token          op;
    protected Expression     value;
    protected boolean        term;

    private Register m_reg;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
