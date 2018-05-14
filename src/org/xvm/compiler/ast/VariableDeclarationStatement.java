package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
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
import org.xvm.compiler.ast.Expression.TuplePref;
import org.xvm.compiler.ast.Expression.TypeFit;

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

    /**
     * @return the name being assigned to
     */
    public String getName()
        {
        return name == null ? "null" : name.getValueText();
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
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // right hand side must have a value if this is not a standalone declaration
        if (getUsage() != Usage.Standalone && value == null)
            {
            log(errs, Severity.ERROR, Compiler.VALUE_REQUIRED);
            fValid = false;
            }

        ConstantPool   pool    = pool();
        TypeExpression typeNew = (TypeExpression) type.validate(ctx, pool.typeType(), TuplePref.Rejected, errs);
        if (typeNew != type)
            {
            fValid &= typeNew != null;
            if (typeNew != null)
                {
                type = typeNew;
                }
            }

        TypeConstant typeVar = type.ensureTypeConstant();
        if (value != null)
            {
            if (typeVar.isTuple())
                {
                TypeFit fitTup = value.testFit(ctx, typeVar, TuplePref.Rejected);
                TypeFit fitSep = TypeFit.NoFit;
                if (typeVar.isParamsSpecified())
                    {
                    fitSep = value.testFitMulti(ctx, typeVar.getParamTypesArray(), TuplePref.Desired);
                    }

                if (fitSep.isFit() && (!fitTup.isFit() || fitTup.isPacking()))
                    {
                    // special case: we'll do the packing ourselves
                    m_fPackingInit = true;
                    }
                }

            Expression valueNew = m_fPackingInit
                    ? value.validateMulti(ctx, typeVar.getParamTypesArray(), TuplePref.Rejected, errs)
                    : value.validate(ctx, typeVar, TuplePref.Rejected, errs);
            if (valueNew != value)
                {
                fValid &= valueNew != null;
                if (valueNew != null)
                    {
                    value = valueNew;
                    }
                }

            // conditional declarations (e.g. inside a while clause) must yield a boolean as the
            // first value
            if (isConditional() && !value.isTypeBoolean())
                {
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool.typeBoolean().getValueString(),
                        value.isVoid() ? "void" : value.getTypes()[0].getValueString());
                fValid = false;
                }

            // use the type of the RValue to update the type of the LValue, if desired
            if (fValid)
                {
                typeVar = m_fPackingInit
                        ? pool.ensureParameterizedTypeConstant(pool.typeTuple(), value.getTypes())
                        : value.getType();

                type    = type.inferTypeFrom(typeVar);
                typeVar = type.ensureTypeConstant();
                }
            }

        m_reg = new Register(typeVar);
        ctx.registerVar(name, m_reg, errs);

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable && (value == null || !value.isAborting());

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

        // no value: declare named var
        StringConstant constName = pool().ensureStringConstant((String) name.getValue());
        if (value == null)
            {
            code.add(new Var_N(m_reg, constName));
            return fCompletes;
            }

        // constant value: declare and initialize named var
        if (value.hasConstantValue())
            {
            Constant constVal;
            if (m_fPackingInit)
                {
                ConstantPool pool = pool();
                constVal = pool.ensureTupleConstant(pool.ensureParameterizedTypeConstant(
                        pool.typeTuple(), value.getTypes()), value.toConstants());
                }
            else
                {
                constVal = value.toConstant();
                }
            code.add(new Var_IN(m_reg, constName, constVal));
            return fCompletes;
            }

        // declare and initialize named var
        TypeConstant typeVar = m_reg.getType();
        if (m_fPackingInit)
            {
            Argument[] aArgs = value.generateArguments(code, false, errs);
            code.add(new Var_TN(m_reg, constName, aArgs));
            }
        else if (value instanceof ListExpression && typeVar.isA(pool().typeSequence()))
            {
            // even though we validated the ListExpression to give us a single list value, it is
            // tolerant of us asking for the values as individual values
            List<Expression> listVals = ((ListExpression) value).getExpressions();
            int              cVals    = listVals.size();
            Argument[]       aArgs    = new Argument[cVals];
            for (int i = 0; i < cVals; ++i)
                {
                aArgs[i] = listVals.get(i).generateArgument(code, false, false, false, errs);
                }
            code.add(new Var_SN(m_reg, constName, aArgs));
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
    private boolean  m_fPackingInit;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
