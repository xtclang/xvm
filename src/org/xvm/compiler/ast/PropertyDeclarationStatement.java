package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A property declaration.
 */
public class PropertyDeclarationStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public PropertyDeclarationStatement(long             lStartPos,
                                        long             lEndPos,
                                        Expression       condition,
                                        List<Token>      modifiers,
                                        List<Annotation> annotations,
                                        TypeExpression   type,
                                        Token            name,
                                        Expression       value,
                                        StatementBlock   body,
                                        Token            doc)
        {
        super(lStartPos, lEndPos);

        // separate annotations from the type into their own list
        TypeExpression typePrev = null;
        TypeExpression typeNext = type;
        while (typeNext.isIntroductoryType())
            {
            if (typeNext instanceof AnnotatedTypeExpression)
                {
                // remove the annotation from the type chain, and add it to the list of annotations
                Annotation anno = ((AnnotatedTypeExpression) type).getAnnotation();
                anno.setParent(this);
                if (annotations == null || annotations.isEmpty())
                    {
                    annotations = new ArrayList<>();
                    }
                annotations.add(anno);

                // unlink the annotated type from the type expression chain, and relink the chain
                typeNext = typeNext.unwrapIntroductoryType();
                if (typePrev != null)
                    {
                    typePrev.replaceIntroducedType(typeNext);
                    }
                typeNext.setParent(typePrev);
                }
            else
                {
                typePrev = typeNext;
                typeNext = typeNext.unwrapIntroductoryType();
                }
            }

        this.condition   = condition;
        this.modifiers   = modifiers;
        this.annotations = annotations;
        this.type        = type;
        this.name        = name;
        this.value       = value;
        this.body        = body;
        this.doc         = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public String getName()
        {
        return name.getValueText();
        }

    public TypeExpression getType()
        {
        return type;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        // the property's type is allowed to auto-narrow, but only for non-static properties
        // belonging to a non-singleton class (even if nested inside another property)
        return type == this.type && getComponent().isAutoNarrowingAllowed();
        }

    /**
     * @return true iff the property is declared as static
     */
    public boolean isStatic()
        {
        // properties inside a method are ALWAYS specified as static, but NEVER actually static (in
        // the "constant property" sense)
        if (getParent().getComponent() instanceof MethodStructure)
            {
            return false;
            }

        List<Token> list = modifiers;
        if (list != null && !list.isEmpty())
            {
            for (Token token : list)
                {
                if (token.getId() == Token.Id.STATIC)
                    {
                    return true;
                    }
                }
            }

        return false;
        }

    @Override
    public Access getDefaultAccess()
        {
        // properties are *not* taking the parent's access by default
        Access  access = getAccess(modifiers);
        return access == null ? Access.PUBLIC : access;
        }

    public Access getAccess2()
        {
        if (modifiers != null && !modifiers.isEmpty())
            {
            Access access = null;
            for (Token modifier : modifiers)
                {
                switch (modifier.getId())
                    {
                    case PUBLIC:
                        if (access == null)
                            {
                            access = Access.PUBLIC;
                            }
                        else
                            {
                            return Access.PUBLIC;
                            }
                        break;

                    case PROTECTED:
                        if (access == null)
                            {
                            access = Access.PROTECTED;
                            }
                        else
                            {
                            return Access.PROTECTED;
                            }
                        break;

                    case PRIVATE:
                        if (access == null)
                            {
                            access = Access.PRIVATE;
                            }
                        else
                            {
                            return Access.PRIVATE;
                            }
                        break;

                    }
                }
            }

        return null;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    /**
     * Mark this property declaration as "synthetic".
     */
    public void markSynthetic()
        {
        fSynthetic = true;
        }


    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected void registerStructures(StageMgr mgr, ErrorListener errs)
        {
        // create the structure for this property
        if (getComponent() == null)
            {
            // create a structure for this type
            String    sName     = name.getValueText();
            Component container = getParent().getComponent();
            if (container.isClassContainer())
                {
                // TODO sanity checks on the declaration of the property e.g. no "private/public", no "public/private static", no "static abstract", etc.

                // another property by the same name should not already exist, but the check for
                // duplicates is deferred, since it is possible (thanks to the complexity of
                // conditionals) to have multiple components occupying the same location within the
                // namespace at this point in the compilation
                // TODO if (container.getProperty(sName) != null) ...

                // the type constant we get from the type expression may be unresolved,
                // but it will resolve when the type expression resolves names
                TypeConstant      constType = type.ensureTypeConstant();
                PropertyStructure prop      = container.createProperty(
                        isStatic(), getDefaultAccess(), getAccess2(), constType, sName);
                if (value != null)
                    {
                    prop.indicateInitialValue();
                    }
                prop.setSynthetic(fSynthetic);
                setComponent(prop);

                // the annotations either have to be registered on the type or on the property, so
                // register them on the property for now (they'll get sorted out later after we
                // can resolve the types to figure out what the annotation targets actually are)
                if (annotations != null)
                    {
                    ConstantPool pool = pool();
                    for (Annotation annotation : annotations)
                        {
                        prop.addAnnotation(annotation.ensureAnnotation(pool));
                        }
                    }
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.PROP_UNEXPECTED, sName, container);
                }
            }
        }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        // TODO?
        }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        if (!alreadyReached(Stage.Validated))
            {
            setStage(Stage.Validating);

            PropertyStructure prop = (PropertyStructure) getComponent();
            if (!prop.resolveAnnotations())
                {
                mgr.requestRevisit();
                return;
                }

            if (prop.hasInitialValue())
                {
                TypeConstant type = prop.getType();
                assert !type.containsUnresolved();

                // create an initializer function
                MethodStructure methodInit = prop.createMethod(true, Access.PRIVATE,
                        org.xvm.asm.Annotation.NO_ANNOTATIONS,
                        new Parameter[] {new Parameter(pool(), type, null, null, true, 0, false)},
                        "=", Parameter.NO_PARAMS, true, false);

                // wrap it with a pretend function in the AST tree
                MethodDeclarationStatement stmtInit = adopt(
                        new MethodDeclarationStatement(methodInit, value));

                // we're going to compile the initializer now, so that we can determine if it could
                // be discarded and replaced with a constant
                if (!(new StageMgr(stmtInit, Stage.Emitted, errs).fastForward(10)))
                    {
                    mgr.requestRevisit();
                    return;
                    }

                // if in the process of compiling the initializer, it became obvious that the result
                // was a constant value, then just take that constant value and discard the
                // initializer
                Expression valueNew = stmtInit.getInitializerExpression();
                if (valueNew != null && valueNew.isConstant()) // REVIEW !valueNew.isCompletable() && valueNew.isRuntimeConstant())
                    {
                    value = adopt(valueNew);

                    Constant constValue = valueNew.toConstant();
                    assert !constValue.containsUnresolved() && !constValue.getType().containsUnresolved();
                    prop.setInitialValue(valueNew.validateAndConvertConstant(constValue, type, errs));

                    // discard the initializer by removing the entire MultiMethodStructure
                    MultiMethodStructure mms = (MultiMethodStructure) methodInit.getParent();
                    mms.getParent().removeChild(mms);
                    }
                else
                    {
                    // clear the "has initial value" setting
                    prop.setInitialValue(null);

                    // REVIEW should value be nulled out since it's now "owned by" initializer?
                    // REVIEW and if so, what other methods make decisions based on "value != null"?
                    initializer = stmtInit;
                    }
                }
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (modifiers != null)
            {
            for (Token token : modifiers)
                {
                sb.append(token.getId().TEXT)
                  .append(' ');
                }
            }

        if (annotations != null)
            {
            for (Annotation annotation : annotations)
                {
                sb.append(annotation)
                  .append(' ');
                }
            }

        sb.append(type)
                .append(' ')
          .append(name.getValueText());

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append(toSignatureString());

        if (value != null)
            {
            sb.append(" = ")
              .append(value)
              .append(";");
            }
        else if (body != null)
            {
            String sBody = body.toString();
            if (sBody.indexOf('\n') >= 0)
                {
                sb.append('\n')
                  .append(indentLines(sBody, "    "));
                }
            else
                {
                sb.append(' ')
                  .append(sBody);
                }
            }
        else
            {
            sb.append(';');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression         condition;
    protected List<Token>        modifiers;
    protected List<Annotation>   annotations;
    protected TypeExpression     type;
    protected Token              name;
    protected Expression         value;
    protected StatementBlock     body;
    protected Token              doc;

    protected transient MethodDeclarationStatement initializer;
    protected transient boolean                    fSynthetic;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PropertyDeclarationStatement.class,
            "condition", "annotations", "type", "value", "body", "initializer");
    }
