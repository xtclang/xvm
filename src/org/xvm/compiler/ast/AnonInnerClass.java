package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Component.Format;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Composition.Extends;
import org.xvm.compiler.ast.Composition.Implements;
import org.xvm.compiler.ast.Composition.Incorporates;

import org.xvm.util.Severity;


/**
 * The AnonInnerClass represents the suggested shape for an anonymous inner class.
 */
public class AnonInnerClass
    {
    /**
     * Construct an AnonInnerClass data collector.
     *
     * @param expr  the type expression for which this AnonInnerClass is collecting data
     * @param errs  the error listener to use to log errors to
     */
    public AnonInnerClass(TypeExpression expr, ErrorListener errs)
        {
        assert expr != null;
        assert errs != null;

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
    public Format getFormat()
        {
        return m_fmt == null ? Format.CLASS : m_fmt;
        }

    /**
     * @return a token for "class", "const", or "service"
     */
    public Token getCategory()
        {
        AstNode location = getTypeExpression();
        switch (getFormat())
            {
            case CLASS:   return genKeyword(location, Id.CLASS);
            case CONST:   return genKeyword(location, Id.CONST);
            case SERVICE: return genKeyword(location, Id.SERVICE);

            default:
                throw new IllegalStateException();
            }
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
    public List<Composition> getCompositions()
        {
        return m_listCompositions == null ? Collections.EMPTY_LIST : m_listCompositions;
        }

    /**
     * @return the Annotations suggested for the anonymous inner class
     */
    public List<Annotation> getAnnotations()
        {
        return m_listAnnos == null ? Collections.EMPTY_LIST : m_listAnnos;
        }


    // ----- data collection -----------------------------------------------------------------------

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
     * Helper to log an error.
     *
     * @param sCode    the error code that identifies the error message
     * @param aoParam  the parameters for the error message; may be null
     */
    void logError(String sCode, Object... aoParam)
        {
        getTypeExpression().log(getErrorListener(true), Severity.ERROR, sCode, aoParam);
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
        if (m_fmt == Format.CONST)
            {
            logError(Compiler.ANON_CLASS_MUTABILITY_CONFUSED);
            }
        }

    /**
     * Mark that the anonymous inner class as defining a class of immutable objects.
     */
    void markImmutable()
        {
        if (m_fmt == Format.SERVICE)
            {
            logError(Compiler.ANON_CLASS_MUTABILITY_CONFUSED);
            }
        else
            {
            m_fmt = Format.CONST;
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
     * @param exprType  the type of the contribution to add
     */
    void addContribution(TypeExpression exprType)
        {
        TypeConstant type = exprType.ensureTypeConstant().resolveTypedefs();
        assert !type.containsUnresolved();
        addContribution(exprType, type);
        }

    /**
     * Add a component contribution to the anonymous inner class.
     *
     * @param exprType  the AST type (used primarily for error reporting)
     * @param type      the type of the contribution to add
     */
    private void addContribution(TypeExpression exprType, TypeConstant type)
        {
        // this is largely duplicated from what the TypeExpression classes do, primarily in order
        // to handle the situation in which a typedef expands to something that would have been
        // represented by a tree of specialized TypeExpression classes
        switch (type.getFormat())
            {
            case ImmutableType:
                // unwrap the type
                addContribution(exprType, type.getUnderlyingType());
                markImmutable();
                return;

            case AnnotatedType:
                {
                TypeConstant typeNext = type.getUnderlyingType();
                long lPos = exprType.getStartPosition();
                NamedTypeExpression fake = new NamedTypeExpression(null, Collections.singletonList(
                        genToken(exprType, Id.IDENTIFIER, type.getValueString())) )
                addAnnotation(new Annotation(fake, anno, lPos, lPos));
                return;
                }

            case ParameterizedType:
                {
                // TODO
                throw new UnsupportedOperationException();
                }

            case UnionType:
                {
                // TODO
                throw new UnsupportedOperationException();
                }

            case IntersectionType:
                exprType.log(getErrorListener(true), Severity.ERROR,
                        Compiler.ANON_CLASS_EXTENDS_INTERSECTION);
                return;

            default:
                throw new IllegalStateException("type=" + type);

            case TerminalType:  // treat it as whatever the type turns out to be
            case DifferenceType:// treat it as an interface
            case AccessType:    // treat it as an interface
                break;
            }

        if (type.isExplicitClassIdentity(true))
            {
            switch (type.getExplicitClassFormat())
                {
                case CLASS:
                    setSuper(exprType);
                    m_fmt = Format.CLASS;
                    break;

                case ENUM:
                case ENUMVALUE:
                case PACKAGE:
                case MODULE:
                    exprType.log(getErrorListener(true), Severity.ERROR,
                            Compiler.ANON_CLASS_EXTENDS_ILLEGAL,
                            type.getExplicitClassFormat().toString().toLowerCase());
                case CONST:
                    setSuper(exprType);
                    markImmutable();
                    break;

                case SERVICE:
                    ensureMutable();
                    setSuper(exprType);
                    m_fmt = Format.SERVICE;
                    break;

                case MIXIN:
                    ensureCompositions().add(new Incorporates(null,
                            genKeyword(exprType, Id.INCORPORATES), exprType, null, null));
                    break;

                case INTERFACE:
                    ensureCompositions().add(new Implements(null,
                            genKeyword(exprType, Id.IMPLEMENTS), exprType));
                    break;

                default:
                    throw new IllegalStateException("type=" + type + ", format=" + type.getExplicitClassFormat());
                }
            }
        //else if (type instanceof )
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return a mutable non-null list of annotations
     */
    private List<Annotation> ensureAnnotations()
        {
        List<Annotation> list = m_listAnnos;
        if (list == null)
            {
            m_listAnnos = list = new ArrayList<>();
            }
        return list;
        }

    /**
     * Create a fake token.
     *
     * @param location  the location at which to create the token
     * @param id        the identity of the token
     *
     * @return the token
     */
    private Token genKeyword(AstNode location, Id id)
        {
        long lPos = location.getStartPosition();
        return new Token(lPos, lPos, id);
        }

    /**
     * Create a fake token.
     *
     * @param location  the location at which to create the token
     * @param id        the identity of the token
     * @param oVal      the value of the token
     *
     * @return the token
     */
    private Token genToken(AstNode location, Id id, Object oVal)
        {
        long lPos = location.getStartPosition();
        return new Token(lPos, lPos, id, oVal);
        }

    /**
     * Store the super class designation in the list of contributions.
     *
     * @param exprType  the type of the super class
     */
    private void setSuper(TypeExpression exprType)
        {
        assert exprType != null;

        TypeConstant type = exprType.ensureTypeConstant();
        assert type.isClassType();

        List<Composition> list = ensureCompositions();
        if (!list.isEmpty() && list.get(0) instanceof Extends)
            {
            getTypeExpression().log(getErrorListener(true), Severity.ERROR, Compiler.ANON_CLASS_EXTENDS_MULTI,
                    list.get(0).getType().ensureTypeConstant().getValueString(), type.getValueString());
            }

        list.add(0, new Extends(null, genKeyword(exprType, Id.EXTENDS), exprType, null));
        }

    /**
     * @return a mutable non-null list of contributions
     */
    private List<Composition> ensureCompositions()
        {
        List<Composition> list = m_listCompositions;
        if (list == null)
            {
            m_listCompositions = list = new ArrayList<>();
            }
        return list;
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The TypeExpression for which this AnonInnerClass was created.
     */
    private TypeExpression m_exprType;

    /**
     * The format that this AnonInnerClass has settled on, one of SERVICE, CONST, or CLASS, or null
     * if no decision has been made.
     */
    private Format m_fmt;

    /**
     * The name that the AnonInnerClass has settled on as a default, or null if none.
     */
    private String m_sName;

    /**
     * The annotations that have been collected for the AnonInnerClass.
     */
    private List<Annotation> m_listAnnos;

    /**
     * The contributions for the AnonInnerClass, starting with the Extends contribution, if any.
     */
    private List<Composition> m_listCompositions;

    /**
     * True if any errors were noticed during the data collection for the AnonInnerClass.
     */
    private boolean m_fError;

    /**
     * The error listener to use to log any errors.
     */
    private ErrorListener m_errs;
    }
