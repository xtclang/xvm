package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.CompositionNode.Extends;
import org.xvm.compiler.ast.CompositionNode.Implements;
import org.xvm.compiler.ast.CompositionNode.Incorporates;

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
        return switch (getFormat())
            {
            case CLASS   -> genKeyword(location, Id.CLASS);
            case CONST   -> genKeyword(location, Id.CONST);
            case SERVICE -> genKeyword(location, Id.SERVICE);
            default      -> throw new IllegalStateException();
            };
        }

    /**
     * @return the suggested name for the anonymous inner class
     */
    public String getDefaultName()
        {
        return m_sName == null ? "Object" : m_sName;
        }

    /**
     * @return the Contributions suggested for the anonymous inner class
     */
    public List<CompositionNode> getCompositions()
        {
        return m_listCompositions == null ? Collections.EMPTY_LIST : m_listCompositions;
        }

    /**
     * @return the Annotations suggested for the anonymous inner class
     */
    public List<AnnotationExpression> getAnnotations()
        {
        return m_listAnnos == null ? Collections.EMPTY_LIST : m_listAnnos;
        }

    /**
     * @return the type parameters suggested for the anonymous inner class
     */
    public List<Parameter> getTypeParameters()
        {
        return m_listParams;
        }


    // ----- data collection -----------------------------------------------------------------------

    /**
     * @param fError  pass true to indicate that an error has been detected
     *
     * @return the error listener to use to report errors
     */
    protected ErrorListener getErrorListener(boolean fError)
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
    private void logError(String sCode, Object... aoParam)
        {
        getTypeExpression().log(getErrorListener(true), Severity.ERROR, sCode, aoParam);
        }

    /**
     * Mark that the anonymous inner class cannot be instantiated due to a declaration error.
     */
    private void markInvalid()
        {
        m_fError = true;
        }

    /**
     * Make sure that the anonymous inner class is not already required to be immutable.
     */
    private void ensureMutable()
        {
        if (m_fmt == Format.CONST)
            {
            logError(Compiler.ANON_CLASS_MUTABILITY_CONFUSED);
            }
        }

    /**
     * Mark that the anonymous inner class as defining a class of immutable objects.
     */
    protected void markImmutable()
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
    protected void addAnnotation(AnnotationExpression anno)
        {
        assert anno != null;
        ensureAnnotations().add(anno);
        }

    /**
     * Add a component contribution to the anonymous inner class.
     *
     * @param exprType  the type of the contribution to add
     */
    protected void addContribution(TypeExpression exprType)
        {
        TypeConstant type = exprType.ensureTypeConstant().resolveTypedefs();
        if (type.containsUnresolved())
            {
            logError(Compiler.NAME_UNRESOLVABLE, type.getValueString());
            }
        else
            {
            addContribution(exprType, type);
            }
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
                addContribution(exprType, type.getUnderlyingType());
                addAnnotation(new AnnotationExpression(((AnnotatedTypeConstant) type).getAnnotation(), exprType));
                return;
                }

            case UnionType:
                {
                addContribution(exprType, type.getUnderlyingType());
                addContribution(exprType, type.getUnderlyingType2());
                return;
                }

            case IntersectionType:
                exprType.log(getErrorListener(true), Severity.ERROR, Compiler.ANON_CLASS_EXTENDS_INTERSECTION);
                return;

            case VirtualChildType:  // treat it as a terminal type
                break;

            case DifferenceType:    // treat it as an interface
            case AccessType:        // treat it as an interface
            case TerminalType:      // treat it as whatever the type turns out to be
            case ParameterizedType: // whatever is parameterized, drop through and handle it
                // fall out of this switch
                break;

            default:
                throw new IllegalStateException("type=" + type);
            }

        // handling for all class & mixin types
        if (type.isExplicitClassIdentity(true))
            {
            switch (type.getExplicitClassFormat())
                {
                case CLASS:
                    setSuper(exprType, type);
                    m_fmt = Format.CLASS;
                    return;

                case ENUM:
                case ENUMVALUE:
                case PACKAGE:
                case MODULE:
                    exprType.log(getErrorListener(true), Severity.ERROR,
                            Compiler.ANON_CLASS_EXTENDS_ILLEGAL,
                            type.getExplicitClassFormat().toString().toLowerCase());
                    // fall through
                case CONST:
                    setSuper(exprType, type);
                    markImmutable();
                    m_fmt = Format.CONST;
                    return;

                case SERVICE:
                    ensureMutable();
                    setSuper(exprType, type);
                    m_fmt = Format.SERVICE;
                    return;

                case MIXIN:
                    ensureCompositions().add(new Incorporates(null,
                            genKeyword(exprType, Id.INCORPORATES), exprType, null, null));
                    return;

                case INTERFACE:
                    exprType = processFormalTypes((NamedTypeExpression) exprType,
                            type.getSingleUnderlyingClass(true));
                    // fall out of this switch
                    break;

                default:
                    throw new IllegalStateException("type=" + type + ", format=" + type.getExplicitClassFormat());
                }
            }

        // handling for all interface types
        if (m_sName == null)
            {
            m_sName = type.isExplicitClassIdentity(true)
                    ? type.getSingleUnderlyingClass(true).getName()
                    : type.getValueString().replace(" ", "");
            }
        ensureCompositions().add(new Implements(null, genKeyword(exprType, Id.IMPLEMENTS), exprType));
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * @return a mutable non-null list of annotations
     */
    private List<AnnotationExpression> ensureAnnotations()
        {
        List<AnnotationExpression> list = m_listAnnos;
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
     * Store the super class designation in the list of contributions.
     *
     * @param exprType  the type expression of the super class
     * @param type      the type of the expression
     *
     * @return false iff the specified expression cannot be used
     */
    private void setSuper(TypeExpression exprType, TypeConstant type)
        {
        assert exprType != null;
        assert type.isClassType();

        IdentityConstant idSuper = type.getSingleUnderlyingClass(false);
        m_sName = idSuper.getName();

        List<CompositionNode> list = ensureCompositions();
        if (!list.isEmpty() && list.get(0) instanceof Extends)
            {
            getTypeExpression().log(getErrorListener(true), Severity.ERROR, Compiler.ANON_CLASS_EXTENDS_MULTI,
                    list.get(0).getType().ensureTypeConstant().getValueString(), type.getValueString());
            return;
            }

        TypeExpression exprSuper = processFormalTypes((NamedTypeExpression) exprType, idSuper);

        list.add(0, new Extends(null, genKeyword(exprSuper, Id.EXTENDS), exprSuper, null));
        }

    /**
     * Process the formal types of the anonymous class declaration and produce a new
     * {@link NamedTypeExpression} that represents the corresponding generic anonymous type.
     *
     * @param exprThis  the original expression
     * @param idBase    the id of the underlying class
     *
     * @return a new {@link NamedTypeExpression} that should replace the original
     */
    private NamedTypeExpression processFormalTypes(NamedTypeExpression exprThis, IdentityConstant idBase)
        {
        // Let's imagine there is code that looks like following:
        //
        // Class C<ElType>
        //     {
        //     Map<ElType, Int> foo()
        //        {
        //        return new Map()
        //             {
        //             ...
        //             }
        //        }
        //    }
        //
        // By default it creates a synthetic (anonymous) TypeCompositionStatement
        //
        //  class Map:1
        //          implements Map<C.ElType, Int>
        //      {
        //      ...
        //      }
        //
        // which requires creation of a special type, e.g. C<Element>.foo().Map:1 to be handled
        // correctly by the run-time.
        //
        // The idea behind this method is to transform the original expression to another
        // synthetic TypeCompositionStatement that looks like this:
        //
        //  class Map:1<Map:1.Key extends C.ElType>
        //          implements Map<Map:1.Key, Int>
        //      {
        //      ...
        //      }
        //
        // and the formal type value is "injected" by the run time
        //
        // This approach also solves the issue with formal type parameters:
        //
        // static <Key> Map<Key, Int> foo(Set<Key> keys)
        //    {
        //    return new Map()
        //         {
        //         ...
        //         }
        //    }
        //

        ClassStructure clzBase = (ClassStructure) idBase.getComponent();

        int cFormalTypes = clzBase.getTypeParamCount();
        if (cFormalTypes == 0)
            {
            return exprThis;
            }

        long                 ofStart    = exprThis.getStartPosition();
        long                 ofEnd      = exprThis.getEndPosition();
        List<Parameter>      listFormal = new ArrayList<>(cFormalTypes);
        List<TypeExpression> listImpl   = new ArrayList<>(cFormalTypes);

        for (Map.Entry<StringConstant, TypeConstant> entry : clzBase.getTypeParamsAsList())
            {
            String       sName          = entry.getKey().getValue();
            TypeConstant typeConstraint = exprThis.ensureTypeConstant().resolveGenericType(sName);

            if (typeConstraint == null || !typeConstraint.containsFormalType(true))
                {
                continue;
                }

            Token          tok       = new Token(ofStart, ofStart, Id.IDENTIFIER, sName);
            TypeExpression exprParam = new NamedTypeExpression(null,
                    Collections.singletonList(tok), null, null, null, ofEnd);
            listImpl.add(exprParam);

            TypeExpression exprExtends = new NamedTypeExpression(exprThis, typeConstraint);
            listFormal.add(new Parameter(exprExtends, tok));
            }

        if (listFormal.isEmpty())
            {
            return exprThis;
            }

         m_listParams = listFormal;

        return new NamedTypeExpression(exprThis.immutable, exprThis.names,
                exprThis.access, exprThis.nonnarrow, listImpl, ofEnd);
        }

    /**
     * @return a mutable non-null list of contributions
     */
    private List<CompositionNode> ensureCompositions()
        {
        List<CompositionNode> list = m_listCompositions;
        if (list == null)
            {
            m_listCompositions = list = new ArrayList<>();
            }
        return list;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (!isValid())
            {
            sb.append("**ERROR** ");
            }

        for (AnnotationExpression anno : getAnnotations())
            {
            sb.append(anno)
              .append(' ');
            }

        sb.append(getFormat())
          .append(' ')
          .append(getDefaultName());

        if (m_listParams != null)
            {
            sb.append('<');
            for (int i = 0, c = m_listParams.size(); i < c; i++)
                {
                Parameter param = m_listParams.get(i);
                if (i > 0)
                    {
                    sb.append(", ");
                    }

                sb.append(param.getName());
                }
            sb.append('>');
            }

        for (CompositionNode comp : getCompositions())
            {
            sb.append(' ')
              .append(comp);
            }

        return sb.toString();
        }


    // ----- data members --------------------------------------------------------------------------

    /**
     * The TypeExpression for which this AnonInnerClass was created.
     */
    private final TypeExpression m_exprType;

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
    private List<AnnotationExpression> m_listAnnos;

    /**
     * The type parameters that have been collected for the AnonInnerClass.
     */
    private List<Parameter> m_listParams;

    /**
     * The contributions for the AnonInnerClass, starting with the Extends contribution, if any.
     */
    private List<CompositionNode> m_listCompositions;

    /**
     * True if any errors were noticed during the data collection for the AnonInnerClass.
     */
    private boolean m_fError;

    /**
     * The error listener to use to log any errors.
     */
    private ErrorListener m_errs;
    }
