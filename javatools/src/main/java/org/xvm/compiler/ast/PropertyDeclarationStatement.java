package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.constants.TypeInfo.MethodKind;

import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.P_Get;
import org.xvm.asm.op.P_Var;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Constants;
import org.xvm.compiler.Token;

import org.xvm.util.ListMap;
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
            if (typeNext instanceof AnnotatedTypeExpression typeAnno)
                {
                // remove the annotation from the type chain, and add it to the list of annotations
                AnnotationExpression anno = typeAnno.getAnnotation();
                anno.setParent(this);
                if (annotations == null || annotations.isEmpty())
                    {
                    annotations = new ArrayList<>();
                    }
                annotations.add(anno);

                // unlink the annotated type from the type expression chain, and relink the chain
                typeNext = typeAnno.unwrapIntroductoryType();
                if (typePrev == null)
                    {
                    typeNext.setParent(typeAnno);
                    }
                else
                    {
                    typePrev.replaceIntroducedType(typeNext);
                    typeNext.setParent(typePrev);
                    }
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
    protected AstNode getCodeContainer()
        {
        return isInMethod()
                ? super.getCodeContainer()
                : null;
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
        m_fSynthetic = true;
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

        if (name.getId() == Token.Id.ANY)
            {
            log(errs, Severity.ERROR, Compiler.NAME_RESERVED, sName);
            return;
            }

        if (name.isSpecial())
            {
            if (!("outer".equals(sName)
                    && container.getIdentityConstant().getModuleConstant().isEcstasyModule()
                    && "reflect.Outer.Inner".equals(container.getIdentityConstant().getPathString())))
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
                // illegal to combine a setter specific access with static
                log(errs, Severity.ERROR, Compiler.STATIC_PROP_HAS_SETTER_ACCESS,
                        sName, container.getIdentityConstant().getValueString());
                accessSet = null;
                }
            else if (accessSet.isMoreAccessibleThan(accessGet))
                {
                // illegal to have a setter specific access that is more accessible than the default
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
        prop.setSynthetic(m_fSynthetic);
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
    public void resolveNames(StageMgr mgr, ErrorListener errs)
        {
        PropertyStructure prop = (PropertyStructure) getComponent();
        if (!prop.resolveAnnotations())
            {
            mgr.requestRevisit();
            }
        }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs)
        {
        if (!alreadyReached(Stage.Validated))
            {
            setStage(Stage.Validating);

            PropertyStructure prop = (PropertyStructure) getComponent();
            TypeConstant      type = prop.getType();
            if (type.containsUnresolved())
                {
                mgr.requestRevisit();
                return;
                }

            if (prop.isStatic() && type.containsGenericType(true))
                {
                log(errs, Severity.ERROR, Compiler.GENERIC_PROPERTY_TYPE_NOT_ALLOWED,
                        prop.getName(), type.getValueString());
                return;
                }

            if (prop.isSimpleUnassigned() && prop.getGetter() != null)
                {
                // property marked as @Unassigned must not have a getter
                AnnotationExpression exprAnno = annotations.get(0);
                exprAnno.log(errs, Severity.ERROR, Compiler.ANNOTATION_NOT_APPLICABLE,
                        pool().clzUnassigned().getName(),
                        prop.getIdentityConstant().getValueString());
                return;
                }

            ClassStructure clz      = prop.getContainingClass(false);
            String         sInvalid = clz.checkGenericTypeVisibility(type);
            if (sInvalid != null)
                {
                log(errs, Severity.ERROR, Compiler.TYPE_PARAMS_UNRESOLVABLE, sInvalid);
                return;
                }

            if (!validateRefAnnotations(prop.getRefAnnotations(), clz, prop, errs))
                {
                return;
                }

            TypeConstant typeRef = prop.getIdentityConstant().getValueType(pool(), null);
            if (!validateAnnotations(prop.getPropertyAnnotations(), typeRef, errs))
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

                    ErrorListener errsTmp = errs.branch(this);
                    if (!(new StageMgr(stmtInit, Stage.Resolved, errsTmp).fastForward(3)) ||
                            errsTmp.hasSeriousErrors())
                        {
                        if (mgr.isLastAttempt())
                            {
                            errsTmp.merge();
                            }
                        else
                            {
                            mgr.requestRevisit();
                            }
                        return;
                        }
                    errsTmp.merge();

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
                    ErrorListener errsTmp = errs.branch(this);
                    if (!(new StageMgr(stmtInit, Stage.Emitted, errsTmp).fastForward(10)) ||
                            errsTmp.hasSeriousErrors())
                        {
                        stmtClone.discardInitializer(methodInit);
                        stmtInit.discard(true);
                        if (mgr.isLastAttempt())
                            {
                            errsTmp.merge();
                            }
                        else
                            {
                            mgr.requestRevisit();
                            }
                        return;
                        }
                    errsTmp.merge();

                    // if in the process of compiling the initializer, it became obvious that the
                    // result was a constant value, then just take that constant value and discard
                    // the initializer
                    Expression exprTest        = stmtInit.getInitializerExpression();
                    boolean    fConstant       = exprTest != null && exprTest.isConstant();
                    boolean    fMethodRequired = !fConstant;

                    // if we have proven, by testing a fast-forward compile of a temp copy of the
                    // property initializer, that the initial property value is a constant, then
                    // get that constant value, and store it as the initial value for the property
                    if (fConstant)
                        {
                        Constant constValue = exprTest.toConstant();
                        if (constValue.containsUnresolved())
                            {
                            stmtClone.discardInitializer(methodInit);
                            stmtInit.discard(true);
                            if (mgr.isLastAttempt())
                                {
                                log(errs, Severity.ERROR, Compiler.CIRCULAR_INITIALIZER,
                                        prop.getName());
                                }
                            else
                                {
                                mgr.requestRevisit();
                                }
                            return;
                            }

                        prop.setInitialValue(exprTest.validateAndConvertConstant(constValue, type, errs));

                        // despite having a constant initial value, the property may still require
                        // an initializer; the simplest example being a property whose value is a
                        // function, e.g. "function Int(Int) prop = n -> n;" and a more complicated
                        // scenario being a MapConstant that contains such a function
                        Set<MethodConstant> setMethods = new HashSet<>();
                        if (constValue instanceof MethodConstant idMethod)
                            {
                            setMethods.add(idMethod);
                            }
                        else
                            {
                            constValue.forEachUnderlying(c ->
                                {
                                if (c instanceof MethodConstant idMethod)
                                    {
                                    setMethods.add(idMethod);
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
                            // basically an assertion
                            log(errs, Severity.FATAL, Compiler.FATAL_ERROR, initializer);
                            }
                        }
                    }
                }
            }
        }

    /**
     * Validate the specified property Ref annotations.
     *
     * @param aAnno  the annotations
     * @param clz    the enclosing class
     * @param prop   the property
     * @param errs   the error listener
     *
     * @return false iff there any errors have been reported
     */
    private boolean validateRefAnnotations(Annotation[] aAnno, ClassStructure clz,
                                           PropertyStructure prop, ErrorListener errs)
        {
        if (aAnno.length > 0)
            {
            if (clz.getFormat() == Format.INTERFACE)
                {
                log(errs, Severity.ERROR, Constants.VE_INTERFACE_PROPERTY_ANNOTATED,
                        clz.getIdentityConstant().getValueString(), prop.getName());
                return false;
                }

            ConstantPool pool = pool();
            for (int iA = 0, c = aAnno.length; iA < c; iA++)
                {
                Annotation     anno    = aAnno[iA];
                ClassConstant  idAnno  = (ClassConstant) anno.getAnnotationClass();
                ClassStructure clzAnno = (ClassStructure) idAnno.getComponent();
                if (clzAnno.getFormat() != Component.Format.MIXIN)
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN,
                            anno.getValueString());
                    return false;
                    }
                if (idAnno.equals(pool.clzVolatile()))
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, Compiler.ANNOTATION_NOT_APPLICABLE,
                        idAnno.getName(),
                        prop.getIdentityConstant().getValueString());
                    return false;
                    }
                }
            }

        return true;
        }

    /**
     * Validate the specified property annotations.
     *
     * Note: this method is similar to {@link TypeCompositionStatement#validateAnnotations} logic,
     *       but differs in the way that it could force the node revisit.
     *
     * @param aAnno     the annotations
     * @param typeProp  the annotated property type
     * @param errs      the error listener
     *
     * @return false iff there were unresolved annotation parameters; true otherwise
     *               (errors might have been reported)
     */
    private boolean validateAnnotations(Annotation[] aAnno, TypeConstant typeProp, ErrorListener errs)
        {
        ConstantPool pool = pool();

        assert typeProp.isA(pool.typeProperty());

        for (int iA = 0, c = aAnno.length; iA < c; iA++)
            {
            Annotation     anno    = aAnno[iA];
            ClassConstant  idAnno  = (ClassConstant) anno.getAnnotationClass();
            ClassStructure clzAnno = (ClassStructure) idAnno.getComponent();
            if (clzAnno.getFormat() != Component.Format.MIXIN)
                {
                findAnnotationExpression(anno, annotations).
                    log(errs, Severity.ERROR, Constants.VE_ANNOTATION_NOT_MIXIN,
                        anno.getValueString());
                break;
                }

            Constant[]      aParams = anno.getParams();
            int             cParams = aParams.length;
            MethodStructure ctor    = clzAnno.findMethod("construct", cParams);

            if (ctor == null)
                {
                // an error will be reported by the AnnotationExpression
                continue;
                }

            TypeConstant typeMixin;
            if (clzAnno.isParameterized() && cParams > 0)
                {
                ListMap<String, TypeConstant> mapResolved = new ListMap<>();
                for (Map.Entry<StringConstant, TypeConstant> entry : clzAnno.getTypeParamsAsList())
                    {
                    String sFormal = entry.getKey().getValue();

                    mapResolved.put(sFormal, entry.getValue()); // prime with the constraint type

                    for (int iP = 0; iP < cParams; iP++)
                        {
                        Constant constParam = aParams[iP];
                        if (constParam.containsUnresolved())
                            {
                            return false;
                            }
                        TypeConstant typeFormal = ctor.getParam(iP).getType();
                        TypeConstant typeActual   = constParam.getType();
                        TypeConstant typeResolved = typeFormal.resolveTypeParameter(typeActual, sFormal);
                        if (typeResolved != null)
                            {
                            mapResolved.put(sFormal, typeResolved);
                            }
                        }
                    }
                typeMixin = clzAnno.getFormalType().resolveGenerics(pool, mapResolved::get);
                }
            else
                {
                typeMixin = clzAnno.getCanonicalType();
                }

            TypeConstant typeInto = typeMixin.getExplicitClassInto(true);

            if (!typeProp.isA(typeInto))
                {
                findAnnotationExpression(anno, annotations).
                    log(errs, Severity.ERROR, Constants.VE_ANNOTATION_INCOMPATIBLE,
                        typeProp.getValueString(), typeMixin.getValueString(), typeInto.getValueString());
                break;
                }

            for (int iA2 = iA + 1; iA2 < c; iA2++)
                {
                Annotation   anno2      = aAnno[iA2];
                TypeConstant typeMixin2 = anno2.getAnnotationType();
                if (typeMixin2.equals(typeMixin))
                    {
                    findAnnotationExpression(anno, annotations).
                        log(errs, Severity.ERROR, Constants.VE_ANNOTATION_REDUNDANT,
                            anno.getValueString());
                    break;
                    }
                }
            }
        return true;
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
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
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
            TypeConstant     typeVar         = idProp.getRefType(ctx.getThisType().ensureAccess(Access.PRIVATE));
            Register         regPropRef      = new Register(typeVar, null, Op.A_STACK);
            Register         regAssigned     = new Register(pool.typeBoolean(), null, Op.A_STACK);
            Label            labelSkipAssign = new Label("skip_assign_" + idProp.getName());
            PropertyConstant idAssigned      = typeVar.ensureTypeInfo(errs)
                                               .findProperty("assigned").getIdentity();

            Register regThis = ctx.getThisRegister();

            code.add(new P_Var(idProp, regThis, regPropRef));
            code.add(new P_Get(idAssigned, regPropRef, regAssigned));
            code.add(new JumpTrue(regAssigned, labelSkipAssign));
            fCompletes = assignment.completes(ctx, fCompletes, code, errs);
            code.add(labelSkipAssign);
            }

        return fCompletes;
        }

        @Override
        protected boolean canResolveNames()
            {
            return super.canResolveNames() || type.canResolveNames();
            }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a newly created property initializer method
     */
    private MethodStructure createInitializer()
        {
        PropertyStructure prop   = (PropertyStructure) getComponent();
        MethodStructure   method = prop.createMethod(isStatic(), Access.PRIVATE,
                org.xvm.asm.Annotation.NO_ANNOTATIONS,
                new Parameter[] {new Parameter(pool(), prop.getType(), null, null, true, 0, false)},
                "=", Parameter.NO_PARAMS, true, false);
        donateSource(method);
        return method;
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

        // this is not quite correct; if there were annotations extracted from the type, we will
        // see them doubled in the string; very rare inconvenience, not worth any effort atm
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
    protected transient AssignmentStatement        assignment;

    /**
     * Indicates that this property declaration is "synthetic".
     */
    protected transient boolean m_fSynthetic;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PropertyDeclarationStatement.class,
            "condition", "annotations", "type", "value", "body", "initializer", "assignment");
    }