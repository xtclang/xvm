package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

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
     * @return the type being assigned to
     */
    public TypeConstant getType()
        {
        return type.getType();
        }

    /**
     * @return true iff the variable is known to be final
     */
    public boolean isFinal()
        {
        return m_fFinal;
        }

    /**
     * @return true iff the variable is known to be Injected
     */
    public boolean isInjected()
        {
        return m_fInjected;
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // before validating the type, disassociate any annotations that do not apply to the
        // underlying type
        ConstantPool   pool      = pool();
        TypeExpression typeOld   = type;
        TypeExpression typeEach  = typeOld;
        boolean        fVar      = false;
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

                    Constant clzAnno = annoAsm.getAnnotationClass();
                    if (clzAnno.equals(pool.clzInject()))
                        {
                        if (m_fInjected)
                            {
                            // TODO log error
                            }

                        // @Inject implies assignment & final
                        m_fFinal = m_fInjected = true;
                        }
                    else if (clzAnno.equals(pool.clzFinal()))
                        {
                        m_fFinal = true;
                        }
                    }
                }

            typeEach = typeEach.unwrapIntroductoryType();
            }

        TypeExpression typeNew = (TypeExpression) typeOld.validate(ctx, pool.typeType(), errs);
        if (typeNew != typeOld)
            {
            if (typeNew == null)
                {
                fValid = false;
                }
            else
                {
                type = typeNew;
                }
            }

        // create the register
        TypeConstant typeVar  = type.ensureTypeConstant();
        m_reg = new Register(typeVar);
        ctx.registerVar(name, m_reg, errs);
        if (m_fInjected)
            {
            ctx.markVarWrite(name, errs);
            m_reg.markEffectivelyFinal();
            }

        // for DVAR registers, specify the DVAR "register type" (separate from the type of the value
        // that gets held in the register)
        if (m_listRefAnnotations != null)
            {
            TypeConstant typeReg = pool.ensureParameterizedTypeConstant(
                    fVar ? pool.typeVar() : pool.typeRef(), typeVar);
            for (int i = m_listRefAnnotations.size()- 1; i >= 0; --i)
                {
                typeReg = pool.ensureAnnotatedTypeConstant(
                        m_listRefAnnotations.get(i).ensureAnnotation(pool), typeReg);
                }
            m_reg.specifyRegType(typeReg);
            }

        return fValid ? this : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        ConstantPool   pool      = pool();
        StringConstant constName = pool.ensureStringConstant((String) name.getValue());

        // declare named var
        if (m_reg.isDVar())
            {
            code.add(new Var_DN(m_reg, constName));
            }
        else
            {
            code.add(new Var_N(m_reg, constName));
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

    protected TypeExpression type;
    protected Token          name;
    protected boolean        term;

    private transient boolean          m_fFinal;
    private transient boolean          m_fInjected;
    private transient Register         m_reg;
    private transient List<Annotation> m_listRefAnnotations;

    private static final Field[] CHILD_FIELDS = fieldsForNames(VariableDeclarationStatement.class, "type");
    }
