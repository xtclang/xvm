package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;


/**
 * The AnonInnerClass represents the suggested shape for an anonymous inner class.
 */
public class AnonInnerClass
    {
    public AnonInnerClass(TypeExpression expr, ErrorListener errs)
        {
        m_exprType = expr;
        m_errs     = errs;
        }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the original type expression that serves as the basis for the anonymous inner class
     */
    public TypeExpression getTypeExpression()
        {
        return m_exprType;
        }

    /**
     * @return true iff there were no errors in the creation of the AnonInnerClass
     */
    public boolean isValid()
        {
        return !m_fError;
        }

    /**
     * @return one of: CLASS, CONST, SERVICE
     */
    public Component.Format getFormat()
        {
        return m_fmt == null ? Format.CLASS : m_fmt;
        }

    /**
     * @return the suggested name for the anonymous inner class
     */
    public String getDefaultName()
        {
        return m_sName;
        }

    /**
     * @return the Contributions suggested for the anonymous inner class
     */
    public List<Contribution> getContributions()
        {
        return m_listContribs == null ? Collections.EMPTY_LIST : m_listContribs;
        }

    /**
     * @return the Annotations suggested for the anonymous inner class
     */
    public List<org.xvm.asm.Annotation> getAnnotations()
        {
        return m_listAnnos == null ? Collections.EMPTY_LIST : m_listAnnos;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @param fError  pass true to indicate that an error has been detected
     *
     * @return the error listener to use to report errors
     */
    ErrorListener getErrorListener(boolean fError)
        {
        if (fError)
            {
            markInvalid();
            }

        return m_errs;
        }

    /**
     * Mark that the anonymous inner class cannot be instantiated due to a declaration error.
     */
    void markInvalid()
        {
        m_fError = true;
        }

    /**
     * Make sure that the anonymous inner class is not already required to be immutable.
     */
    void ensureMutable()
        {
        if (m_fmt == Component.Format.CONST)
            {
            m_fError = true;
            }
        }

    /**
     * Mark that the anonymous inner class as defining a class of immutable objects.
     */
    void markImmutable()
        {
        if (m_fmt == Component.Format.SERVICE)
            {
            m_fError = true;
            }
        else
            {
            m_fmt = Component.Format.CONST;
            }
        }

    /**
     * Add an annotation to the definition of the anonymous inner class.
     *
     * @param anno  the annotation
     */
    void addAnnotation(Annotation anno)
        {
        assert anno != null;
        ensureAnnotations().add(anno);
        }

    /**
     * Add a component contribution to the anonymous inner class.
     *
     * @param type  the type of the contribution to add
     */
    void addContribution(TypeConstant type)
        {
        if (type.isExplicitClassIdentity(true))
            {
            switch (type.getExplicitClassFormat())
                {
                case CLASS:
                    setSuper(type);
                    m_fmt = Component.Format.CLASS;
                    break;

                case ENUM:
                case ENUMVALUE:
                case PACKAGE:
                case MODULE:
                    m_fError = true;
                case CONST:
                    setSuper(type);
                    m_fmt = Component.Format.CONST;
                    break;

                case SERVICE:
                    ensureMutable();
                    setSuper(type);
                    m_fmt = Component.Format.SERVICE;
                    break;

                case MIXIN:
                    ensureContributions().add(new Contribution(Component.Composition.Incorporates, type));
                    break;

                case INTERFACE:
                    ensureContributions().add(new Contribution(Component.Composition.Implements, type));
                    break;

                case TYPEDEF:
                case PROPERTY:
                case METHOD:
                case RSVD_C:
                case RSVD_D:
                case MULTIMETHOD:
                case FILE:
                    throw new IllegalStateException("type=" + type);
                }
            }
        }

    private void setSuper(TypeConstant type)
        {
        assert type != null;
        assert type.isClassType();

        List<Contribution> list = ensureContributions();
        if (!list.isEmpty() && list.get(0).getComposition() == Component.Composition.Extends)
            {
            m_fError = true;
            }
        list.add(0, new Contribution(Component.Composition.Extends, type));
        }

    private List<Contribution> ensureContributions()
        {
        List<Contribution> list = m_listContribs;
        if (list == null)
            {
            m_listContribs = list = new ArrayList<>();
            }
        return list;
        }

    private List<Annotation> ensureAnnotations()
        {
        List<Annotation> list = m_listAnnos;
        if (list == null)
            {
            m_listAnnos = list = new ArrayList<>();
            }
        return list;
        }


    // ----- data members --------------------------------------------------------------------------

    private TypeExpression     m_exprType;
    private Component.Format   m_fmt;
    private String             m_sName;
    private List<Annotation>   m_listAnnos;
    private List<Contribution> m_listContribs;
    private boolean            m_fError;
    private ErrorListener      m_errs;
    }
