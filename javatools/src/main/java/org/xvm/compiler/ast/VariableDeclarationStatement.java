package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ExpressionConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Move;
import org.xvm.asm.op.Var;
import org.xvm.asm.op.Var_DN;
import org.xvm.asm.op.Var_N;

import org.xvm.compiler.Token;


/**
 * A variable declaration statement specifies a type and a simply name for a variable.
 */
public class VariableDeclarationStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     *
     * @param type  the type of the variable
     * @param name  the name of the variable
     * @param term  true iff this statement is terminated immediately after the declaration
     */
    public VariableDeclarationStatement(TypeExpression type, Token name, boolean term)
        {
        this.name = name;
        this.type = type;
        this.term = term;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the name being assigned to
     */
    public String getName()
        {
        return name.getValueText();
        }

    /**
     * @return the token holding the name
     */
    public Token getNameToken()
        {
        return name;
        }

    /**
     * @return the type being assigned to
     */
    public TypeConstant getType()
        {
        return type.getTypeConstant();
        }

    /**
     * @return after validate(), this returns true iff the variable has any Ref/Var annotations
     */
    public boolean hasRefAnnotations()
        {
        return type instanceof AnnotatedTypeExpression &&
                !((AnnotatedTypeExpression) type).getRefAnnotations().isEmpty();
        }

    /**
     * @return the Register for this VariableDeclarationStatement, if any has been created by this
     *         point in the compilation process
     */
    Register getRegister()
        {
        return m_reg;
        }

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return name.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- LValue methods ------------------------------------------------------------------------

    @Override
    protected boolean isRValue(Expression exprChild)
        {
        return exprChild != m_exprName;
        }

    @Override
    public boolean isLValueSyntax()
        {
        return true;
        }

    @Override
    public Expression getLValueExpression()
        {
        NameExpression exprName = m_exprName;
        if (exprName == null)
            {
            m_exprName = exprName = new NameExpression(this, name, m_reg);
            }
        return exprName;
        }

    @Override
    public void updateLValueFromRValueTypes(Context ctx, TypeConstant[] aTypes)
        {
        if (aTypes != null && aTypes.length >= 1)
            {
            TypeExpression exprType = this.type;
            if (exprType instanceof VariableTypeExpression)
                {
                exprType.setTypeConstant(aTypes[0]);

                if (m_reg != null)
                    {
                    m_reg.specifyActualType(aTypes[0]);
                    }
                }
            }
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        ConstantPool   pool    = pool();
        TypeExpression typeOld = type;
        TypeExpression typeNew = (TypeExpression) typeOld.validate(ctx, pool.typeType(), errs);

        if (typeNew == null)
            {
            return null;
            }

        type = typeNew;

        // create the register
        TypeConstant typeVar = typeNew.ensureTypeConstant(ctx).resolveAutoNarrowing(pool, false, null);

        m_reg = new Register(typeVar);
        ctx.registerVar(name, m_reg, errs);

        if (typeNew instanceof AnnotatedTypeExpression)
            {
            AnnotatedTypeExpression exprAnnoType = (AnnotatedTypeExpression) typeNew;

            // for DVAR registers, specify the DVAR "register type"
            // (separate from the type of the value that gets held in the register)
            List<AnnotationExpression> listRefAnnos = exprAnnoType.getRefAnnotations();
            int                        cRefAnnos    = listRefAnnos == null ? 0 : listRefAnnos.size();
            if (cRefAnnos > 0)
                {
                if (exprAnnoType.isInjected())
                    {
                    ctx.markVarWrite(name, false, errs);
                    m_reg.markEffectivelyFinal();
                    }

                boolean      fVar    = exprAnnoType.isVar();
                boolean      fConst  = true;
                TypeConstant typeReg = pool.ensureParameterizedTypeConstant(
                        fVar ? pool.typeVar() : pool.typeRef(), typeVar);
                for (int i = cRefAnnos - 1; i >= 0; --i)
                    {
                    AnnotationExpression exprAnno = listRefAnnos.get(i);

                    typeReg = pool.ensureAnnotatedTypeConstant(
                            typeReg, exprAnno.ensureAnnotation(pool));
                    fConst &= exprAnno.isConstant();
                    }
                m_reg.specifyRegType(typeReg);
                m_fConstAnno = fConst;
                }
            }

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool   pool      = pool();
        StringConstant constName = pool.ensureStringConstant((String) name.getValue());

        // declare named var
        Register reg = m_reg;
        if (reg.isDVar())
            {
            if (!m_fConstAnno)
                {
                // replace the non-constant args with the corresponding registers
                AnnotatedTypeExpression exprAnnoType = (AnnotatedTypeExpression) type;

                TypeConstant typeReg = pool.ensureParameterizedTypeConstant(
                        exprAnnoType.isVar() ? pool.typeVar() : pool.typeRef(), reg.getOriginalType());

                List<AnnotationExpression> listRefAnnotations = exprAnnoType.getRefAnnotations();
                for (int i = listRefAnnotations.size() - 1; i >= 0; --i)
                    {
                    AnnotationExpression exprAnno = listRefAnnotations.get(i);
                    Annotation           anno     = exprAnno.ensureAnnotation(pool);
                    if (!exprAnno.isConstant())
                        {
                        Constant[] aConst = anno.getParams();
                        for (int j = 0, c = aConst.length; j < c; j++)
                            {
                            Constant constArg = aConst[j];
                            if (constArg instanceof ExpressionConstant)
                                {
                                Expression exprArg = ((ExpressionConstant) constArg).getExpression();

                                Argument argArg = exprArg.generateArgument(ctx, code, true, false, errs);
                                Register regArg;
                                if (argArg instanceof Register)
                                    {
                                    regArg = (Register) argArg;
                                    }
                                else
                                    {
                                    regArg = new Register(exprArg.getType());
                                    code.add(new Var(regArg));
                                    code.add(new Move(argArg, regArg));
                                    }
                                aConst[j] = new RegisterConstant(pool, regArg);
                                }
                            }
                        anno = pool.ensureAnnotation(anno.getAnnotationClass(), aConst);
                        }
                    typeReg = pool.ensureAnnotatedTypeConstant(typeReg, anno);
                    }

                reg.specifyRegType(typeReg);
                }
            code.add(new Var_DN(reg, constName));
            }
        else
            {
            code.add(new Var_N(reg, constName));
            }

        return fReachable;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append(' ')
          .append(getName());

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

    public static final VariableDeclarationStatement[] NONE = new VariableDeclarationStatement[0];

    protected TypeExpression type;
    protected Token          name;
    protected boolean        term;

    private transient Register       m_reg;
    private transient NameExpression m_exprName;
    private transient boolean        m_fConstAnno;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type");
    }
