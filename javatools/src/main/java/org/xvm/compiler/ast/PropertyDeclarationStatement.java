package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.P_Get;
import org.xvm.asm.op.P_Var;

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

    public PropertyDeclarationStatement(long                       lStartPos,
                                        long                       lEndPos,
                                        Expression                 condition,
                                        List<Token>                modifiers,
                                        List<AnnotationExpression> annotations,
                                        TypeExpression             type,
                                        Token                      name,
                                        Token                      tokAsn,
                                        Expression                 value,
                                        StatementBlock             body,
                                        Token                      doc)
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
                AnnotationExpression anno = ((AnnotatedTypeExpression) type).getAnnotation();
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
        this.tokAsn      = tokAsn;
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
        return getComponent().isAutoNarrowingAllowed();
        }

    /**
     * @return true iff the property is declared as static
     */
    public boolean isStatic()
        {
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
        Access access = getAccess(modifiers);
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

    /**
     * @return true iff this property is nested directly within a method, such that it is processed
     *         as part of the method compilation
     */
    public boolean isInMethod()
        {
        Component componentParent = getParent().getComponent();
        assert componentParent != null;
        return componentParent.getFormat() == Format.METHOD;
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
        if (getComponent() != null)
            {
            return;
            }

        String    sName     = name.getValueText();
        Component container = getParent().getComponent();
        if (container == null)
            {
            // error should have already been reported
            return;
            }
        if (!container.isClassContainer())
            {
            log(errs, Severity.ERROR, Compiler.PROP_UNEXPECTED, sName, container);
            return;
            }

        if (name.isSpecial())
            {
            if (!(sName.equals("outer")
                    && container.getIdentityConstant().getModuleConstant().isEcstasyModule()
                    && container.getIdentityConstant().getPathString().equals("reflect.Outer.Inner")))
                {
                log(errs, Severity.ERROR, Compiler.NAME_RESERVED, sName);
                return;
                }
            }

        // another property by the same name should not already exist (other than in the case of
        // conditionals, which are not yet implemented)
        if (container.getChild(sName) != null)
            {
            log(errs, Severity.ERROR, Compiler.PROP_DUPLICATE, sName);
            return;
            }

        // create the structure for this property
        Access  accessDft = getDefaultAccess();
        Access  accessGet = getAccess(modifiers);
        Access  accessSet = getAccess2();
        boolean fStatic   = isStatic();
        boolean fInMethod = isInMethod();

        if (fInMethod)
            {
            if (fStatic)
                {
                if (accessGet != null)
                    {
                    // a property in a method must either say "private" or "static", but not both
                    log(errs, Severity.ERROR, Compiler.STATIC_PROP_IN_METHOD_HAS_ACCESS,
                            sName, container.getIdentityConstant().getValueString());
                    }
                }
            else if (accessGet != Access.PRIVATE)
                {
                // for a property in a method, the only allowable access is "private"
                log(errs, Severity.ERROR, Compiler.PROP_IN_METHOD_NOT_PRIVATE,
                        sName, container.getIdentityConstant().getValueString());
                }

            // properties in a method will always be created as "private"
            accessDft = Access.PRIVATE;
            }

        if (accessSet != null)
            {
            if (accessSet == accessGet)
                {
                accessSet = null;
                }
            else if (fStatic)
                {
                // illegal to combine a setter-specific access with static
                log(errs, Severity.ERROR, Compiler.STATIC_PROP_HAS_SETTER_ACCESS,
                        sName, container.getIdentityConstant().getValueString());
                accessSet = null;
                }
            else if (accessSet.isMoreAccessibleThan(accessGet))
                {
                // illegal to have a setter-specific access that is more accessible than the default
                // access (the getter access)
                log(errs, Severity.ERROR, Compiler.PROP_SETTER_ACCESS_TOO_ACCESSIBLE,
                        sName, container.getIdentityConstant().getValueString());
                accessSet = null;
                }
            }

        // the type constant we get from the type expression may be unresolved,
        // but it will resolve when the type expression resolves names
        TypeConstant      constType = type.ensureTypeConstant();
        PropertyStructure prop      = container.createProperty(
                fStatic, accessDft, accessSet, constType, sName);
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
            for (AnnotationExpression annotation : annotations)
                {
                prop.addAnnotation(annotation.ensureAnnotation(pool));
                }
            }
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

            TypeConstant type = prop.getType();
            if (type.containsUnresolved())
                {
                mgr.requestRevisit();
                return;
                }

            if (prop.hasInitialValue())
                {
                if (isInMethod() && !isStatic())
                    {
                    // the assignment will occur as part of the execution of the method body
                    AssignmentStatement stmtInit = adopt(new AssignmentStatement(
                                new NameExpression(name), tokAsn, value));
                    stmtInit.introduceParentage();
                    if (!(new StageMgr(stmtInit, Stage.Resolved, errs).fastForward(3)))
                        {
                        mgr.requestRevisit();
                        return;
                        }

                    // the assignment statement takes the place of the initial value
                    assignment = stmtInit;

                    // mark the property as unassigned to prevent default initialization
                    prop.addAnnotation(pool().clzUnassigned());

                    // clear the "has initial value" setting
                    prop.setInitialValue(null);
                    }
                else
                    {
                    // create a clone of ourselves
                    PropertyDeclarationStatement stmtClone = (PropertyDeclarationStatement) clone();

                    // create an initializer function
                    MethodStructure            methodInit = stmtClone.createInitializer();
                    MethodDeclarationStatement stmtInit   = stmtClone.createAstNodeFor(methodInit);

                    // we're going to compile the (cloned) initializer now, so that we can determine
                    // if it could be discarded and replaced with a constant
                    // IMPORTANT NOTE: this goes forward BEYOND validation, so the caller's context
                    // must be ready to resolve the corresponding names (e.g. see NewExpression)
                    if (!(new StageMgr(stmtInit, Stage.Emitted, errs).fastForward(10)))
                        {
                        stmtClone.discardInitializer(methodInit);
                        stmtInit.discard(true);
                        mgr.requestRevisit();
                        return;
                        }

                    // if in the process of compiling the initializer, it became obvious that the
                    // result was a constant value, then just take that constant value and discard
                    // the initializer
                    // REVIEW CP !valueTest.isCompletable() && valueTest.isRuntimeConstant())
                    Expression valueTest       = stmtInit.getInitializerExpression();
                    boolean    fConstant       = valueTest != null && valueTest.isConstant();
                    boolean    fMethodRequired = !fConstant;

                    // if we have proven, by testing a fast-forward compile of a temp copy of the
                    // property initializer, that the initial property value is a constant, then
                    // get that constant value, and store it as the initial value for the property
                    if (fConstant)
                        {
                        Constant constValue = valueTest.toConstant();
                        assert !constValue.containsUnresolved() && !constValue.getType().containsUnresolved();
                        prop.setInitialValue(valueTest.validateAndConvertConstant(constValue, type, errs));

                        // despite having a constant initial value, the property may still require
                        // an initializer; the simplest example being a property whose value is a
                        // function, e.g. "function Int(Int) prop = n -> n;" and a more complicated
                        // scenario being a MapConstant that contains such a function
                        Set<MethodConstant> setMethods = new HashSet<>();
                        if (constValue instanceof MethodConstant)
                            {
                            setMethods.add((MethodConstant) constValue);
                            }
                        else
                            {
                            constValue.forEachUnderlying(c ->
                                {
                                if (c instanceof MethodConstant)
                                    {
                                    setMethods.add((MethodConstant) c);
                                    }
                                });
                            }

                        // if any of the constants point to the initializer's class - keep it
                        if (!setMethods.isEmpty())
                            {
                            for (MethodConstant idMethod : setMethods)
                                {
                                if (idMethod.getNamespace().equals(methodInit.getIdentityConstant()))
                                    {
                                    fMethodRequired = true;
                                    break;
                                    }
                                }
                            }
                        }
                    else
                        {
                        prop.setInitialValue(null);
                        }

                    // at this point, we are done with the "test clone"
                    stmtClone.discardInitializer(methodInit);
                    stmtInit.discard(true);

                    if (fMethodRequired)
                        {
                        // create a real method and the initializer statement
                        methodInit  = this.createInitializer();
                        initializer = this.createAstNodeFor(methodInit);

                        // since the initializer is created around the "value" expression, the
                        // initializer now "owns" the value expression
                        assert value.getParent() != this;
                        value = null;

                        // "catch up" the newly created initializer to our stage
                        if (!new StageMgr(initializer, Stage.Validated, errs).fastForward(10))
                            {
                            assert false;
                            }
                        }
                    }
                }
            }
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        if (assignment != null)
            {
            // first, invalidate all TypeInfos that should have included this property, because
            // the cached TypeInfos will be wrong (this is a temporary solution, because it doesn't
            // follow the dependency graph and clean it up)
            ctx.getThisType().invalidateTypeInfo();

            AssignmentStatement stmtNew = (AssignmentStatement) assignment.validate(ctx, errs);
            if (stmtNew == null)
                {
                return null;
                }
            assignment = stmtNew;
            }

        return this;
        }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs)
        {
        if (!errs.isSilent())
            {
            PropertyStructure   prop = (PropertyStructure) getComponent();
            ClassStructure      clz  = prop.getContainingClass();
            TypeConstant        type = pool().ensureAccessTypeConstant(clz.getFormalType(), Access.PRIVATE);
            TypeInfo            info = type.ensureTypeInfo(errs);
            Set<MethodConstant> set  = info.findMethods(prop.getName(), -1, MethodKind.Any);
            if (!set.isEmpty())
                {
                MethodConstant idMethod = set.iterator().next();
                log(errs, Severity.ERROR, Compiler.PROPERTY_NAME_COLLISION,
                        prop.getName(), idMethod.getNamespace().getPathString());
                }
            }
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, MethodStructure.Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;

        // this is the code generation for a property inside of a method; see for example the
        // "Map.KeyBasedEntrySet.iterator().Iterator#1.next().entry" property:
        //
        //     private KeyBasedCursorEntry entry = new KeyBasedCursorEntry(key);
        //
        // the compilation must ensure that the first execution of the "next()" method for an
        // instance of the "Iterator#1" inner class will initialize the property:
        //
        //     MOV_VAR entry ref                  # i.e. the next register is "ref"
        //     PGET ref Ref.assigned -> stack     # is the property already assigned?
        //     JMP_TRUE stack skip_assignment     # if it is, then skip over assigning it
        //     NEWC KeyBasedCursorEntry entry     # entry = new KeyBasedCursorEntry(key);
        //     skip_assignment:
        //
        // for a constant (a static property), the initialization is done as it would be if the
        // property were nested directly under the class itself, through an initialization function.
        // no code is contributed to the method for a static property.
        if (assignment != null)
            {
            NameExpression   exprProp        = (NameExpression) assignment.getLValueExpression();
            ConstantPool     pool            = pool();
            PropertyConstant idProp          = (PropertyConstant) exprProp.getIdentity(ctx);
            TypeConstant     typeVar         = idProp.getRefType(pool.ensureAccessTypeConstant(
                                               ctx.getThisType(), Access.PRIVATE));  // REVIEW GG should context always give us PRIVATE type?
            Register         regPropRef      = new Register(typeVar, Op.A_STACK);
            Register         regAssigned     = new Register(pool.typeBoolean(), Op.A_STACK);
            Label            labelSkipAssign = new Label("skip_assign_" + idProp.getName());
            PropertyConstant idAssigned      = typeVar.ensureTypeInfo(errs)
                                               .findProperty("assigned").getIdentity();

            Register regThis = ctx.generateThisRegister(code, true, errs);

            code.add(new P_Var(idProp, regThis, regPropRef));
            code.add(new P_Get(idAssigned, regPropRef, regAssigned));
            code.add(new JumpTrue(regAssigned, labelSkipAssign));
            fCompletes = assignment.completes(ctx, fCompletes, code, errs);
            code.add(labelSkipAssign);
            }

        return fCompletes;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a newly created property initializer method
     */
    private MethodStructure createInitializer()
        {
        PropertyStructure prop = (PropertyStructure) getComponent();
        return prop.createMethod(isStatic(), Access.PRIVATE,
                org.xvm.asm.Annotation.NO_ANNOTATIONS,
                new Parameter[] {new Parameter(pool(), prop.getType(), null, null, true, 0, false)},
                "=", Parameter.NO_PARAMS, true, false);
        }

    /**
     * @return a synthetic {@link MethodDeclarationStatement} for the property initializer
     */
    private MethodDeclarationStatement createAstNodeFor(MethodStructure methodInit)
        {
        return adopt(new MethodDeclarationStatement(methodInit, value));
        }

    /**
     * Discard an unused initializer.
     */
    private void discardInitializer(MethodStructure methodInit)
        {
        PropertyStructure prop = (PropertyStructure) getComponent();
        prop.removeChild(methodInit.getParent());
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
            for (AnnotationExpression annotation : annotations)
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

    protected Expression                 condition;
    protected List<Token>                modifiers;
    protected List<AnnotationExpression> annotations;
    protected TypeExpression             type;
    protected Token                      name;
    protected Token                      tokAsn;
    protected Expression                 value;
    protected StatementBlock             body;
    protected Token                      doc;

    protected transient MethodDeclarationStatement initializer;
    protected transient boolean                    fSynthetic;
    protected transient AssignmentStatement        assignment;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PropertyDeclarationStatement.class,
            "condition", "annotations", "type", "value", "body", "initializer", "assignment");
    }
