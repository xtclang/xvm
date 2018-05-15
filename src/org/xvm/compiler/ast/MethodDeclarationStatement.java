package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorList.ErrorInfo;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.appendString;
import static org.xvm.util.Handy.indentLines;


/**
 * A method declaration.
 */
public class MethodDeclarationStatement
        extends ComponentStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MethodDeclarationStatement(long                 lStartPos,
                                      long                 lEndPos,
                                      Expression           condition,
                                      List<Token>          modifiers,
                                      List<Annotation>     annotations,
                                      List<Parameter>      typeParams,
                                      Token                conditional,
                                      List<TypeExpression> returns,
                                      Token                name,
                                      List<TypeExpression> redundant,
                                      List<Parameter>      params,
                                      StatementBlock       body,
                                      StatementBlock       continuation,
                                      Token                doc)
        {
        super(lStartPos, lEndPos);

        assert name != null;

        this.condition    = condition;
        this.modifiers    = modifiers;
        this.annotations  = annotations;
        this.conditional  = conditional;
        this.typeParams   = typeParams;
        this.returns      = returns;
        this.name         = name;
        this.redundant    = redundant;
        this.params       = params;
        this.body         = body;
        this.continuation = continuation;
        this.doc          = doc;
        }

    /**
     * Create a MethodDeclarationStatement that turns an expression into a MethodStructure. This is
     * used, for example, by initializers.
     * <p/>
     * Note: the underlying expression can be retrieved using the {@link #getInitializerExpression()}
     * method.
     *
     * @param struct  the MethodStructure that this MethodDeclarationStatement is intended to
     *                compile into
     * @param expr    the Expression that the resulting method must evaluate as its one return value
     */
    public MethodDeclarationStatement(MethodStructure struct, Expression expr)
        {
        super(expr.getStartPosition(), expr.getEndPosition());

        // store off the method structure that we will generate code into
        setComponent(struct);

        // grab a body from the expression, if it has one, otherwise make one
        if (expr instanceof LambdaExpression && ((LambdaExpression) expr).params.isEmpty())
            {
            body = ((LambdaExpression) expr).body;
            }
        else
            {
            // turn "<expr>" into the statement block "{ return <expr>; }"
            Token fakeReturn = new Token(expr.getStartPosition(), expr.getStartPosition(), Id.RETURN);
            ReturnStatement stmt = new ReturnStatement(fakeReturn, expr);
            body = new StatementBlock(Collections.singletonList(stmt), expr.getStartPosition(), expr.getEndPosition());
            }

        body.setParent(this);
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isConstructor()
        {
        return name != null && name.getId() == Id.CONSTRUCT;
        }

    public String getName()
        {
        if (name != null)
            {
            return name.getValueText();
            }

        MethodStructure struct = (MethodStructure) getComponent();
        return struct == null
                ? "???"
                : struct.getName();
        }

    @Override
    public Access getDefaultAccess()
        {
        Access access = getAccess(modifiers);
        return access == null
                ? super.getDefaultAccess()
                : access;
        }

    @Override
    public boolean isAutoNarrowingAllowed(TypeExpression type)
        {
        return getComponent().isAutoNarrowingAllowed() &&
                (   (typeParams != null && typeParams.stream().anyMatch(param -> param.getType() == type))
                 || (returns != null && returns.contains(type))
                 || (redundant != null && redundant.contains(type))
                 || (params != null && params.stream().anyMatch(param -> param.getType() == type)));
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    protected boolean usesSuper()
        {
        return body != null && body.usesSuper();
        }

    /**
     * This helper method is only used for MethodDeclarationStatements that are created with the
     * {@link #MethodDeclarationStatement(MethodStructure, Expression) method-based constructor}
     *
     * @return the one expression that defines the return value of this statement, if such a thing
     *         is even possible; otherwise null
     */
    public Expression getInitializerExpression()
        {
        List<Statement> list = body.stmts;
        if (list.isEmpty())
            {
            return null;
            }

        Statement stmtFirst = list.get(0);
        if (stmtFirst instanceof ReturnStatement && ((ReturnStatement) stmtFirst).exprs.size() == 1)
            {
            return ((ReturnStatement) stmtFirst).exprs.get(0);
            }

        return null;
        }

    // ----- compile phases ------------------------------------------------------------------------

    @Override
    protected AstNode registerStructures(ErrorListener errs)
        {
        // create the structure for this method
        CreateStructure: if (getComponent() == null)
            {
            // TODO validate that the "redundant" types match the return types
            // TODO validate that the names (params, type params) are unique

            // create a structure for this type
            Component container = getParent().getComponent();
            String    sName     = getName();
            if (container.isMethodContainer())
                {
                boolean      fConstructor = isConstructor();
                boolean      fFunction    = isStatic(modifiers) || fConstructor;
                Access       access       = getDefaultAccess();
                ConstantPool pool         = container.getConstantPool();

                // build array of annotations
                org.xvm.asm.Annotation[] aAnnotations = buildAnnotations(pool);

                // build array of return types
                org.xvm.asm.Parameter[] aReturns;
                if (returns == null)
                    {
                    if (container instanceof PropertyStructure)
                        {
                        if (fFunction)
                            {
                            // TODO: error - function is not allowed
                            throw new UnsupportedOperationException("function is not allowed");
                            }
                        // it's a "short hand" property method; stop right here
                        // will continue resolution in resolveNames() below
                        break CreateStructure;
                        }

                    if (fConstructor)
                        {
                        aReturns = new org.xvm.asm.Parameter[0];
                        }
                    else
                        {
                        // parser should have caught this
                        throw new IllegalStateException("missing returns");
                        }
                    }
                else
                    {
                    int ofReturn = 0;
                    int cReturns = returns.size();
                    if (conditional != null)
                        {
                        ++ofReturn;
                        ++cReturns;
                        }
                    aReturns = new org.xvm.asm.Parameter[cReturns];
                    if (conditional != null)
                        {
                        aReturns[0] = new org.xvm.asm.Parameter(pool, pool.typeBoolean(), null, null, true, 0, true);
                        }
                    for (int i = ofReturn; i < cReturns; ++i)
                        {
                        aReturns[i] = new org.xvm.asm.Parameter(pool,
                                returns.get(i-ofReturn).ensureTypeConstant(), null, null, true, i, false);
                        }
                    }

                org.xvm.asm.Parameter[] aParams = buildParameters(pool);

                boolean fUsesSuper = !fFunction && access != Access.PRIVATE && usesSuper();
                MethodStructure method = container.createMethod(
                        fFunction, access, aAnnotations, aReturns, sName, aParams, fUsesSuper);
                setComponent(method);

                // "finally" continuation for constructors
                if (continuation != null)
                    {
                    assert fConstructor;

                    MethodStructure methodFinally = container.createMethod(false, Access.PRIVATE,
                            null, aReturns, "finally", aParams, false);
                    this.methodFinally = methodFinally;
                    }
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.METHOD_UNEXPECTED, sName, container);
                throw new UnsupportedOperationException("not a method container: " + container);
                }
            }

        return super.registerStructures(errs);
        }

    @Override
    public AstNode resolveNames(List<AstNode> listRevisit, ErrorListener errs)
        {
        if (getComponent() == null)
            {
            Component container = getParent().getComponent();
            String    sName     = getName();
            if (container.isMethodContainer())
                {
                if (returns == null && container instanceof PropertyStructure)
                    {
                    // this is a short-hand property method
                    PropertyStructure property = (PropertyStructure) container;
                    List<Annotation> annotations =
                        ((PropertyDeclarationStatement) getParent().getParent()).annotations; // TODO: replace

                    MethodStructure methodSuper = findRefMethod(property, annotations, sName, params, errs);
                    if (methodSuper == null)
                        {
                        listRevisit.add(this);
                        return this;
                        }

                    ConstantPool            pool     = container.getConstantPool();
                    int                     cReturns = methodSuper.getReturnCount();
                    org.xvm.asm.Parameter[] aReturns = new org.xvm.asm.Parameter[cReturns];
                    for (int i = 0; i < cReturns; i++)
                        {
                        org.xvm.asm.Parameter param = methodSuper.getReturn(i);
                        TypeConstant type = param.getType();

                        if (type.getFormat() == Constant.Format.TerminalType)
                            {
                            Constant constReturn = type.getDefiningConstant();
                            if (constReturn.getFormat() == Constant.Format.UnresolvedName)
                                {
                                // not yet resolved; come back later
                                listRevisit.add(this);
                                return this;
                                }

                            if (constReturn.getFormat() == Constant.Format.Property
                                    && ((PropertyConstant) constReturn).getName().equals("RefType"))
                                {
                                // replace the RefType with the actual property type
                                param = new org.xvm.asm.Parameter(pool,
                                        property.getType(), param.getName(), null, true, i, false);
                                }
                            }
                        aReturns[i] = param;
                        }

                    org.xvm.asm.Parameter[] aParams = buildParameters(pool);

                    // the parameters were already matched; no need to re-check
                    org.xvm.asm.Annotation[] annos = new org.xvm.asm.Annotation[]
                            {new org.xvm.asm.Annotation(pool.clzOverride(), Constant.NO_CONSTS)};
                    MethodStructure method = container.createMethod(
                            false, methodSuper.getAccess(), annos, aReturns, sName, aParams, usesSuper());
                    setComponent(method);
                    }
                }
            }

        AstNode nodeResult = super.resolveNames(listRevisit, errs);
        assert nodeResult == this;

        Component component = getComponent();
        if (component instanceof MethodStructure)
            {
            MethodStructure method = (MethodStructure) component;

            // sort out which annotations go on the method, and which belong to the return type
            if (!method.resolveAnnotations())
                {
                listRevisit.add(this);
                return this;
                }
            }

        return this;
        }

    @Override
    public AstNode generateCode(List<AstNode> listRevisit, ErrorListener errs)
        {
        ensureReached(Stage.Validated);
        if (!alreadyReached(Stage.Emitted))
            {
            setStage(Stage.Emitting);

            MethodStructure method = (MethodStructure) getComponent();
            if (body == null)
                {
                // it's abstract
                method.setAbstract(true); // TODO this should also set the enclosing class to abstract? and so on?
                }
            else
                {
                String    sPath    = method.getIdentityConstant().getPathString();
                Code      code     = method.createCode();
                ErrorList errsTemp = new ErrorList(10);
                try
                    {
                    body.compileMethod(code, errsTemp);

                    // TODO: temporary
                    if (errsTemp.getErrors().isEmpty())
                        {
                        if (sPath.startsWith("Test"))
                            {
                            if (sPath.contains("ExpectedFailure"))
                                {
                                System.err.println("Compilation should have failed: " + sPath);
                                }
                            else
                                {
                                System.out.println("Successfully compiled: " + sPath);
                                }
                            }

                        return super.generateCode(listRevisit, errs);
                        }
                    else
                        {
                        if (sPath.startsWith("Test"))
                            {
                            if (sPath.contains("ExpectedFailure"))
                                {
                                System.out.println("Successfully failed compilation: " + sPath);
                                }
                            else
                                {
                                System.err.println("Compilation error: " + sPath);
                                errsTemp.getErrors().forEach(System.err::println);
                                }
                            }

                        if (System.getProperty("GG") == null)
                            {
                            // copy over errors
                            for (ErrorInfo info : errsTemp.getErrors())
                                {
                                errs.log(info.getSeverity(), info.getCode(), info.getParams(), getSource(), info.getPos(), info.getEndPos());
                                }
                            }
                        else
                            {
                            method.setNative(true);
                            }
                        }
                    }
                catch (Throwable e) // TODO temporary
                // catch (UnsupportedOperationException e) // TODO temporary
                    {
                    // copy over errors
                    for (ErrorInfo info : errsTemp.getErrors())
                        {
                        errs.log(info.getSeverity(), info.getCode(), info.getParams(), getSource(), info.getPos(), info.getEndPos());
                        }

                    String sMsg = e.getMessage();
                    log(errs, Severity.INFO, Compiler.FATAL_ERROR, "could not compile "
                            + method.getIdentityConstant() + (sMsg == null ? "" : ": " + sMsg));
                    method.setNative(true);
                    if (sPath.startsWith("TestCompiler"))
                        {
                        if (e instanceof AssertionError || e instanceof NullPointerException)
                            {
                            e.printStackTrace(System.err);
                            }
                        else
                            {
                            System.err.println("Compilation error: " + sPath + " " + sMsg);
                            }
                        }
                    }
                }
            }

        return this;
        }


    // ----- internal ------------------------------------------------------------------------------

    protected org.xvm.asm.Annotation[] buildAnnotations(ConstantPool pool)
        {
        org.xvm.asm.Annotation[] aAnnotations = org.xvm.asm.Annotation.NO_ANNOTATIONS;
        if (annotations != null)
            {
            int cAnnotations = annotations.size();
            aAnnotations = new org.xvm.asm.Annotation[cAnnotations];
            for (int i = 0; i < cAnnotations; ++i)
                {
                aAnnotations[i] = annotations.get(i).ensureAnnotation(pool);
                }
            }

        return aAnnotations;
        }

    protected org.xvm.asm.Parameter[] buildParameters(ConstantPool pool)
        {
        // build array of parameters
        int cTypes  = typeParams == null ? 0 : typeParams.size();
        int cParams = cTypes + params.size();
        org.xvm.asm.Parameter[] aParams = new org.xvm.asm.Parameter[cParams];
        for (int i = 0; i < cTypes; ++i)
            {
            Parameter param = typeParams.get(i);
            TypeExpression exprType  = param.getType();
            TypeConstant constType = pool.ensureClassTypeConstant(pool.clzType(), null,
                    exprType == null
                            ? pool.typeObject()
                            : exprType.ensureTypeConstant());
            aParams[i] = new org.xvm.asm.Parameter(pool, constType, param.getName(), null, false, i, true);
            }
        for (int i = cTypes; i < cParams; ++i)
            {
            Parameter param = params.get(i-cTypes);
            aParams[i] = new org.xvm.asm.Parameter(pool, param.getType().ensureTypeConstant(),
                    param.getName(), /* TODO how to do value? */ null, false, i, false);
            }
        return aParams;
        }

    /**
     * Find a method on the Ref class or any of the annotations that matches the specified
     * name and parameters of a "short-hand" property method declaration.
     *
     * @param property     the property structure
     * @param annotations  the annotations on the property
     * @param sMethName    the method name
     * @param params       the parameters
     * @param errs         the error listener
     *
     * @return the matching methods structure of null if none is found
     */
    protected MethodStructure findRefMethod(PropertyStructure property, List<Annotation> annotations,
            String sMethName, List<Parameter> params, ErrorListener errs)
        {
        ConstantPool pool = property.getConstantPool();

        ClassStructure clzRef = (ClassStructure) pool.clzRef().getComponent();
        if (clzRef == null)
            {
            // no class for "Ref" yet; come back later
            return null;
            }

        MethodStructure method = findMethod(pool, clzRef, sMethName, params);
        if (method == null)
            {
            if (annotations != null)
                {
                for (Iterator<Annotation> iter = annotations.iterator(); iter.hasNext();)
                    {
                    Annotation annotation = iter.next();

                    String        sAnnotation = annotation.getType().getName();
                    ClassConstant constClass  = (ClassConstant) pool.getImplicitlyImportedIdentity(sAnnotation);
                    if (constClass == null)
                        {
                        log(errs, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, '@' + sAnnotation);
                        iter.remove();
                        continue;
                        }

                    ClassStructure clzMixin = (ClassStructure) constClass.getComponent();
                    if (clzMixin == null)
                        {
                        // no class for the annotation yet; come back later
                        continue;
                        }

                    method = findMethod(pool, clzMixin, sMethName, params);
                    if (method != null)
                        {
                        break;
                        }
                    }
                }
            }

        return method;
        }

    /**
     * Find a method on the specified ClassStructure that matches the specified name and parameters.
     *
     * @param pool        the constant pool
     * @param clz         the class structure
     * @param sMethName   the method name
     * @param parameters  the parameters
     *
     * @return the matching method structure
     */
    protected MethodStructure findMethod(ConstantPool pool, ClassStructure clz,
            String sMethName, List<Parameter> parameters)
        {
        MultiMethodStructure mms = (MultiMethodStructure) clz.getChild(sMethName);
        if (mms != null)
            {
            for (Component c : mms.children())
                {
                MethodStructure method = (MethodStructure) c;

                if (parameters.size() != method.getParamCount())
                    {
                    continue;
                    }

                // TODO: compare the ast.Parameters (parameters) with asm.Parameters (method)
                return method;
                }
            }
        // TODO: check the contributions (super, mixin, etc.)
        return null;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        if (name == null)
            {
            MethodStructure struct = (MethodStructure) getComponent();
            return struct == null
                    ? "?()"
                    : struct.getIdentityConstant().getValueString();
            }

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

        if (typeParams != null)
            {
            sb.append('<');
            boolean first = true;
            for (Parameter param : typeParams)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param.toTypeParamString());
                }
            sb.append("> ");
            }

        if (returns == null)
            {
            sb.append("<Unknown> ");
            }
        else if (returns.isEmpty())
            {
            sb.append("Void ");
            }
        else if (returns.size() == 1)
            {
            sb.append(returns.get(0))
                    .append(' ');
            }
        else
            {
            sb.append(" (");
            boolean first = true;
            for (TypeExpression type : returns)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append(") ");
            }

        sb.append(getName());

        if (redundant != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : redundant)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

        if (params != null)
            {
            sb.append('(');
            boolean first = true;
            for (Parameter param : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(param);
                }
            sb.append(')');
            }

        if (continuation != null)
            {
            sb.append(" {..} finally {..}");
            }

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

        if (body == null)
            {
            sb.append(';');
            }
        else
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

            if (continuation != null)
                {
                String sFinally = continuation.toString();
                sb.append("\nfinally");
                if (sFinally.indexOf('\n') >= 0)
                    {
                    sb.append('\n')
                      .append(indentLines(sFinally, "    "));
                    }
                else
                    {
                    sb.append(' ')
                      .append(sFinally);
                    }
                }
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           condition;
    protected List<Token>          modifiers;
    protected List<Annotation>     annotations;
    protected List<Parameter>      typeParams;
    protected Token                conditional;
    protected List<TypeExpression> returns;
    protected Token                name;
    protected List<TypeExpression> redundant;
    protected List<Parameter>      params;
    protected StatementBlock       body;
    protected StatementBlock       continuation;
    protected Token                doc;
    protected MethodStructure      methodFinally;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MethodDeclarationStatement.class,
            "condition", "annotations", "typeParams", "returns", "redundant", "params", "body", "continuation");
    }
