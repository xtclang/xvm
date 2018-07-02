package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Var;
import org.xvm.asm.op.Var_DN;
import org.xvm.asm.op.Var_IN;
import org.xvm.asm.op.Var_N;
import org.xvm.asm.op.Var_SN;

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

        // assignment and conditional usage requires a value
        assert op == null || value != null;
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

    /**
     * @return the type being assigned to
     */
    public TypeConstant getType()
        {
        return type.getType();
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
    protected void split(Context ctx, ErrorListener errs)
        {
        if (value == null)
            {
            // this already declares and does not assign, so the split is already effectively done
            long      lPos    = getEndPosition();
            Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
            configureSplit(this, stmtNOP, errs);
            }
        else
            {
            // actually split this declaration statement into separate declaration and assignment
            AssignmentStatement stmtAsn = new AssignmentStatement(new NameExpression(name), op, value, false);

            this.op    = null;
            this.value = null;
            m_scenario = Scenario.DeclareOnly;
            configureSplit(this, stmtAsn, errs);

            notImplemented(); // TODO: it's too late to validate the assign statement;
                              // consider moving the split() call to the validate phase on the parent
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

        // before validating the type, disassociate any annotations that do not apply to the
        // underlying type
        ConstantPool   pool     = pool();
        TypeExpression typeOld  = type;
        TypeExpression typeEach = typeOld;
        boolean        fVar     = false;
        while (typeEach != null)
            {
            if (typeEach instanceof AnnotatedTypeExpression)
                {
                Annotation             annoAst  = ((AnnotatedTypeExpression) typeEach).getAnnotation();
                org.xvm.asm.Annotation annoAsm  = annoAst.ensureAnnotation(pool());
                TypeConstant           typeInto = annoAsm.getAnnotationType().getExplicitClassInto();
                if (typeInto.isIntoVariableType())
                    {
                    // steal the annotation from the type held _in_ the variable
                    ((AnnotatedTypeExpression) typeEach).disassociateAnnotation();

                    // add the annotation to the type _of_ the variable implementation itself
                    if (m_listRefAnnotations == null)
                        {
                        m_listRefAnnotations = new ArrayList<>();
                        }
                    m_listRefAnnotations.add(annoAst);
                    fVar |= typeInto.getIntoVariableType().isA(pool.typeVar());
                    }
                }

            typeEach = typeEach.unwrapIntroductoryType();
            }

        TypeExpression typeNew = (TypeExpression) typeOld.validate(ctx, pool.typeType(), errs);
        if (typeNew != typeOld)
            {
            fValid &= typeNew != null;
            if (typeNew != null)
                {
                type = typeNew;
                }
            }

        TypeConstant typeVar  = type.ensureTypeConstant();
        Expression   valueNew = null;

        // allow the r-value to resolve names based on the l-value type's contributions
        ctx = ctx.createInferringContext(typeVar);

        if (isConditional())
            {
            // what it means to be conditional:
            // 1. there is a boolean value that the RVal expression must yield that satisfies the
            //    "conditional" portion of the statement
            // 2. there is at least one additional value that the RVal expression must yield that
            //    satisfies the typeVar constraint, i.e. that is assigned to the variable
            // 3. the RVal expression is fairly flexible in what we will accept, specifically:
            // 3.1 an expression that yields multiple values of which the first is a boolean and the
            //     second is compatible with typeVar:
            //          while (Person p : iter.next())
            // 3.2 an expression that yields a tuple, of whose fields the first is a boolean and the
            //     second is compatible with typeVar:
            //          if (Person p : (True, person))
            // 3.3 an expression that yields an Iterator:
            //          while (Person p : iter)
            // 3.4 an expression that yields an Iterable:
            //          while (Person p : peopleList)
            TypeConstant[] atypeRequired = new TypeConstant[2];
            atypeRequired[0] = pool.typeBoolean();
            atypeRequired[1] = typeVar;

            TypeConstant typeRequired;
            if (value.testFitMulti(ctx, atypeRequired).isFit())
                {
                // validate scenario 3.1
                valueNew   = value.validateMulti(ctx, atypeRequired, errs);
                m_scenario = Scenario.FromCondValue;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeRequired)).isFit())
                {
                // validate scenario 3.2
                valueNew   = value.validate(ctx, typeRequired, errs);
                if (valueNew != null)
                    {
                    valueNew = new MultiValueExpression(valueNew.unpackedExpressions(errs), errs);
                    }
                m_scenario = Scenario.FromCondValue;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeIterator(), typeVar)).isFit())
                {
                // validate scenario 3.3
                valueNew   = value.validate(ctx, typeRequired, errs);
                m_scenario = Scenario.FromIterator;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeIterable(), typeVar)).isFit())
                {
                // validate scenario 3.4
                valueNew   = value.validate(ctx, typeRequired, errs);
                m_scenario = Scenario.FromIterable;
                }
            else
                {
                // nothing fits, so force the value to do the assumed default path
                valueNew = value.validateMulti(ctx, atypeRequired, errs);
                m_scenario = Scenario.FromCondValue;
                }
            }
        else if (value != null)
            {
            // what it means to be an assignment:
            // 1. there is a value that the RVal expression must yield that satisfies the type of
            //    the variable
            //          Int i = 0;
            //          Tuple<Int, String, Int> t3 = (0, "hello", 1);
            // 2. there is a value of type tuple that the RVal expression must yield, whose first
            //    field satisfies the type of the variable
            //          Int i = t3;
            //    This is supported to be consistent with multi variable declaration, e.g.
            //          (Int i, String s) = t3;
            TypeConstant typeRequired;
            if (value.testFit(ctx, typeVar).isFit())
                {
                valueNew   = value.validate(ctx, typeVar, errs);
                m_scenario = Scenario.DeclareAssign;
                }
            else if (value.testFit(ctx, typeRequired =
                    pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeVar)).isFit())
                {
                valueNew = value.validate(ctx, typeRequired, errs);
                if (valueNew != null)
                    {
                    valueNew = valueNew.unpackedExpressions(errs)[0];
                    }
                m_scenario = Scenario.DeclareAssign;
                }
            else
                {
                // nothing fits, so force the value to do the assumed default path
                valueNew   = value.validate(ctx, typeVar, errs);
                m_scenario = Scenario.DeclareAssign;
                }
            }
        else
            {
            m_scenario = Scenario.DeclareOnly;
            }

        if (valueNew != value)
            {
            fValid &= valueNew != null && valueNew.getTypeFit().isFit();
            if (valueNew != null)
                {
                value = valueNew;
                }
            }

        // use the type of the RValue to update the type of the LValue, if desired
        if (fValid)
            {
            type    = type.inferTypeFrom(typeVar);
            typeVar = type.ensureTypeConstant();
            }

        // create the register
        m_reg = new Register(typeVar);

        // for DVAR registers, specify the DVAR "register type" (separate from the type of the value
        // that gets held in the register)
        if (m_listRefAnnotations != null)
            {
            TypeConstant typeReg = pool.ensureParameterizedTypeConstant(
                    fVar ? pool.typeVar() : pool.typeRef(), typeVar);
            for (int i = m_listRefAnnotations.size()-1; i >= 0; --i)
                {
                typeReg = pool.ensureAnnotatedTypeConstant(
                        m_listRefAnnotations.get(i).ensureAnnotation(pool), typeReg);
                }
            m_reg.specifyRegType(typeReg);
            }

        ctx.registerVar(name, m_reg, errs);

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean      fCompletes = fReachable && (value == null || !value.isAborting());
        ConstantPool pool       = pool();

        if (m_scenario != Scenario.DeclareOnly)
            {
            switch (getUsage())
                {
                case While:
                case If:
                    // in the form "Type var : conditional"
                    // first, declare an unnamed Boolean variable that will hold the conditional result
                    code.add(new Var(pool.typeBoolean()));
                    Register regCond = code.lastRegister();
                    // next, declare the named variable
                    code.add(new Var_N(m_reg, pool.ensureStringConstant((String) name.getValue())));
                    // next, assign the r-value to the two variables
                    value.generateAssignments(code, new Assignable[]
                            {value.new Assignable(regCond), value.new Assignable(m_reg)}, errs);
                    code.add(getUsage() == Usage.If
                            ? new JumpFalse(regCond, getLabel())
                            : new JumpTrue (regCond, getLabel()));
                    return fCompletes;

                case For:
                    // in the form "Type var : Iterable"
                    // TODO
                    throw new UnsupportedOperationException();

                case Switch:
                    // TODO - this one might just be the same as non-conditional usage
                    // fall through
                default:
                    break;
                }
            }

        StringConstant constName = pool.ensureStringConstant((String) name.getValue());
        if (m_listRefAnnotations == null && value != null)
            {
            // constant value: declare and initialize named var
            if (value.isConstant())
                {
                Constant constVal = null;
                switch (m_scenario)
                    {
                    case DeclareAssign:
                        constVal = value.toConstant();
                        break;

                    case FromCondValue:
                        {
                        Constant[] aconst = value.toConstants();
                        if (aconst[0].equals(pool.valTrue()))
                            {
                            constVal = aconst[1];
                            }
                        break;
                        }

                    case FromIterable:
                    case FromIterator:
                        notImplemented(); // is it possible to have a constant iterator ?
                        break;

                    default:
                        throw new IllegalStateException();
                    }

                if (constVal != null)
                    {
                    code.add(new Var_IN(m_reg, constName, constVal));
                    }
                return fCompletes;
                }

            TypeConstant typeVar = m_reg.getType();

            // an optimization for a list assignment
            if (m_scenario == Scenario.DeclareAssign &&
                    value instanceof ListExpression && typeVar.isA(pool.typeSequence()))
                {
                // even though we validated the ListExpression to give us a single list value, it is
                // tolerant of us asking for the values as individual values
                List<Expression> listVals = ((ListExpression) value).getExpressions();
                int cVals = listVals.size();
                Argument[] aArgs = new Argument[cVals];
                for (int i = 0; i < cVals; ++i)
                    {
                    aArgs[i] = listVals.get(i).generateArgument(code, false, false, errs);
                    }
                code.add(new Var_SN(m_reg, constName, aArgs));
                return fCompletes;
                }
            }

        // declare named var
        if (m_reg.isDVar())
            {
            code.add(new Var_DN(m_reg, constName));
            }
        else
            {
            code.add(new Var_N(m_reg, constName));
            }

        switch (m_scenario)
            {
            case DeclareAssign:
                value.generateAssignment(code, value.new Assignable(m_reg), errs);
                break;

            case FromCondValue:
            case FromIterable:
            case FromIterator:
                notImplemented(); // is it possible to have a constant iterator ?
                break;

            case DeclareOnly:
                // all done
                break;

            default:
                throw new IllegalStateException();
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

    enum Scenario {DeclareOnly, DeclareAssign, FromCondValue, FromIterator, FromIterable}

    private transient Register m_reg;
    private transient List<Annotation> m_listRefAnnotations;
    private transient Scenario m_scenario;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type", "value");
    }
