package org.xvm.compiler;


import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Version;

import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.*;

import org.xvm.tool.ResourceDir;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;
import org.xvm.util.Severity;


/**
 * A recursive descent parser for Ecstasy source code.
 */
public class Parser
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy lexical analyzer.
     *
     * @param source   the source to parse
     * @param listener the error listener
     */
    public Parser(Source source, ErrorListener listener)
        {
        this(source, listener, new Lexer(source, listener));
        }

    /**
     * Create a temporary lexer that provides a stream of tokens as specified.
     *
     * @param parent  the parent parser
     * @param atoken  the tokens to parse
     */
    protected Parser(Parser parent, Token[] atoken)
        {
        this(parent.m_source, parent.m_errorListener, parent.m_lexer.createLexer(atoken));
        }

    private Parser(Source source, ErrorListener errs, Lexer lexer)
        {
        if (source == null)
            {
            throw new IllegalArgumentException("Source required");
            }

        if (errs == null)
            {
            throw new IllegalArgumentException("ErrorListener required");
            }

        m_source        = source;
        m_errorListener = errs;
        m_lexer         = lexer;

        // prime the token stream
        next();
        }


    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Parse the compilation unit.
     *
     * <p/><code><pre>
     * CompilationUnit
     *     AliasStatements-opt TypeDeclaration
     * </pre></code>
     *
     * @return the top level type declaration
     *
     * @throws CompilerException if a parsing error occurs while parsing the
     *         source code that forces the parser to abandon its progress before
     *         completion
     */
    public StatementBlock parseSource()
        {
        // parsing can only occur once
        if (!m_fDone)
            {
            // set the completion flag at this point (in case an exception occurs
            // during parsing
            m_fDone = true;

            List<Statement> stmts = parseTypeCompositionComponents(null, new ArrayList<>(), true);
            m_root = new StatementBlock(stmts, m_source, stmts.getFirst().getStartPosition(),
                    stmts.getLast().getEndPosition());

            // there shouldn't be more in the file; (note that a zero-length token doesn't count,
            // since it is probably the synthetic closing '}')
            Token next = peek();
            if (next != null && next.getStartPosition() < next.getEndPosition())
                {
                log(Severity.ERROR, EXPECTED_EOF, next.getStartPosition(), next.getEndPosition(), next);
                }
            }

        return m_root;
        }

    /**
     * Parse the "implicits.x" file format.
     *
     * @return a Map from String import name to qualified name (as a String[])
     */
    public Map<String, String[]> parseImplicits()
        {
        Map<String, String[]> imports = new ListMap<>();

        while (!eof())
            {
            ImportStatement stmt = parseImportStatement(null);
            imports.put(stmt.getAliasName(), stmt.getQualifiedName());
            }

        // there shouldn't be more in the file; (note that a zero-length token doesn't count,
        // since it is probably the synthetic closing '}')
        Token next = peek();
        if (next != null && next.getStartPosition() < next.getEndPosition())
            {
            log(Severity.ERROR, EXPECTED_EOF, next.getStartPosition(), next.getEndPosition(), next);
            }

        return imports;
        }

    /**
     * Quick-scan the file for the module name.
     *
     * @return the module name
     */
    public String parseModuleNameIgnoreEverythingElse()
        {
        ErrorListener errsPrev = m_errorListener;
        try
            {
            m_errorListener = ErrorListener.BLACKHOLE;

            Loop: while (!eof())
                {
                switch (current().getId())
                    {
                    case MODULE:
                        if (!eof())
                            {
                            m_errorListener = new ErrorList(1);
                            List<Token> tokens = parseQualifiedName();
                            if (!m_errorListener.hasSeriousErrors())
                                {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0, c = tokens.size(); i < c; ++i)
                                    {
                                    if (i > 0)
                                        {
                                        sb.append('.');
                                        }
                                    sb.append(tokens.get(i).getValueText());
                                    }
                                return sb.toString();
                                }
                            }

                    case L_CURLY:
                    case R_CURLY:
                        break Loop;
                    }
                }
            }
        catch (RuntimeException ignore)
            {
            }
        finally
            {
            m_errorListener = errsPrev;
            }

        return null;
        }

    /**
     * As part of the runtime (NOT compile-time), parse the name of the class that is in the source.
     *
     * <p/><code><pre>
     * ClassExpression
     *     AnnotationList-opt ModuleName-opt QualifiedName TypeParameterTypeList-opt ChildClasses-opt Modifiers-opt
     *
     * AnnotationList
     *     AnnotationList Annotation
     *     Annotation
     *
     * Annotation
     *     "@" ClassName ArgumentList-opt       # arguments must be constants
     *
     * ModuleName
     *     QualifiedName ":"
     *
     * ChildClasses
     *     ChildClasses ChildClass
     *     ChildClass
     *
     * ChildClass
     *     "." AnnotationList-opt QualifiedName TypeParameterTypeList-opt
     *
     * ClassModifiers
     *     ClassModifiers ClassModifier
     *     ClassModifier
     *
     * ClassModifier
     *     "[" DimIndicators-opt "]"
     *     "..."
     * </pre></code>
     *
     * In the above BNF, the definitions are custom to this method, except for ArgumentList,
     * DimIndicators, and QualifiedName
     *
     * @return the TypeExpression for the class that is parsed, or null
     */
    public TypeExpression parseClassExpression()
        {
        TypeExpression exprResult = null;
        do
            {
            List<AnnotationExpression> annotations = null;
            AnnotationExpression       annotation;
            while ((annotation = parseAnnotation(false)) != null)
                {
                if (annotations == null)
                    {
                    annotations = new ArrayList<>();
                    }
                annotations.add(annotation);
                }

            List<Token> moduleNames = null;
            List<Token> classNames  = parseQualifiedName();
            if (exprResult == null && match(Id.COLON) != null)
                {
                moduleNames = classNames;
                classNames  = parseQualifiedName(false);
                }

            List<TypeExpression> params = parseTypeParameterTypeList(false, true);

            if (exprResult == null)
                {
                exprResult = new NamedTypeExpression(moduleNames, classNames, params, prev().getEndPosition());
                }
            else
                {
                exprResult = new NamedTypeExpression(exprResult, classNames, params, prev().getEndPosition());
                }

            if (annotations != null)
                {
                for (int i = annotations.size()-1; i >= 0; --i)
                    {
                    exprResult = new AnnotatedTypeExpression(annotations.get(i), exprResult);
                    }
                }
            }
        while (match(Id.DOT) != null);

        // parse modifiers
        while (!eof())
            {
            expect(Id.L_SQUARE);
            int cExplicitDims = 0;
            while (match(Id.R_SQUARE) == null)
                {
                if (cExplicitDims > 0)
                    {
                    expect(Id.COMMA);
                    }
                expect(Id.COND);
                ++cExplicitDims;
                }
            exprResult = new ArrayTypeExpression(exprResult, cExplicitDims, prev().getEndPosition());
            }

        return exprResult;
        }

    /**
     * Parse a type declaration
     *
     * <p/><code><pre>
     * TypeComposition
     *     Modifiers-opt Category QualifiedName TypeParameterList-opt     ->
     *             ParameterList-opt Compositions-opt TypeCompositionBody
     *
     * Category
     *     "module"
     *     "package"
     *     "class"
     *     "interface"
     *     "service"
     *     "const"
     *     "enum"
     *     "mixin"
     *
     * Compositions
     *     Composition
     *     Compositions Composition
     *
     * Composition
     *     "extends" Type ArgumentList-opt
     *     "implements" Type
     *     "delegates" Type "(" Expression ")"
     *     "incorporates" Type ArgumentList-opt
     *     "into" Type
     *     "import" QualifiedName VersionRequirement-opt
     *     "default" "(" Expression ")"
     *
     * TypeCompositionBody
     *     "{" EnumList-opt TypeCompositionComponents-opt "}"
     *     ";"
     * </pre></code>
     *
     * @return a TypeDeclaration
     */
    TypeCompositionStatement parseTypeCompositionStatement()
        {
        Token doc       = takeDoc();
        long  lStartPos = peek().getStartPosition();

        // modifiers (including annotations)
        List<Token>                modifiers   = null;
        List<AnnotationExpression> annotations = null;

        List[] twoLists = parseModifiers();
        if (twoLists != null)
            {
            // note to self: this language needs multiple return values
            modifiers   = twoLists[0];
            annotations = twoLists[1];
            }

        return parseTypeDeclarationStatementAfterModifiers(lStartPos, null, doc, modifiers, annotations);
        }

    /**
     * (This is just a continuation of the above method.)
     */
    TypeCompositionStatement parseTypeDeclarationStatementAfterModifiers(long lStartPos,
                Expression exprCondition, Token doc, List<Token> modifiers,
                List<AnnotationExpression> annotations)
        {
        // category & name
        Token category;
        Token name;
        List<Token> qualified = null;
        switch (peek().getId())
            {
            case PACKAGE:
            case CLASS:
            case INTERFACE:
            case SERVICE:
            case CONST:
            case ENUM:
            case MIXIN:
                category = current();
                name = expect(Id.IDENTIFIER);
                break;

            default:
                category = expect(Id.MODULE);
                qualified = parseQualifiedName();
                name = qualified.get(0);
            }

        // optional type parameters
        List<Parameter> typeParams = parseTypeParameterList(false);

        // optional constructor definition
        List<Parameter> constructorParams = parseParameterList(false);

        // sequence of compositions
        List<CompositionNode> compositions = parseCompositions();

        // TypeCompositionBody
        StatementBlock body = null;
        long           lEndPos;
        if (peek(Id.L_CURLY))
            {
            body    = parseTypeCompositionBody(category);
            lEndPos = body.getEndPosition();
            }
        else
            {
            lEndPos = prev().getEndPosition();
            expect(Id.SEMICOLON);
            }

        return new TypeCompositionStatement(m_source, lStartPos, lEndPos, exprCondition, modifiers,
                annotations, category, name, qualified, typeParams, constructorParams, compositions,
                body, doc);
        }

    /**
     * Parse any compositions found, but do not handle any conditional statements.
     *
     * @return a list of compositions
     */
    List<CompositionNode> parseCompositions()
        {
        List<CompositionNode> compositions = new ArrayList<>();
        while (true)
            {
            // the keywords below require "match()" to extract them, because they are context
            // sensitive
            Token keyword;
            if ((keyword = match(Id.EXTENDS)) != null)
                {
                do
                    {
                    TypeExpression   type = parseExtendedTypeExpression();
                    List<Expression> args = parseArgumentList(false, false, false);
                    compositions.add(new CompositionNode.Extends(null, keyword, type, args,
                            prev().getEndPosition()));
                    }
                while (match(Id.COMMA) != null);
                }
            else if ((keyword = match(Id.IMPLEMENTS)) != null)
                {
                do
                    {
                    compositions.add(new CompositionNode.Implements(null, keyword, parseExtendedTypeExpression()));
                    }
                while (match(Id.COMMA) != null);
                }
            else if ((keyword = match(Id.DELEGATES)) != null)
                {
                do
                    {
                    TypeExpression type = parseExtendedTypeExpression();
                    expect(Id.L_PAREN);
                    Expression expr = parseExpression();
                    Token tokEnd = expect(Id.R_PAREN);
                    compositions.add(new CompositionNode.Delegates(null, keyword, type,
                            expr, tokEnd.getEndPosition()));
                    }
                while (match(Id.COMMA) != null);
                }
            else if ((keyword = match(Id.INCORPORATES)) != null)
                {
                // "incorporates" IncorporatesFinish
                // IncorporatesFinish
                //     "conditional" QualifiedName TypeParameterList ArgumentList-opt
                //     TypeExpression ArgumentList-opt
                do
                    {
                    TypeExpression  type        = null;
                    List<Parameter> constraints = null;
                    if (match(Id.CONDITIONAL) == null)
                        {
                        type = parseTypeExpression();
                        }
                    else
                        {
                        do
                            {
                            // parse the type parameter list e.g. "<Key extends Int, Value>",
                            // and turn it into a type parameter name list e.g. "<Key, Value>"
                            List<Token>          names      = parseQualifiedName();
                            List<Parameter>      params     = parseTypeParameterList(type == null);
                            List<TypeExpression> paramnames = null;
                            if (params != null)
                                {
                                if (constraints == null)
                                    {
                                    constraints = params;
                                    }
                                else
                                    {
                                    constraints.addAll(params);
                                    }

                                paramnames  = new ArrayList<>();
                                for (Parameter param : params)
                                    {
                                    Token       tokName       = param.getNameToken();
                                    List<Token> listParamName = Collections.singletonList(tokName);
                                    paramnames.add(new NamedTypeExpression(null, listParamName,
                                            null, null, null, tokName.getEndPosition()));
                                    }
                                }

                            type = type == null
                                    ? new NamedTypeExpression(null, names, null, null, paramnames, prev().getEndPosition())
                                    : new NamedTypeExpression(type, names, paramnames, prev().getEndPosition());
                            }
                        while (match(Id.DOT) != null);
                        }

                    List<Expression> args = parseArgumentList(false, false, false);
                    compositions.add(new CompositionNode.Incorporates(null, keyword, type, args, constraints));
                    }
                while (match(Id.COMMA) != null);
                }
            else if ((keyword = match(Id.INTO)) != null)
                {
                compositions.add(new CompositionNode.Into(null, keyword, parseExtendedTypeExpression()));
                }
            else // not context-sensitive keywords
                {
                switch (peek().getId())
                    {
                    case IMPORT:
                        {
                        keyword = current();
                        Token modifier;
                        if (   (modifier = match(Id.EMBEDDED)) != null
                            || (modifier = match(Id.REQUIRED)) != null
                            || (modifier = match(Id.DESIRED)) != null
                            || (modifier = match(Id.OPTIONAL)) != null) {}

                        List<Token>           names    = parseQualifiedName();
                        NamedTypeExpression   module   = new ModuleTypeExpression(names);
                        List<VersionOverride> versions = parseVersionRequirement(false);

                        List<Parameter>     injects  = parseResourceList();
                        NamedTypeExpression injector = null;
                        if (match(Id.USING, (injects != null)) != null)
                            {
                            injector = parseNamedTypeExpression(null);
                            }
                        compositions.add(new CompositionNode.Import(null, keyword, modifier,
                                module,versions, injects, injector, prev().getEndPosition()));
                        }
                        break;

                    case DEFAULT:
                        {
                        keyword = expect(Id.DEFAULT);
                        expect(Id.L_PAREN);
                        compositions.add(new CompositionNode.Default(null, keyword,
                                parseExpression(), expect(Id.R_PAREN).getEndPosition()));
                        }
                        break;

                    default:
                        return compositions;
                    }
                }
            }
        }

    /**
     * Parse the body of a type composition, including support for enum bodies.
     *
     * <p/><code><pre>
     * EnumList
     *     Enums ";"
     *
     * Enums
     *     Enum
     *     Enums "," Enum
     *
     * Enum
     *     Annotations-opt Name TypeParameterTypeList-opt ArgumentList-opt TypeCompositionBody-opt
     * </pre></code>
     *
     * @return
     */
    StatementBlock parseTypeCompositionBody(Token category)
        {
        List<Statement> stmts = new ArrayList<>();
        Token tokLCurly = expect(Id.L_CURLY);
        if (category.getId() == Id.ENUM)
            {
            do
                {
                Token doc = takeDoc();

                long lStartPos = peek().getStartPosition();

                // annotations
                List<AnnotationExpression> annotations = null;
                while (true)
                    {
                    AnnotationExpression annotation = parseAnnotation(false);
                    if (annotation == null)
                        {
                        break;
                        }
                    if (annotations == null)
                        {
                        annotations = new ArrayList<>();
                        }
                    annotations.add(annotation);
                    }

                // name
                Token name = expect(Id.IDENTIFIER);

                // optional type parameters
                List<TypeExpression> typeParams = parseTypeParameterTypeList(false, true);

                // argument list
                List<Expression> args = parseArgumentList(false, false, false);

                StatementBlock body = null;
                if (match(Id.L_CURLY) != null)
                    {
                    Token tokLCurly2 = prev();
                    body = new StatementBlock(parseTypeCompositionComponents(null, new ArrayList<>(), false),
                            tokLCurly2.getStartPosition(), prev().getEndPosition());
                    }

                long lEndPos = prev().getEndPosition();

                stmts.add(new TypeCompositionStatement(annotations, name, typeParams, args, body,
                        doc, lStartPos, lEndPos));
                }
            while (match(Id.COMMA) != null && !peek(Id.SEMICOLON) && !peek(Id.R_CURLY));

            if (!peek(Id.R_CURLY))
                {
                expect(Id.SEMICOLON);
                }
            }

        return new StatementBlock(parseTypeCompositionComponents(null, stmts, false),
                tokLCurly.getStartPosition(), prev().getEndPosition());
        }

    /**
     * Parse the components of a type composition.
     *
     * <p/><code><pre>
     * TypeCompositionComponents
     *     TypeCompositionComponent
     *     TypeCompositionComponents TypeCompositionComponent
     *
     * TypeCompositionComponent
     *     AccessModifier-opt TypeDefStatement
     *     ImportStatement
     *     TypeComposition
     *     PropertyDeclaration
     *     MethodDeclaration
     *     ConstantDeclaration
     * </pre></code>
     *
     * @return a list of statements
     */
    List<Statement> parseTypeCompositionComponents(Expression exprCondition, List<Statement> stmts, boolean fFileLevel)
        {
        boolean fFoundType = false;
        while (match(Id.R_CURLY) == null)
            {
            Statement stmt;
            switch (peek().getId())
                {
                case IMPORT:
                    stmt = parseImportStatement(exprCondition);
                    break;

                case TYPEDEF:
                    stmt = parseTypeDefStatement(exprCondition, null);
                    break;

                default:
                    {
                    Token start = peek();

                    stmt = parseTypeCompositionComponent(exprCondition, false);
                    fFoundType = true;

                    if (fFileLevel)
                        {
                        // module cannot have any statements before it in the source
                        if (stmt instanceof TypeCompositionStatement stmtType)
                            {
                            if (stmtType.getCategory().getId() == Id.MODULE && !stmts.isEmpty())
                                {
                                if (stmts.stream().allMatch(ImportStatement.class::isInstance))
                                    {
                                    // transfer the imports from outside the module body to inside
                                    // the module body
                                    stmtType.ensureBody().getStatements().addAll(0, stmts);
                                    stmts.clear();
                                    }
                                else
                                    {
                                    log(Severity.ERROR, MODULE_NOT_ROOT, start.getStartPosition(),
                                            start.getEndPosition());
                                    }
                                }
                            }
                        else
                            {
                            log(Severity.ERROR, NO_TYPE_FOUND, start.getStartPosition(),
                                    start.getEndPosition());
                            }
                        }

                    break;
                    }
                }

            stmts.add(stmt);
            if (stmt instanceof MethodDeclarationStatement)
                {
                MethodDeclarationStatement stmtFinally =
                        ((MethodDeclarationStatement) stmt).getConstructorFinally();
                if (stmtFinally != null)
                    {
                    stmts.add(stmtFinally);
                    }
                }

            if (fFileLevel && fFoundType)
                {
                // at the file level, there is nothing after the outermost type's conclusion
                break;
                }
            }

        return stmts;
        }

    /**
     * Parse the components of a type composition.
     *
     * <p/><code><pre>
     * TypeCompositionComponent
     *     [..]
     *     TypeComposition
     *     PropertyDeclaration
     *     MethodDeclaration
     *     ConstantDeclaration
     * </pre></code>
     *
     * And if other statements are allowed:
     *
     * <p/><code><pre>
     * VariableDeclarationStatement
     *     TypeExpression Name VariableInitializerFinish-opt ";"
     *
     * AssignmentStatement
     *     Assignee AssignmentOperator Expression ";"
     *
     * Assignee
     *     Assignable
     *     "(" AssignableList "," Assignable ")"
     *
     * AssignableList
     *     Assignable
     *     AssignableList "," Assignable
     *
     * # Assignable turns out to be just an Expression that meets certain requirements, i.e. one that ends
     * # with a Name or an ArrayIndex
     * Assignable
     *     Name
     *     Expression "." Name
     *     Expression ArrayIndex
     *
     * ExpressionStatement
     *     Expression ";"
     * </pre></code>
     *
     *
     * @param exprCondition  the condition applying to this composition component, or null
     * @param fInMethod      pass true to allow parsing of an expression statement, a variable
     *                       declaration statement (almost the same syntax as a property), or an
     *                       assignment statement
     *
     * @return a StatementBlock
     */
    Statement parseTypeCompositionComponent(Expression exprCondition, boolean fInMethod)
        {
        // if this is inside a method, there shouldn't be a conditional; that would be handled by
        // an enclosing "if" statement instead
        assert !(fInMethod && exprCondition != null);

        Token doc       = takeDoc();
        long  lStartPos = peek().getStartPosition();

        // constant starts with "static" (a modifier)
        // method starts with annotations/modifiers
        // property starts with annotations/modifiers
        // type-composition starts with annotations/modifiers
        List<Token>                modifiers   = null;
        List<AnnotationExpression> annotations = null;

        List[] twoLists = parseModifiers(true);
        if (twoLists != null)
            {
            // note to self: this language needs multiple return values
            modifiers   = twoLists[0];
            annotations = twoLists[1];
            }

        // both constant and property have a TypeExpression next
        // method has "TypeVariableList-opt ReturnList" next (so "<" is a give-away)
        // - ReturnList could be either a TypeExpression or a "(", so "(" is a give-away
        // type-composition has a category keyword next
        switch (peek().getId())
            {
            case MODULE:
            case PACKAGE:
                if (fInMethod)
                    {
                    log(Severity.ERROR, NO_TOP_LEVEL, peek().getStartPosition(), peek().getEndPosition());
                    }
                // fall through
            case CLASS:
            case INTERFACE:
            case SERVICE:
            case CONST:
            case ENUM:
            case MIXIN:
                // it's definitely a type composition
                return parseTypeDeclarationStatementAfterModifiers(lStartPos, exprCondition, doc,
                        modifiers, annotations);

            case TYPEDEF:
                // evaluate annotations
                if (annotations != null)
                    {
                    for (AnnotationExpression annotation : annotations)
                        {
                        annotation.log(m_errorListener, Severity.ERROR, Compiler.ANNOTATION_UNEXPECTED);
                        }
                    }

                // evaluate modifiers looking for an access modifier
                Token tokAccess = null;
                if (modifiers != null)
                    {
                    for (Token modifier : modifiers)
                        {
                        switch (modifier.getId())
                            {
                            case PUBLIC:
                            case PROTECTED:
                            case PRIVATE:
                                if (tokAccess == null)
                                    {
                                    tokAccess = modifier;
                                    break;
                                    }
                                // fall through
                            default:
                                modifier.log(m_errorListener, m_source, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED, modifier.getValueText());
                                break;
                            }
                        }
                    }

                return parseTypeDefStatement(exprCondition, tokAccess);

            case COMP_LT:
            case CONDITIONAL:
            case VOID:
                {
                // it's definitely a method or a function
                List<Parameter> typeVars    = peek(Id.COMP_LT) ? parseTypeParameterList(true) : null;
                Token           conditional = match(Id.CONDITIONAL);
                List<Parameter> returns     = parseReturnList();
                Token           name        = expect(Id.IDENTIFIER);
                return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc,
                        modifiers, annotations, typeVars, conditional, returns, name);
                }

            case CONSTRUCT:
                {
                if (fInMethod)
                    {
                    // it is a method invocation
                    Statement stmt = new ExpressionStatement(parseExpression());
                    expect(Id.SEMICOLON);
                    return stmt;
                    }

                Token keyword = expect(Id.CONSTRUCT);
                return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc,
                        modifiers, annotations, null, null, null, keyword);
                }

            case ASSERT:
                {
                assert !fInMethod;

                Token keyword = expect(Id.ASSERT);
                return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc,
                        modifiers, annotations, null, null, null, keyword);
                }

            case L_PAREN:
                {
                // it's a property or a method, but the property type is parenthesized (odd!) or
                // the method return types (with optional names) are parenthesized (multi-return),
                // which means that to know it's a method, we have to find one of:
                //  - a list of types inside the parenthesis;
                //  - a type followed by a name inside the parenthesis; or
                //  - a '<' or '(' after the name.
                //
                // additionally, if we're inside a method, it could be a multi-assignment (tuple
                // result), a variable declaration starting with a parenthesized type (odd!), or
                // just an expression.

                // start by assuming it's a parenthesized list of types and/or type/name combos
                Mark             mark      = mark();
                List<Expression> listExpr  = new ArrayList<>();
                List<Token>      listName  = new ArrayList<>();
                boolean          fAnyNames = false;

                Token start = expect(Id.L_PAREN);
                do
                    {
                    Expression exprType = null;
                    Token      tokType  = matchVarOrVal();
                    if (tokType != null)
                        {
                        if (peekNameOrAny())
                            {
                            exprType = new VariableTypeExpression(tokType);
                            }
                        else
                            {
                            // var and val are not reserved keywords; they are context sensitive types
                            putBack(tokType);
                            }
                        }

                    if (exprType == null)
                        {
                        exprType = parseExtendedExpression();
                        }
                    listExpr.add(exprType);

                    Token tokName = matchNameOrAny();
                    fAnyNames |= tokName != null;
                    listName.add(tokName);
                    }
                while (match(Id.COMMA) != null);

                // if the next thing is not the closing parenthesis, then we _might_ be on the
                // right path
                if (match(Id.R_PAREN) != null)
                    {
                    // if the next token is an assignment operator, then this is a multi
                    // declaration/assignment
                    Token tokAssign;
                    if (fInMethod && modifiers == null
                            && ((tokAssign = match(Id.ASN)) != null || (tokAssign = match(Id.COND_ASN)) != null))
                        {
                        // compile as multiple assignment (and possible variable declarations)
                        int cLVals = listExpr.size();
                        if (cLVals <= 1)
                            {
                            log(Severity.ERROR, NOT_MULTI_ASN, start.getStartPosition(), peek().getEndPosition());
                            throw new CompilerException("multi assignment has only " + cLVals + " l-values");
                            }

                        Expression value = parseExpression();
                        expect(Id.SEMICOLON);

                        // build a list of LValues
                        List<AstNode> listLVals = new ArrayList<>(cLVals);
                        for (int i = 0; i < cLVals; ++i)
                            {
                            Expression expr    = listExpr.get(i);
                            Token      tokName = listName.get(i);
                            if (tokName == null)
                                {
                                if (expr.isLValueSyntax())
                                    {
                                    listLVals.add(expr);
                                    }
                                else
                                    {
                                    expr.log(m_errorListener, Severity.ERROR, NOT_ASSIGNABLE);
                                    }
                                }
                            else
                                {
                                listLVals.add(new VariableDeclarationStatement(
                                        expr.toTypeExpression(), tokName, false));
                                }
                            }

                        return new AssignmentStatement(new MultipleLValueStatement(listLVals), tokAssign, value);
                        }

                    // if there were any names, then this must be a method declaration; if there
                    // were multiple expressions, then this must be a method declaration; if there
                    // were no names, and only one expression, then it could still be a method
                    // declaration (but that can be handled more easily by dropping through to the
                    // default handling)
                    if (fAnyNames || listExpr.size() > 1)
                        {
                        int cReturns = listExpr.size();
                        List<Parameter> returns = new ArrayList<>(cReturns);
                        for (int i = 0; i < cReturns; ++i)
                            {
                            Expression expr    = listExpr.get(i);
                            Token      tokName = listName.get(i);
                            returns.add(new Parameter(expr.toTypeExpression(), tokName));
                            }
                        return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc,
                                modifiers, annotations, null, null, returns, expect(Id.IDENTIFIER));
                        }
                    }

                restore(mark);
                }
            // fall through
            default:

            // the following two case options are listed here solely for doc purposes; while they
            // can be "matched", they cannot be "peeked" (because they're context sensitive)
            case VAL:
            case VAR:
                {
                Token tokType = matchVarOrVal();
                if (tokType != null)
                    {
                    if (!fInMethod)
                        {
                        Token tok = current();
                        log(Severity.ERROR, NO_TYPE_FOUND, tok.getStartPosition(), tok.getEndPosition());
                        throw new CompilerException("var or val keyword outside of method");
                        }

                    Token tokName = matchNameOrAny();
                    if (tokName == null)
                        {
                        // var and val are not reserved keywords; they are context sensitive types
                        putBack(tokType);
                        }
                    else
                        {
                        VariableTypeExpression       typeDecl = new VariableTypeExpression(tokType);
                        VariableDeclarationStatement stmtDecl = new VariableDeclarationStatement(typeDecl, tokName, false);
                        AssignmentStatement          stmtAsn  = new AssignmentStatement(stmtDecl, match(Id.ASN), parseExpression());
                        expect(Id.SEMICOLON);
                        return stmtAsn;
                        }
                    }

                TypeExpression type;
                if (fInMethod && modifiers == null && annotations == null)
                    {
                    Expression expr = parseExpression();
                    Statement  stmt = parsePossibleExpressionOrAssignmentStatement(expr);
                    if (stmt != null)
                        {
                        return stmt;
                        }

                    type = expr.toTypeExpression();
                    }
                else
                    {
                    type = parseTypeExpression();
                    }

                if (type instanceof BadTypeExpression)
                    {
                    log(Severity.ERROR, NO_TYPE_FOUND, lStartPos, m_token.getStartPosition());
                    }

                // it's a constant, property, or method
                Token name = expectNameOrAny();
                if (peek(Id.COMP_LT) || peek(Id.L_PAREN))
                    {
                    // '<' indicates redundant return type list
                    // '(' indicates parameters
                    return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc, modifiers,
                            annotations, null, null, Collections.singletonList(new Parameter(type)), name);
                    }
                else
                    {
                    if (fInMethod && modifiers == null)
                        {
                        return parseVariableDeclarationAfterName(annotations, type, name);
                        }

                    // it's a property
                    return parsePropertyDeclarationFinish(lStartPos, exprCondition, doc, modifiers, annotations, type, name);
                    }
                }
            }
        }

    /**
     * Given the specified expression and the current point in the parsing stream, generate a
     * statement if the expression is supposed to be an expression statement, or if we're in the
     * middle of an assignment statement.
     *
     * @param expr  an expression that was parsed at the beginning of a statement
     *
     * @return a expression statement, an assignment statement, or null
     */
    Statement parsePossibleExpressionOrAssignmentStatement(Expression expr)
        {
        switch (peek().getId())
            {
            case SEMICOLON:
                expect(Id.SEMICOLON);
                return new ExpressionStatement(expr);

            case ASN:
            case ADD_ASN:
            case SUB_ASN:
            case MUL_ASN:
            case DIV_ASN:
            case MOD_ASN:
            case SHL_ASN:
            case SHR_ASN:
            case USHR_ASN:
            case BIT_AND_ASN:
            case BIT_OR_ASN:
            case BIT_XOR_ASN:
            case COND_ASN:
            case COND_AND_ASN:
            case COND_OR_ASN:
            case COND_NN_ASN:
            case COND_ELSE_ASN:
                {
                AssignmentStatement stmt = new AssignmentStatement(expr, current(), parseExpression());
                expect(Id.SEMICOLON);
                return stmt;
                }
            }

        return null;
        }

    /**
     * Finish the parsing of a variable declaration statement, starting right after the name.
     *
     * @param annotations  list of annotations or null
     * @param type         the type of the variable
     * @param name         the name of the variable
     *
     * @return a VariableDeclarationStatement
     */
    Statement parseVariableDeclarationAfterName(List<AnnotationExpression> annotations,
                                                TypeExpression type, Token name)
        {
        Expression value = null;
        Token      op    = match(Id.ASN);
        if (op != null)
            {
            value = parseExpression();
            }
        expect(Id.SEMICOLON);

        // apply any annotations to the variable type; "@A @B @C T" is "@A of (@B of (@C of T))"
        if (annotations != null)
            {
            for (int i = annotations.size() - 1; i >= 0; --i)
                {
                AnnotationExpression annotation = annotations.get(i);
                type = new AnnotatedTypeExpression(annotation, type);
                }
            }

        Statement stmt = new VariableDeclarationStatement(type, name, value == null);
        if (value != null)
            {
            stmt = new AssignmentStatement(stmt, op, value);
            }
        return stmt;
        }

    /**
     * <p/><code><pre>
     * ParameterList MethodDeclarationFinish
     *
     * MethodDeclarationFinish
     *     ;
     *     StatementBlock
     * </pre></code>
     *
     * @return a MethodDeclarationStatement
     */
    MethodDeclarationStatement parseMethodDeclarationAfterName(long lStartPos, Expression exprCondition,
            Token doc, List<Token> modifiers, List<AnnotationExpression> annotations,
            List<Parameter> typeVars, Token conditional, List<Parameter> returns, Token name)
        {
        List<TypeExpression> redundantReturns = parseTypeParameterTypeList(false, true);
        List<Parameter>      params           = parseParameterList(true);
        long                 lEndPos          = prev().getEndPosition();
        StatementBlock       body;
        switch (peek().getId())
            {
            case SEMICOLON:
                body = null;
                expect(Id.SEMICOLON);
                break;
            case ASN:
                Token eq = expect(Id.ASN);
                Expression expr = parseExpression();
                Token semi = expect(Id.SEMICOLON);
                ReturnStatement stmt = new ReturnStatement(eq, expr);
                body = new StatementBlock(Arrays.asList(stmt), stmt.getStartPosition(),
                                                               semi.getEndPosition());
                break;
            default:
                body = parseStatementBlock();
                break;
            }

        Token          tokFinally  = null;
        StatementBlock stmtFinally = null;
        if (body != null)
            {
            // check for "constructor finally" block
            if (name.getId() == Id.CONSTRUCT && (tokFinally = match(Id.FINALLY)) != null)
                {
                stmtFinally = parseStatementBlock();
                lEndPos = stmtFinally.getEndPosition();
                }
            else
                {
                lEndPos = body.getEndPosition();
                }
            }

        return new MethodDeclarationStatement(lStartPos, lEndPos, exprCondition, modifiers,
                annotations, typeVars, conditional, returns, name, redundantReturns, params,
                body, tokFinally, stmtFinally, doc);
        }

    /**
     * Parse the remainder of a property statement.
     *
     * <p/><code><pre>
     * PropertyDeclarationFinish
     *     "=" Expression ";"
     *     "." Name Parameters MethodBody
     *     TypeCompositionBody
     * </pre></code>
     *
     * @return a StatementBlock
     */
    PropertyDeclarationStatement parsePropertyDeclarationFinish(long lStartPos,
            Expression exprCondition, Token doc, List<Token> modifiers,
            List<AnnotationExpression> annotations, TypeExpression type, Token name)
        {
        StatementBlock body       = null;
        long           lEndPos    = prev().getEndPosition();
        boolean        fNeedsSemi = false;
        if (match(Id.DOT) != null)
            {
            // "." Name Parameters MethodBody
            Token           methodName = expect(Id.IDENTIFIER);
            List<Parameter> params     = parseParameterList(true);
            StatementBlock  block;
            Token           eq         =  match(Id.ASN);
            if (eq != null)
                {
                Expression expr = parseExpression();
                ReturnStatement stmt = new ReturnStatement(eq, expr);
                block = new StatementBlock(Arrays.asList(stmt), stmt.getStartPosition(),
                                                                stmt.getEndPosition());
                fNeedsSemi = true;
                }
            else
                {
                block      = parseStatementBlock();
                }

            MethodDeclarationStatement method = new MethodDeclarationStatement(
                    methodName.getStartPosition(), block.getEndPosition(), null, null, null, null,
                    null, null, methodName, null, params, block, null, null, null);
            body    = new StatementBlock(Collections.singletonList(method),
                    method.getStartPosition(), method.getEndPosition());
            lEndPos = body.getEndPosition();
            }
        else if (peek(Id.L_CURLY))
            {
            // pretend we're parsing a class (use the property name token as the basis)
            body    = parseTypeCompositionBody(new Token(name.getStartPosition(),
                                               name.getEndPosition(), Id.CLASS));
            lEndPos = body.getEndPosition();
            }

        Token      tokAsn = match(Id.ASN);
        Expression value  = null;
        if (tokAsn != null)
            {
            // "=" Expression ";"
            value   = parseExpression();
            lEndPos = value.getEndPosition();
            expect(Id.SEMICOLON);
            }
        else if (body == null || fNeedsSemi)
            {
            expect(Id.SEMICOLON);
            }

        return new PropertyDeclarationStatement(lStartPos, lEndPos, exprCondition,
                modifiers, annotations, type, name, tokAsn, value, body, doc);
        }

    /**
     * Parse a block statement.
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a StatementBlock
     */
    StatementBlock parseStatementBlock()
        {
        Token tokStart = expect(Id.L_CURLY);
        List<Statement> stmts = new ArrayList<>();
        while (match(Id.R_CURLY) == null)
            {
            stmts.add(parseStatement());
            }

        return new StatementBlock(stmts, tokStart.getStartPosition(), prev().getEndPosition());
        }

    /**
     * Parse an Ecstasy statement.
     *
     * <p/><code><pre>
     * Statement
     *     TypeComposition
     *     PropertyDeclarationStatement
     *     MethodDeclaration
     *     StatementBlock
     *     VariableDeclarationStatement
     *     AssignmentStatement
     *     ExpressionStatement
     *     LabeledStatement
     *     AssertStatement
     *     "break" Name-opt ";"
     *     "continue" Name-opt ";"
     *     "do" StatementBlock "while" "(" ConditionalDeclaration-opt Expression ")" ";"
     *     ForStatement
     *     IfStatement
     *     ImportStatement
     *     ReturnStatement
     *     SwitchStatement
     *     TryStatement
     *     TypeDefStatement
     *     "using" ResourceDeclaration StatementBlock
     *     "while" "(" ConditionalDeclaration-opt Expression ")" StatementBlock
     *
     * PropertyDeclarationStatement
     *     "static" TypeExpression Name PropertyDeclarationFinish-opt
     *
     * StatementBlock
     *     "{" Statements "}"
     *
     * VariableDeclarationStatement
     *     TypeExpression Name VariableInitializerFinish-opt ";"
     *
     * AssignmentStatement
     *     Assignee AssignmentOperator Expression ";"
     *
     * Assignee
     *     Assignable
     *     "(" AssignableList "," Assignable ")"
     *
     * AssignableList
     *     Assignable
     *     AssignableList "," Assignable
     *
     * # Assignable turns out to be just an Expression that meets certain requirements, i.e. one that ends
     * # with a Name or an ArrayIndex
     * Assignable
     *     Name
     *     Expression "." Name
     *     Expression ArrayIndex
     *
     * ExpressionStatement
     *     Expression ";"
     *
     * LabeledStatement
     *     Name ":" Statement
     *
     * AssertStatement
     *     AssertInstruction ConditionList-opt AssertMessage-opt ";"
     *
     * AssertInstruction                      # (when active, what gets thrown)
     *     "assert"                           # runtime, IllegalState
     *     "assert:arg"                       # runtime, IllegalArgument
     *     "assert:bounds"                    # runtime, OutOfBounds
     *     "assert:T0D0"                      # runtime, NotImplemented
     *     "assert:once"                      # runtime, Assertion (only tested "the first time")
     *     "assert:rnd(" Expression ")"       # runtime (sampling), IllegalState
     *     "assert:test"                      # test mode (e.g. CI/QC), Assertion
     *     "assert:debug"                     # debug mode, breakpoint-only (i.e. no throw)
     *
     * AssertMessage
     *     "as" Expression
     *
     * ForStatement
     *     "for" "(" ForCondition ")" StatementBlock
     *
     * IfStatement
     *     "if" "(" ConditionalDeclaration-opt Expression ")" StatementBlock ElseStatement-opt
     *
     * ImportStatement
     *     "import" QualifiedName ImportAlias-opt ";"
     *
     * ReturnStatement
     *     "return" ReturnValue-opt ";"
     *
     * SwitchStatement
     *     switch "(" Expression ")" "{" SwitchBlocks-opt SwitchLabels-opt "}"
     *
     * TryStatement
     *     "try" ResourceDeclaration-opt StatementBlock TryFinish
     *
     * TypeDefStatement
     *     "typedef" TypeExpression Name ";"
     * </pre></code>
     *
     * @return a statement
     */
    Statement parseStatement()
        {
        switch (peek().getId())
            {
            case SEMICOLON:
                Token tokSemi = match(Id.SEMICOLON);
                log(Severity.ERROR, NO_EMPTY_STMT, tokSemi.getStartPosition(), tokSemi.getEndPosition());
                return new StatementBlock(null, tokSemi.getStartPosition(), tokSemi.getEndPosition());

            case L_CURLY:
                return parseStatementBlock();

            case ASSERT:
            case ASSERT_RND:
            case ASSERT_ARG:
            case ASSERT_BOUNDS:
            case ASSERT_TODO:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                return parseAssertStatement();

            case BREAK:
            case CONTINUE:
                {
                Token keyword = current();
                Token name    = match(Id.IDENTIFIER);
                Statement stmt = keyword.getId() == Id.BREAK
                        ? new BreakStatement(keyword, name)
                        : new ContinueStatement(keyword, name);

                expect(Id.SEMICOLON);
                return stmt;
                }

            case DO:
                return parseDoStatement();

            case FOR:
                return parseForStatement();

            case IF:
                return parseIfStatement();

            case IMPORT:
                return parseImportStatement(null);

            case RETURN:
                return parseReturnStatement();

            case SWITCH:
                return parseSwitchStatement();

            case TRY:
                return parseTryStatement();

            case TYPEDEF:
                return parseTypeDefStatement(null, null);

            case USING:
                return parseUsingStatement();

            case WHILE:
                return parseWhileStatement();

            case CLASS:
            case INTERFACE:
            case SERVICE:
            case CONST:
            case ENUM:
            case MIXIN:
                // this is obviously a TypeComposition
                return parseTypeCompositionStatement();

            case IDENTIFIER:
                {
                // check if it is a LabeledStatement
                Token name = expect(Id.IDENTIFIER);
                if (!name.hasTrailingWhitespace() && peek(Id.COLON) && peek().hasTrailingWhitespace())
                    {
                    expect(Id.COLON);
                    return new LabeledStatement(name, parseStatement());
                    }
                else
                    {
                    putBack(name);
                    }
                }
                // fall through
            default:
                return parseTypeCompositionComponent(null, true);
            }
        }

    /**
     * Parse an "assert" statement.
     *
     * <p/><code><pre>
     * AssertStatement
     *     AssertInstruction ConditionList-opt AssertMessage-opt ";"
     *
     * AssertInstruction                      # (when active, what gets thrown)
     *     "assert"                           # runtime, IllegalState
     *     "assert:arg"                       # runtime, IllegalArgument
     *     "assert:bounds"                    # runtime, OutOfBounds
     *     "assert:T0D0"                      # runtime, NotImplemented
     *     "assert:once"                      # runtime, Assertion (only tested "the first time")
     *     "assert:rnd(" Expression ")"       # runtime (sampling), IllegalState
     *     "assert:test"                      # test mode (e.g. CI/QC), Assertion
     *     "assert:debug"                     # debug mode, breakpoint-only (i.e. no throw)
     *
     * AssertMessage
     *     "as" Expression
     * </pre></code>
     *
     * @return an "assert" statement
     */
    Statement parseAssertStatement()
        {
        Token keyword = current();
        long  lEndPos = keyword.getEndPosition();

        Expression exprRate = null;
        if (keyword.getId() == Id.ASSERT_RND)
            {
            // for readability / clarity, whitespace after the "assert:rnd" and before the "(..)"
            // is forbidden, because the sample range "(..)" belongs to the assert, and is not
            // a condition that follows the assert
            if (keyword.hasTrailingWhitespace())
                {
                long lPos = keyword.getEndPosition();
                char ch   = m_lexer.charAt(lPos);
                log(Severity.ERROR, Lexer.EXPECTED_CHAR, lPos, lPos,
                        "(", Handy.appendChar(new StringBuilder(), ch).toString());
                }

            expect(Id.L_PAREN);
            exprRate = parseExpression();
            lEndPos  = expect(Id.R_PAREN).getEndPosition();
            }

        List<AstNode> conds = null;
        if (!peek(Id.SEMICOLON) && !peek(Id.AS))
            {
            conds   = parseConditionList();
            lEndPos = conds.getLast().getEndPosition();
            }

        Expression exprMsg = null;
        if (match(Id.AS) != null)
            {
            exprMsg = parseExpression();
            lEndPos = exprMsg.getEndPosition();
            }

        expect(Id.SEMICOLON);
        return new AssertStatement(keyword, exprRate, conds, exprMsg, lEndPos);
        }

    /**
     * Parse a "do" statement.
     *
     * <p/><code><pre>
     * DoStatement
     *     "do" StatementBlock "while" "(" ConditionList ")" ";"
     * </pre></code>
     *
     * @return a "do" statement
     */
    Statement parseDoStatement()
        {
        Token keyword = expect(Id.DO);
        StatementBlock block = parseStatementBlock();
        expect(Id.WHILE);
        expect(Id.L_PAREN);
        List<AstNode> conds = parseConditionList();
        long lEndPos = expect(Id.R_PAREN).getEndPosition();
        expect(Id.SEMICOLON);
        return new WhileStatement(keyword, conds, block, lEndPos);
        }

    /**
     * Parse a "for" statement.
     *
     * <p/><code><pre>
     * ForStatement
     *     "for" "(" ForCondition ")" StatementBlock
     *
     * ForCondition
     *     VariableInitializationList-opt ";" Expression-opt ";" VariableModificationList-opt
     *     OptionalDeclarationList ":" Expression
     *
     * VariableInitializationList
     *     VariableInitializer
     *     VariableInitializationList "," VariableInitializer
     *
     * VariableInitializer
     *     VariableTypeExpression-opt Name VariableInitializerFinish
     *
     * VariableTypeExpression
     *     "var"
     *     "val"
     *     TypeExpression
     *
     * VariableInitializerFinish
     *     "=" Expression
     *
     * VariableModificationList
     *     VariableModification
     *     VariableModificationList "," VariableModification
     *
     * VariableModification
     *     Assignment
     *     Expression
     * </pre></code>
     *
     * @return a for statement
     */
    Statement parseForStatement()
        {
        Token keyword = expect(Id.FOR);
        expect(Id.L_PAREN);

        // figure out which form of the "for" statement this is:
        // 1) VariableInitializationList-opt ";" Expression-opt ";" VariableModificationList-opt
        // 2) OptionalDeclarationList ":" Expression
        List<AstNode> init = new ArrayList<>();
        if (!peek(Id.SEMICOLON))
            {
            boolean fFirst = true;
            do
                {
                AstNode LVal = peekMultiVariableInitializer();
                if (LVal == null)
                    {
                    Token tokType = matchVarOrVal();
                    if (tokType != null)
                        {
                        Token tokName = match(Id.IDENTIFIER);
                        if (tokName == null)
                            {
                            // var and val are not reserved keywords; they are context sensitive types
                            putBack(tokType);
                            }
                        else
                            {
                            LVal = new VariableDeclarationStatement(new VariableTypeExpression(tokType), tokName, false);
                            }
                        }

                    if (LVal == null)
                        {
                        Expression expr = parseTernaryExpression();
                        if (peek(Id.IDENTIFIER))
                            {
                            LVal = new VariableDeclarationStatement(expr.toTypeExpression(), expect(Id.IDENTIFIER), false);
                            }
                        else
                            {
                            // the expression has to be an L-Value
                            if (!expr.isLValueSyntax())
                                {
                                log(Severity.ERROR, NOT_ASSIGNABLE, expr.getStartPosition(), expr.getEndPosition());
                                }
                            LVal = expr;
                            }
                        }
                    }

                if (fFirst && peek(Id.COLON))
                    {
                    AssignmentStatement cond = new AssignmentStatement(
                            LVal, expect(Id.COLON), parseExpression(), false);
                    expect(Id.R_PAREN);
                    return new ForEachStatement(keyword, cond, parseStatementBlock());
                    }

                init.add(new AssignmentStatement(LVal, expect(Id.ASN), parseExpression(), false));
                fFirst = false;
                }
            while (match(Id.COMMA) != null);
            }

        // parse the second part
        expect(Id.SEMICOLON);
        List<AstNode> conds = (peek(Id.SEMICOLON)) ? null : parseConditionList();
        expect(Id.SEMICOLON);

        // parse the third part
        List<Statement> update = new ArrayList<>();
        while (!peek(Id.R_PAREN))
            {
            if (!update.isEmpty())
                {
                expect(Id.COMMA);
                }

            Expression exprUpdate = parseExpression();
            Token.Id   op         = Id.ASN;
            switch (peek().getId())
                {
                case R_PAREN:
                case COMMA:
                    update.add(new ExpressionStatement(exprUpdate, false));
                    break;

                case ADD_ASN:
                case SUB_ASN:
                case MUL_ASN:
                case DIV_ASN:
                case MOD_ASN:
                case SHL_ASN:
                case SHR_ASN:
                case USHR_ASN:
                case BIT_AND_ASN:
                case BIT_OR_ASN:
                case BIT_XOR_ASN:
                case COND_ASN:
                case COND_AND_ASN:
                case COND_OR_ASN:
                case COND_NN_ASN:
                case COND_ELSE_ASN:
                    op = peek().getId();
                    // fall through
                default:
                    // the expression has to be an L-Value
                    if (!exprUpdate.isLValueSyntax())
                        {
                        log(Severity.ERROR, NOT_ASSIGNABLE,
                                exprUpdate.getStartPosition(), exprUpdate.getEndPosition());
                        }
                    update.add(new AssignmentStatement(exprUpdate, expect(op), parseExpression(), false));
                    break;
                }
            }

        expect(Id.R_PAREN);
        return new ForStatement(keyword, (List<Statement>) (List) init, conds, update, parseStatementBlock());
        }

    /**
     * Parse an "if" statement.
     *
     * <p/><code><pre>
     * IfStatement
     *     "if" "(" ConditionList ")" StatementBlock ElseStatement-opt
     *
     * ElseStatement
     *     "else" IfStatement
     *     "else" StatementBlock
     * </pre></code>
     *
     * @return an "if" statement
     */
    Statement parseIfStatement()
        {
        Token keyword = expect(Id.IF);
        expect(Id.L_PAREN);
        List<AstNode> conds = parseConditionList();
        expect(Id.R_PAREN);
        StatementBlock block = parseStatementBlock();

        if (match(Id.ELSE) == null)
            {
            return new IfStatement(keyword, conds, block);
            }
        else
            {
            Statement stmtElse = peek(Id.IF) ? parseIfStatement() : parseStatementBlock();
            return new IfStatement(keyword, conds, block, stmtElse);
            }
        }

    /**
     * Parse a ConditionList, which is used in "assert", "if", "for", "while", and "do" statements.
     *
     * <p/><code><pre>
     * ConditionList
     *     Condition
     *     ConditionList, Condition
     *
     * Condition
     *     Expression
     *     OptionalDeclaration ConditionalAssignmentOp Expression
     *     ( OptionalDeclarationList, OptionalDeclaration ) ConditionalAssignmentOp Expression
     *
     * ConditionalAssignmentOp
     *     :=
     *     ?=
     *
     * OptionalDeclarationList
     *     OptionalDeclaration
     *     OptionalDeclarationList "," OptionalDeclaration
     *
     * OptionalDeclaration
     *     Assignable
     *     VariableTypeExpression Name
     *
     * # Assignable turns out to be just an Expression that meets certain requirements, i.e. one
     * # that ends with a Name or an ArrayIndexes
     * Assignable
     *     Name
     *     TernaryExpression "." Name
     *     TernaryExpression ArrayIndexes
     *
     * VariableTypeExpression
     *     "val"
     *     "var"
     *     TypeExpression
     * </pre></code>
     *
     * @return the Expression that provides the condition, or the conditional AssignmentStatement
     */
    List<AstNode> parseConditionList()
        {
        List<AstNode> list = new ArrayList<>(4);
        list.add(parseCondition(false));
        while (match(Id.COMMA) != null && !peek(Id.R_PAREN))
            {
            list.add(parseCondition(false));
            }
        return list;
        }

    AstNode parseCondition(boolean fNegated)
        {
        if (!fNegated && peek(Id.NOT))
            {
            // test for a negated conditional assignment
            Token               tokNot  = null;
            AssignmentStatement stmtAsn = null;
            try (SafeLookAhead attempt = new SafeLookAhead())
                {
                tokNot = expect(Id.NOT);
                if (match(Id.L_PAREN) != null)
                    {
                    AstNode stmtPeek = parseCondition(true);
                    if (attempt.isClean() && stmtPeek instanceof AssignmentStatement)
                        {
                        stmtAsn = (AssignmentStatement) stmtPeek;
                        attempt.keepResults();
                        }
                    }
                }
            catch (CompilerException ignore) {}

            if (stmtAsn != null)
                {
                stmtAsn.negate(tokNot, expect(Id.R_PAREN));
                return stmtAsn;
                }
            }

        // it could be an OptionalDeclarationList
        AstNode LVal = peekMultiVariableInitializer();

        if (LVal == null)
            {
            Token tokType = matchVarOrVal();
            if (tokType != null)
                {
                Token tokName = match(Id.IDENTIFIER);
                if (tokName == null)
                    {
                    // var and val are not reserved keywords; they are context sensitive types
                    putBack(tokType);
                    }
                else
                    {
                    LVal = new VariableDeclarationStatement(new VariableTypeExpression(tokType), tokName, false);
                    }
                }

            if (LVal == null)
                {
                Expression expr = parseExpression();
                if (peek(Id.IDENTIFIER))
                    {
                    LVal = new VariableDeclarationStatement(expr.toTypeExpression(), expect(Id.IDENTIFIER), false);
                    }
                else
                    {
                    switch (peek().getId())
                        {
                        case COND_ASN:
                        case COND_NN_ASN:
                            // the expression has to be the L-Value
                            if (!expr.isLValueSyntax())
                                {
                                log(Severity.ERROR, NOT_ASSIGNABLE, expr.getStartPosition(), expr.getEndPosition());
                                }
                            LVal = expr;
                            break;

                        default:
                            // if we're testing for a negated conditional assignment, then an
                            // expression isn't what we're looking for, so return null; otherwise,
                            // the expression is the condition, so return it
                            return fNegated ? null : expr;
                        }
                    }
                }
            }

        Token tokAsn = match(Id.COND_NN_ASN);
        if (tokAsn == null)
            {
            tokAsn = expect(Id.COND_ASN);
            }

        return new AssignmentStatement(LVal, tokAsn, parseExpression(), false);
        }

    /**
     * Parse an import statement.
     *
     * <p/><code><pre>
     * ImportStatement
     *     "import" QualifiedName ImportFinish
     *
     * ImportFinish
     *     ";"
     *     "as" Name ";"
     *     NoWhitespace ".*" ";"
     * </pre></code>
     *
     * @return an ImportStatement
     */
    ImportStatement parseImportStatement(Expression exprCond)
        {
        List<Token> qualifiedName = new ArrayList<>();
        Token       simpleName    = null;
        Token       keyword       = expect(Id.IMPORT);

        // parse qualified name
        boolean first = true;
        while (first || (match(Id.DOT) != null))
            {
            if (!first && match(Id.MUL) != null)
                {
                Token star = prev();
                expect(Id.SEMICOLON);
                return new ImportStatement(exprCond, keyword, qualifiedName, star);
                }

            simpleName = expect(Id.IDENTIFIER);
            qualifiedName.add(simpleName);
            first = false;
            }

        // optional alias override
        if (simpleName != null && match(Id.AS) != null)
            {
            // parse simple name
            simpleName = expect(Id.IDENTIFIER);
            }

        expect(Id.SEMICOLON);

        return new ImportStatement(exprCond, keyword, simpleName, qualifiedName);
        }

    /**
     * Parse a return statement.
     *
     * <p/><code><pre>
     * ReturnStatement
     *     "return" ReturnValue-opt ";"
     *
     * ReturnValue
     *     TupleLiteral
     *     ExpressionList
     * </pre></code>
     *
     * @return a return statement
     */
    ReturnStatement parseReturnStatement()
        {
        Token keyword = expect(Id.RETURN);
        if (match(Id.SEMICOLON) != null)
            {
            return new ReturnStatement(keyword);
            }

        // note: it is possible that the expression list is parenthesized, in which case it will be
        //       parsed as a single expression (a tuple literal), and the compiler will have to
        //       it out later
        List<Expression> exprs = parseExpressionList();
        expect(Id.SEMICOLON);
        return new ReturnStatement(keyword, exprs);
        }

    /**
     * Parse a "switch" statement.
     *
     * <p/><code><pre>
     * SwitchStatement
     *     switch "(" SwitchCondition-opt ")" "{" SwitchBlocks "}"
     *
     * SwitchBlocks
     *     SwitchBlock
     *     SwitchBlocks SwitchBlock
     *
     * # the SwitchBlockFinish is required unless the SwitchBlock does not complete (e.g. ends with a "throw")
     * SwitchBlock
     *     SwitchLabels Statements SwitchBlockFinish-opt
     *
     * SwitchLabels
     *     SwitchLabel
     *     SwitchLabels SwitchLabel
     *
     * # 1) for a SwitchStatement with a SwitchCondition, each "case" expression must be a
     * #    "constant expression", i.e. compiler has to be able to determine the value (or a constant that
     * #    points to a value that is constant at run-time, e.g. a property constant for a static property)
     * # 2) for a SwitchStatement without a SwitchCondition, each "case" expression must be of type Boolean
     * #    and is not required to be a constant
     * # 3) for a SwitchStatement with a SwitchCondition, a case may specify a list of values, which is
     * #    semantically identical to having that same number of "case" labels each with one of those values.
     * # 4) for a SwitchStatement with multiple SwitchConditionExpressions in the SwitchCondition or with
     * #    a single SwitchConditionExpression of a tuple type, each "case" value must be either:
     * #    (a) a parenthesized list of expressions (a compatible tuple constant), or
     * #    (b) a constant expression of a compatible tuple type
     * # 5) each "case" expression may be any of:
     * #    (a) the type of the corresponding expression (or tuple field value) in the SwitchCondition;
     * #    (b) a Range of that type; or
     * #    (c) the wild-card "_" (compiled as the "blackhole" constant)
     * #    a CaseExpressionList of all wild-cards is semantically equivalent to the use of a "default"
     * #    label, and would predictably conflict with the same if both were specified.
     * SwitchLabel
     *     "case" CaseOptionList ":"
     *     "default" ":"
     *
     * SwitchBlockFinish:
     *     BreakStatement
     *     ContinueStatement
     *
     * BreakStatement:
     *     "break" Name-opt ";"
     *
     * ContinueStatement:
     *     "continue" Name-opt ";"
     * </pre></code>
     *
     * @return a switch statement
     */
    Statement parseSwitchStatement()
        {
        Token keyword = expect(Id.SWITCH);
        expect(Id.L_PAREN);
        List<AstNode> cond = parseSwitchCondition();
        expect(Id.R_PAREN);

        Token           tokLCurly = expect(Id.L_CURLY);
        List<Statement> stmts     = new ArrayList<>();
        boolean         fDefault  = false;
        do
            {
            fDefault |= parseSwitchBlock(stmts, fDefault);
            }
        while (match(Id.R_CURLY) == null);

        // there must be at least one case
        if (stmts.isEmpty())
            {
            putBack(prev());
            expect(Id.CASE);
            }

        return new SwitchStatement(keyword, cond, new StatementBlock(stmts,
                tokLCurly.getStartPosition(), prev().getEndPosition()));
        }

    private boolean parseSwitchBlock(List<Statement> stmts, boolean fDefault)
        {
        boolean fAnyLabels = false;
        boolean fAnyStmts  = false;

        while (true)
            {
            switch (peek().getId())
                {
                case CASE:
                    if (fAnyStmts)
                        {
                        return fDefault;
                        }

                    stmts.add(new CaseStatement(current(), parseCaseOptionList(), expect(Id.COLON)));
                    fAnyLabels = true;
                    break;

                case DEFAULT:
                    {
                    if (fAnyStmts)
                        {
                        return fDefault;
                        }

                    Token tokDefault = current();
                    if (fDefault)
                        {
                        log(Severity.ERROR, REPEAT_DEFAULT, tokDefault.getStartPosition(), tokDefault.getEndPosition());
                        }

                    stmts.add(new CaseStatement(tokDefault, null, expect(Id.COLON)));
                    fAnyLabels = true;
                    fDefault   = true;
                    break;
                    }

                case BREAK:
                case CONTINUE:
                default:
                    if (!fAnyLabels)
                        {
                        log(Severity.ERROR, MISSING_CASE, peek().getStartPosition(), peek().getEndPosition());
                        if (stmts.isEmpty())
                            {
                            throw new CompilerException("switch must start with a case");
                            }
                        }
                    stmts.add(parseStatement());
                    fAnyStmts = true;
                    break;

                case R_CURLY:
                    if (stmts.isEmpty())
                        {
                        log(Severity.ERROR, MISSING_CASE, peek().getStartPosition(), peek().getEndPosition());
                        }
                    return fDefault;
                }
            }
        }

    /**
     * Parse a "switch" condition.
     *
     * <p/><code><pre>
     * SwitchCondition
     *     SwitchConditionExpression
     *     SwitchCondition "," SwitchConditionExpression
     *
     * SwitchConditionExpression
     *     VariableInitializer
     *     Expression
     *
     * VariableInitializer
     *     "(" OptionalDeclarationList "," OptionalDeclaration ")" VariableInitializerFinish
     *     OptionalDeclaration VariableInitializerFinish
     *
     * OptionalDeclarationList
     *     OptionalDeclaration
     *     OptionalDeclarationList "," OptionalDeclaration
     *
     * OptionalDeclaration
     *     Assignable
     *     VariableTypeExpression Name
     *
     * VariableTypeExpression
     *     "val"
     *     "var"
     *     TypeExpression
     *
     * VariableInitializerFinish
     *     "=" Expression
     * </pre></code>
     *
     * @return a switch condition, or null if there is none
     */
    private List<AstNode> parseSwitchCondition()
        {
        // no condition expression
        if (peek(Id.R_PAREN))
            {
            return null;
            }

        List<AstNode> list = new ArrayList<>();
        list.add(parseSwitchConditionExpression());
        while (match(Id.COMMA) != null)
            {
            list.add(parseSwitchConditionExpression());
            }
        return list;
        }

    private AstNode parseSwitchConditionExpression()
        {
        // there's only one real left-to-right challenge here, which is an expression that begins
        // with a parenthesized expression, such as "(x+y)*2", because the opening parenthesis are
        // shared with the list form of the VariableInitializer; after parsing the first expression
        // inside the parenthesis, it will either (i) start with "var" or "val", or (ii) be followed
        // by an identifier or a comma if it is a multi VariableInitializer
        AstNode LVal = peekMultiVariableInitializer();

        // there is either a single (i.e. not the multi-form of the) VariableInitializer, or there
        // is a single expression that is the entire switch condition
        if (LVal == null)
            {
            Token tokType = matchVarOrVal();
            if (tokType != null)
                {
                Token tokName = match(Id.IDENTIFIER);
                if (tokName == null)
                    {
                    // var and val are not reserved keywords; they are context sensitive types
                    putBack(tokType);
                    }
                else
                    {
                    LVal = new VariableDeclarationStatement(new VariableTypeExpression(tokType), tokName, false);
                    }
                }

            if (LVal == null)
                {
                Expression expr = parseExpression();
                if (peek(Id.IDENTIFIER))
                    {
                    LVal = new VariableDeclarationStatement(expr.toTypeExpression(), expect(Id.IDENTIFIER), false);
                    }
                else if (peek(Id.ASN))
                    {
                    // the expression has to be the L-Value
                    if (!expr.isLValueSyntax())
                        {
                        log(Severity.ERROR, NOT_ASSIGNABLE, expr.getStartPosition(), expr.getEndPosition());
                        }
                    LVal = expr;
                    }
                else
                    {
                    // the SwitchCondition is just an expression
                    return expr;
                    }
                }
            }

        return new AssignmentStatement(LVal, expect(Id.ASN), parseExpression(), false);
        }

    /**
     * Parse an expression list for a case label, but one that does not look for a trailing ':'.
     *
     * <p/><code><pre>
     * SwitchLabel
     *     "case" CaseOptionList ":"
     *     "default" ":"
     *
     * CaseOptionList:
     *     CaseOption
     *     CaseOptionList "," CaseOption
     *
     * CaseOption:
     *     "(" CaseExpressionList "," CaseExpression ")"
     *     SafeCaseExpression
     *
     * CaseExpressionList:
     *     CaseExpression
     *     CaseExpressionList "," CaseExpression
     *
     * CaseExpression:
     *     "_"
     *     Expression
     *
     * # parse for "case TernaryExpression:" because Expression parsing looks for a possible trailing ':'
     * SafeCaseExpression:
     *     "_"
     *     TernaryExpression
     * </pre></code>
     *
     * @return
     */
    private List<Expression> parseCaseOptionList()
        {
        ArrayList<Expression> listCaseOptions = new ArrayList<>();
        do
            {
            // check for possible tuple form: "(" CaseExpressionList "," CaseExpression ")"
            if (peek(Id.L_PAREN))
                {
                Mark mark = mark();
                long lStart = expect(Id.L_PAREN).getStartPosition();

                Expression expr = peek(Id.ANY)
                        ? new IgnoredNameExpression(current())
                        : parseExpression();

                // if the expression is followed by a comma, then our guess was correct; otherwise,
                // we shouldn't be here (must back up and assume that the opening parenthesis is the
                // beginning of a single expression CaseOption)
                if (peek(Id.COMMA))
                    {
                    ArrayList<Expression> listTupleValues = new ArrayList<>();
                    listTupleValues.add(expr);
                    while (match(Id.COMMA) != null)
                        {
                        listTupleValues.add(peek(Id.ANY)
                                ? new IgnoredNameExpression(current())
                                : parseExpression());
                        }
                    long lEnd = expect(Id.R_PAREN).getEndPosition();
                    listCaseOptions.add(new TupleExpression(null,  listTupleValues, lStart, lEnd));

                    // process next case option
                    continue;
                    }

                // revert and assume that the case option is a single ternary expression
                restore(mark);
                }

            // single SafeCaseExpression
            listCaseOptions.add(peek(Id.ANY)
                    ? new IgnoredNameExpression(current())
                    : parseTernaryExpression());
            }
        while (match(Id.COMMA) != null);

        return listCaseOptions;
        }

    /**
     * Parse the multi-form of the VariableInitializer construct, or nothing.
     *
     * @return null or a MultipleLValueStatement
     */
    MultipleLValueStatement peekMultiVariableInitializer()
        {
        if (!peek(Id.L_PAREN))
            {
            return null;
            }

        Mark mark = mark();
        expect(Id.L_PAREN);

        List<AstNode> listLVals = new ArrayList<>();
        while (true)
            {
            AstNode LVal    = null;
            boolean fFirst  = listLVals.isEmpty();
            Token   tokType = matchVarOrVal();
            if (tokType != null)
                {
                Token tokName = matchNameOrAny();
                if (tokName == null)
                    {
                    // var and val are not reserved keywords; they are context sensitive types
                    putBack(tokType);
                    }
                else
                    {
                    LVal = new VariableDeclarationStatement(new VariableTypeExpression(tokType), tokName, false);
                    }
                }

            if (LVal == null)
                {
                // assuming that we haven't already started building a list of declarations,
                // encountering an expression followed by anything other than an identifier (for
                // a declaration) or a comma indicates that we're going down the wrong path
                // REVIEW does this correctly parse @annotated types? should "Annotations" be added to "PrimaryExpression"? (seems logical)
                Expression expr = parseExtendedExpression();

                // next token   meaning
                // ----------   ----------------------------------------
                // COMMA        the expression must be an LValue (commit)
                // IDENTIFIER   expression was the type portion of a declaration (commit)
                // R_PAREN      list is empty    : oops, it's not a multi VariableInitializer
                //              list is NOT empty: done parsing the multi VariableInitializer
                // otherwise    list is empty    : oops, it's not a multi VariableInitializer
                //              list is NOT empty: it's an error
                if (peekNameOrAny())
                    {
                    // there is a variable declaration to use as an L-Value
                    LVal = new VariableDeclarationStatement(expr.toTypeExpression(), expect(Id.IDENTIFIER), false);
                    }
                else
                    {
                    LVal = expr;
                    }
                }

            // bail out if this is not a multiple value construct
            if (fFirst && !peek(Id.COMMA))
                {
                restore(mark);
                return null;
                }

            // the expression has to be the L-Value
            if (!LVal.isLValueSyntax())
                {
                log(Severity.ERROR, NOT_ASSIGNABLE, LVal.getStartPosition(), LVal.getEndPosition());
                }
            else
                {
                listLVals.add(LVal);
                }

            Token comma = match(Id.COMMA);
            if (!fFirst && match(Id.R_PAREN) != null)
                {
                return new MultipleLValueStatement(listLVals);
                }

            if (comma == null)
                {
                // comma is required
                expect(Id.COMMA);
                }
            }
        }

    /**
     * Parse a "try" statement.
     *
     * <p/><code><pre>
     * TryStatement
     *     "try" ResourceDeclaration-opt StatementBlock TryFinish
     *
     * ResourceDeclaration
     *     "(" VariableInitializationList ")"
     *
     * TryFinish
     *     Catches
     *     Catches-opt "finally" StatementBlock
     *
     * Catches
     *     Catch
     *     Catches Catch
     *
     * Catch
     *     "catch" "(" TypeExpression Name ")" StatementBlock
     * </pre></code>
     *
     * @return a "try" statement
     */
    Statement parseTryStatement()
        {
        Token keyword = expect(Id.TRY);
        List<AssignmentStatement> resources = null;
        if (match(Id.L_PAREN) != null)
            {
            resources = parseVariableInitializationList(true, false);
            expect(Id.R_PAREN);
            }

        StatementBlock block = parseStatementBlock();

        List<CatchStatement> catches = new ArrayList<>();
        while (match(Id.CATCH) != null)
            {
            long lStartPos = prev().getStartPosition();
            expect(Id.L_PAREN);
            VariableDeclarationStatement var = new VariableDeclarationStatement(
                    parseTypeExpression(), expectNameOrAny(), false);
            expect(Id.R_PAREN);
            catches.add(new CatchStatement(var, parseStatementBlock(), lStartPos));
            }

        StatementBlock catchall = null;
        if (match(Id.FINALLY) != null)
            {
            catchall = parseStatementBlock();
            }

        return new TryStatement(keyword, resources, block, catches, catchall);
        }

    /**
     * Parse a typedef statement.
     *
     * <p/><code><pre>
     * TypeDefStatement
     *     "typedef" Type Name ";"
     * </pre></code>
     *
     * @param tokenAccess  if the typedef is a type composition component, it can be declared with
     *                     an access modifier
     *
     * @return a TypedefStatement
     */
    TypedefStatement parseTypeDefStatement(Expression exprCond, Token tokenAccess)
        {
        Token keyword = expect(Id.TYPEDEF);

        TypeExpression type = parseExtendedTypeExpression();

        // required "as" keyword (note: previously optional)
        expect(Id.AS);

        Token simpleName = expect(Id.IDENTIFIER);

        expect(Id.SEMICOLON);

        return new TypedefStatement(exprCond, tokenAccess == null ? keyword : tokenAccess, type, simpleName);
        }

    /**
     * Parse a "using" statement.
     *
     * <p/><code><pre>
     * UsingStatement
     *     "using" ResourceDeclaration StatementBlock
     *
     * ResourceDeclaration
     *     "(" VariableInitializationList ")"
     * </pre></code>
     *
     * @return a "using" statement
     */
    Statement parseUsingStatement()
        {
        Token keyword = expect(Id.USING);
        expect(Id.L_PAREN);
        List<AssignmentStatement> resources = parseVariableInitializationList(true, true);
        expect(Id.R_PAREN);
        return new TryStatement(keyword, resources, parseStatementBlock(), null, null);
        }

    /**
     * Parse a "while" statement.
     *
     * <p/><code><pre>
     * WhileStatement
     *     "while" "(" ConditionList ")" StatementBlock
     * </pre></code>
     *
     * @return a "while" statement
     */
    Statement parseWhileStatement()
        {
        Token keyword = expect(Id.WHILE);
        expect(Id.L_PAREN);
        List<AstNode> conds = parseConditionList();
        expect(Id.R_PAREN);
        StatementBlock block = parseStatementBlock();
        return new WhileStatement(keyword, conds, block);
        }

    /**
     * Parse a variable initializer:
     *
     * <p/><code><pre>
     * VariableInitializationList
     *     VariableInitializer
     *     VariableInitializationList "," VariableInitializer
     *
     * VariableInitializer
     *     TypeExpression-opt Name VariableInitializerFinish
     *
     * VariableInitializerFinish
     *     "=" Expression
     * </pre></code>
     *
     * @param fRequired   true iff at least one VariableInitializer is required
     * @param fAllowExpr  true iff a naked expression can be used as an implicit variable initializer
     *
     * @return a statement representing the variable initializer
     */
    List<AssignmentStatement> parseVariableInitializationList(boolean fRequired, boolean fAllowExpr)
        {
        if (!fRequired)
            {
            switch (peek().getId())
                {
                case COMMA:
                case SEMICOLON:
                case R_PAREN:
                    return null;
                }
            }

        List<AssignmentStatement> list = new ArrayList<>();
        list.add(parseVariableInitializer(fAllowExpr));
        while (match(Id.COMMA) != null)
            {
            list.add(parseVariableInitializer(fAllowExpr));
            }

        return list;
        }

    /**
     * Parse a variable initializer:
     *
     * <p/><code><pre>
     * VariableInitializer
     *     TypeExpression-opt Name VariableInitializerFinish
     *
     * VariableInitializerFinish
     *     "=" Expression
     * </pre></code>
     *
     * @param fAllowExpr  allow an R-Value expression to be specified, as if an implicit "val _ ="
     *                    precedes it
     *
     * @return a statement representing the variable initializer
     */
    AssignmentStatement parseVariableInitializer(boolean fAllowExpr)
        {
        TypeExpression type = null;

        Token tokType = matchVarOrVal();
        if (tokType != null)
            {
            if (peekNameOrAny())
                {
                type = new VariableTypeExpression(tokType);
                }
            else
                {
                // var and val are not reserved keywords; they are context sensitive types
                putBack(tokType);
                }
            }

        if (type == null)
            {
            Expression expr = parseExpression();
            switch (peek().getId())
                {
                case ASN:
                case COND_ASN:
                case COND_NN_ASN:
                    return new AssignmentStatement(expr, current(), parseExpression(), false);

                case COMMA:
                case SEMICOLON:
                case R_PAREN:
                    if (fAllowExpr)
                        {
                        long  lPos    = expr.getStartPosition();
                              type    = new VariableTypeExpression(new Token(lPos, lPos, Id.VAL));
                        Token tokName = new Token(lPos, lPos, Id.ANY);
                        Token tokAsn  = new Token(lPos, lPos, Id.ASN);
                        VariableDeclarationStatement var = new VariableDeclarationStatement(type, tokName, false);
                        return new AssignmentStatement(var, tokAsn, expr);
                        }
                }

            type = expr.toTypeExpression();
            }

        VariableDeclarationStatement var = new VariableDeclarationStatement(type, expectNameOrAny(), false);

        Token tokAsn = switch (peek().getId())
            {
            case ASN, COND_ASN, COND_NN_ASN -> current();
            default                         -> expect(Id.ASN);
            };

        return new AssignmentStatement(var, tokAsn, parseExpression());
        }

    /**
     * Parse a condition expression.
     *
     * <p/><code><pre>
     * while parsing is of a generic Expression, there are only a few expression
     * forms that are permitted:
     * 1. StringLiteral "." "defined"
     * 2. QualifiedName "." "present"
     * 3. QualifiedName "." "versionMatches" "(" VersionLiteral ")"
     * 4. Any of 1-3 and 5 negated using "!"
     * 5. Any two of 1-5 combined using "&", "&&", "|", or "||"
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseLinkerCondition()
        {
        Expression expr = parseExpression();
        expr.validateCondition(m_errorListener);
        return expr;
        }

    /**
     * Parse a list of expressions.
     *
     * <p/><code><pre>
     * ExpressionList
     *     Expression
     *     ExpressionList "," Expression
     * </pre></code>
     *
     * @return an expression
     */
    List<Expression> parseExpressionList()
        {
        List<Expression> exprs = new ArrayList<>();
        do
            {
            exprs.add(parseExpression());
            }
        while (match(Id.COMMA) != null);
        return exprs;
        }

    /**
     * Parse an expression.
     *
     * @return an expression
     */
    Expression parseExpression()
        {
        return parseElseExpression(false);
        }

    /**
     * Parse an extended expression.
     *
     * @return an expression
     */
    Expression parseExtendedExpression()
        {
        return parseElseExpression(true);
        }

    /**
     * Parse an "else" expression (the "grounding" expression for any short-circuit expressions).
     *
     * <p/><code><pre>
     * Expression
     *     TernaryExpression
     *     TernaryExpression ":" Expression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseElseExpression(boolean fExtended)
        {
        Expression expr = parseTernaryExpression(fExtended);
        if (peek(Id.COLON))
            {
            expr = new ElseExpression(expr, current(), parseElseExpression(false));
            }
        return expr;
        }

    /**
     * Parse a ternary expression, which is the "a ? b : c" expression.
     *
     * <p/><code><pre>
     * TernaryExpression
     *     OrExpression
     *     OrExpression Whitespace "?" OrExpression ":" TernaryExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseTernaryExpression()
        {
        return parseTernaryExpression(false);
        }

    /**
     * Parse a ternary expression, which is the "a ? b : c" expression.
     *
     * <p/><code><pre>
     * TernaryExpression
     *     OrExpression
     *     OrExpression Whitespace "?" OrExpression ":" TernaryExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseTernaryExpression(boolean fExtended)
        {
        Expression expr = parseOrExpression(fExtended);
        if (peek(Id.COND))
            {
            expect(Id.COND);
            Expression exprThen = parseTernaryExpression(false);
            expect(Id.COLON);
            Expression exprElse = parseTernaryExpression(false);
            expr = new TernaryExpression(expr, exprThen, exprElse);
            }
        return expr;
        }

    /**
     * Parse a logical "or"/"xor" expression.
     *
     * <p/><code><pre>
     * OrExpression
     *     AndExpression
     *     OrExpression || AndExpression
     *     OrExpression ^^ AndExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseOrExpression(boolean fExtended)
        {
        Expression expr = parseAndExpression(fExtended);
        while (true)
            {
            switch (peek().getId())
                {
                case COND_OR:
                    expr = new CondOpExpression(expr, current(), parseAndExpression(false));
                    break;

                case COND_XOR:
                    expr = new RelOpExpression(expr, current(), parseAndExpression(false));
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse a logical "and" expression.
     *
     * <p/><code><pre>
     * AndExpression
     *     EqualityExpression
     *     AndExpression && EqualityExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseAndExpression(boolean fExtended)
        {
        Expression expr = parseEqualityExpression(fExtended);
        while (peek(Id.COND_AND))
            {
            expr = new CondOpExpression(expr, current(), parseEqualityExpression(false));
            }
        return expr;
        }

    /**
     * Parse an equality/inequality expression.
     *
     * <p/><code><pre>
     * EqualityExpression
     *     RelationalExpression
     *     EqualityExpression "==" RelationalExpression
     *     EqualityExpression "!=" RelationalExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseEqualityExpression(boolean fExtended)
        {
        Expression            expr     = parseRelationalExpression(fExtended);
        ArrayList<Expression> listExpr = null;
        ArrayList<Token>      listOps  = null;
        Token.Id              idPrev   = null;
        boolean               fErr     = false;
        while (peek(Id.COMP_EQ) || peek(Id.COMP_NEQ))
            {
            Token      tokCmp   = current();
            Expression exprNext = parseRelationalExpression(false);
            if (idPrev == null)
                {
                expr = new CmpExpression(expr, tokCmp, exprNext);
                }
            else
                {
                if (listExpr == null)
                    {
                    listExpr = new ArrayList<>();
                    listOps  = new ArrayList<>();
                    CmpExpression exprPrev = (CmpExpression) expr;
                    listExpr.add(exprPrev.getExpression1());
                    listOps .add(exprPrev.getOperator());
                    listExpr.add(exprPrev.getExpression2());
                    expr = null;
                    }

                listOps.add(tokCmp);
                listExpr.add(exprNext);

                Token.Id idCur = tokCmp.getId();
                if (idPrev != null && idCur != idPrev && !fErr)
                    {
                    log(Severity.ERROR, BAD_CHAINED_EQ,
                            tokCmp.getStartPosition(), tokCmp.getEndPosition());
                    fErr = true;
                    }
                }

            idPrev = tokCmp.getId();
            }

        return expr == null
                ? new CmpChainExpression(listExpr, listOps.toArray(new Token[0]))
                : expr;
        }

    /**
     * Parse a relational expression.
     *
     * <p/><code><pre>
     * RelationalExpression
     *     AssignmentExpression
     *     AssignmentExpression "<=>" AssignmentExpression
     *     RelationalExpression "<"   AssignmentExpression
     *     RelationalExpression "<="  AssignmentExpression
     *     RelationalExpression ">"   AssignmentExpression
     *     RelationalExpression ">="  AssignmentExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseRelationalExpression(boolean fExtended)
        {
        Expression expr = parseAssignmentExpression(fExtended);
        Unchained: switch (peek().getId())
            {
            case COMP_LT:
            case COMP_LTEQ:
            case COMP_GT:
            case COMP_GTEQ:
                expr = new CmpExpression(expr, current(), parseAssignmentExpression(false));
                switch (peek().getId())
                    {
                    case COMP_LT:
                    case COMP_LTEQ:
                    case COMP_GT:
                    case COMP_GTEQ:
                        // it's a chained comparison
                        break Unchained;
                    }
                return expr;

            case COMP_ORD:
                return new CmpExpression(expr, current(), parseAssignmentExpression(false));

            default:
                return expr;
            }

        ArrayList<Expression> listExpr = new ArrayList<>();
        ArrayList<Token>      listOps  = new ArrayList<>();
        CmpExpression exprPrev = (CmpExpression) expr;
        listExpr.add(exprPrev.getExpression1());
        listOps .add(exprPrev.getOperator());
        listExpr.add(exprPrev.getExpression2());
        boolean fFirstAscending = exprPrev.isAscending();

        boolean fErr = false;
        while (true)
            {
            boolean fThisAscending;
            switch (peek().getId())
                {
                case COMP_LT:
                case COMP_LTEQ:
                    fThisAscending = true;
                    break;

                case COMP_GT:
                case COMP_GTEQ:
                    fThisAscending = false;
                    break;

                default:
                    return new CmpChainExpression(listExpr, listOps.toArray(new Token[0]));
                }

            listOps .add(current());
            listExpr.add(parseAssignmentExpression(false));

            if (fThisAscending != fFirstAscending && !fErr)
                {
                log(Severity.ERROR, BAD_CHAINED_CMP,
                        expr.getStartPosition(), expr.getEndPosition());
                fErr = true;
                }
            }
        }

    /**
     * Parse an assignment expression.
     *
     * <p/><code><pre>
     * AssignmentExpression
     *     RangeExpression
     *     RangeExpression "<-" AssignmentExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseAssignmentExpression(boolean fExtended)
        {
        Expression expr = parseRangeExpression(fExtended);
        if (peek(Id.ASN_EXPR))
            {
            Expression exprLValue = expr;
            Token      tokAsnExpr = expect(Id.ASN_EXPR);
            Expression exprRValue = parseAssignmentExpression(false);    // i.e. right-to-left

            // for now, syntactic sugar
            //
            //   {
            //   val tmp=rvalue;
            //   lvalue=tmp;
            //   return tmp;
            //   }
            //
            // TODO eventually replace this with a proper expression
            //
            Token                        tokVal    = new Token(tokAsnExpr.getStartPosition(), tokAsnExpr.getEndPosition(), Id.VAL);
            Token                        tokTmp    = new Token(tokAsnExpr.getStartPosition(), tokAsnExpr.getEndPosition(), Id.IDENTIFIER, "#tmp");
            Token                        tokAsn    = new Token(tokAsnExpr.getStartPosition(), tokAsnExpr.getEndPosition(), Id.ASN);
            VariableTypeExpression       typeDecl  = new VariableTypeExpression(tokVal);
            VariableDeclarationStatement stmtDecl  = new VariableDeclarationStatement(typeDecl, tokTmp, false);
            AssignmentStatement          stmtTmp   = new AssignmentStatement(stmtDecl, tokAsn, exprRValue);
            AssignmentStatement          stmtAsn   = new AssignmentStatement(exprLValue, tokAsn, new NameExpression(tokTmp));
            Token                        tokRet    = new Token(exprLValue.getStartPosition(), exprLValue.getStartPosition(), Id.RETURN);
            ReturnStatement              stmtRet   = new ReturnStatement(tokRet, new NameExpression(tokTmp));
            StatementBlock               stmtBlock = new StatementBlock(List.of(stmtTmp, stmtAsn, stmtRet), exprLValue.getStartPosition(), exprRValue.getEndPosition());
            expr = new StatementExpression(stmtBlock);
            }
        return expr;
        }

    /**
     * Parse an interval or range expression.
     *
     * <p/><code><pre>
     * RangeExpression
     *     BitwiseExpression
     *     RangeExpression ".." BitwiseExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseRangeExpression(boolean fExtended)
        {
        Expression expr = parseBitwiseExpression(fExtended);
        while (true)
            {
            switch (peek().getId())
                {
                case I_RANGE_I:
                case E_RANGE_I:
                case I_RANGE_E:
                case E_RANGE_E:
                    expr = new RelOpExpression(expr, current(), parseBitwiseExpression(fExtended));
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse a bitwise shift expression.
     *
     * <p/><code><pre>
     * ShiftExpression
     *     AdditiveExpression
     *     ShiftExpression "<<"  AdditiveExpression
     *     ShiftExpression ">>"  AdditiveExpression
     *     ShiftExpression ">>>" AdditiveExpression
     *     ShiftExpression "&"   AdditiveExpression
     *     ShiftExpression "^"   AdditiveExpression
     *     ShiftExpression "|"   AdditiveExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseBitwiseExpression(boolean fExtended)
        {
        Expression expr = parseAdditiveExpression(fExtended);
// TODO CP special handling if expr is a type?
        while (true)
            {
            switch (peek().getId())
                {
                case SHL:
                case SHR:
                case USHR:
                case BIT_AND:
                case BIT_XOR:
                case BIT_OR:
                    expr = new RelOpExpression(expr, current(), parseAdditiveExpression(fExtended));
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse an addition or subtraction expression.
     *
     * <p/><code><pre>
     * AdditiveExpression
     *     MultiplicativeExpression
     *     AdditiveExpression "+" MultiplicativeExpression
     *     AdditiveExpression "-" MultiplicativeExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseAdditiveExpression(boolean fExtended)
        {
// TODO CP type handling? (if expr comes back as a type)
        Expression expr = parseMultiplicativeExpression(fExtended);
        while (peek(Id.ADD) || peek(Id.SUB))
            {
            expr = new RelOpExpression(expr, current(), parseMultiplicativeExpression(fExtended));
            }
        return expr;
        }

    /**
     * Parse a multiplication / division / modulo expression.
     *
     * <p/><code><pre>
     * MultiplicativeExpression
     *     ElvisExpression
     *     MultiplicativeExpression "*"  ElvisExpression
     *     MultiplicativeExpression "/"  ElvisExpression
     *     MultiplicativeExpression "%"  ElvisExpression
     *     MultiplicativeExpression "/%" ElvisExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseMultiplicativeExpression(boolean fExtended)
        {
        Expression expr = parseElvisExpression(fExtended);
        while (true)
            {
            switch (peek().getId())
                {
                case MUL:
                case DIV:
                case MOD:
                case DIVREM:
                    expr = new RelOpExpression(expr, current(), parseElvisExpression(false));
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse an "elvis" expression, which is of the form "a ?: b".
     *
     * <p/><code><pre>
     * ElvisExpression
     *     PrefixExpression
     *     PrefixExpression ?: ElvisExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parseElvisExpression(boolean fExtended)
        {
        Expression expr = parsePrefixExpression(fExtended);
        if (peek(Id.COND_ELSE))
            {
            expr = new ElvisExpression(expr, current(), parseElvisExpression(false));
            }
        return expr;
        }

    /**
     * Parse a prefix expression.
     *
     * <p/><code><pre>
     * PrefixExpression
     *     PostfixExpression
     *     "++" PrefixExpression
     *     "--" PrefixExpression
     *     "+" PrefixExpression
     *     "-" PrefixExpression
     *     "!" PrefixExpression
     *     "~" PrefixExpression
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parsePrefixExpression(boolean fExtended)
        {
        switch (peek().getId())
            {
            case ADD:
                return new UnaryPlusExpression(current(), parsePrefixExpression(false));

            case SUB:
                return new UnaryMinusExpression(current(), parsePrefixExpression(false));

            case NOT:
            case BIT_NOT:
                return new UnaryComplementExpression(current(), parsePrefixExpression(false));

            case INC:
            case DEC:
                return new SequentialAssignExpression(current(), parsePrefixExpression(false));

            default:
                return parsePostfixExpression(fExtended);
            }
        }

    /**
     * Parse a prefix expression.
     *
     * <p/><code><pre>
     * PostfixExpression
     *     PrimaryExpression
     *     PostfixExpression "++"
     *     PostfixExpression "--"
     *     PostfixExpression "(" Arguments-opt ")"
     *     PostfixExpression ArrayDims             # TODO REVIEW - is this correct? (does it imply that the expression is a type expression?)
     *     PostfixExpression ArrayIndexes
     *     PostfixExpression NoWhitespace "?"
     *     PostfixExpression "." "&"-opt DotNameFinish
     *     PostfixExpression ".new" NewFinish
     *     PostfixExpression ".as" "(" AnyTypeExpression ")"
     *     PostfixExpression ".is" "(" AnyTypeExpression ")"
     *
     * ArrayDims
     *     "[" DimIndicators-opt "]"
     *
     * DimIndicators
     *     DimIndicator
     *     DimIndicators "," DimIndicator
     *
     * DimIndicator
     *     "?"
     *
     * ArrayIndexes
     *     "[" ExpressionList "]"
     *
     * ExpressionList
     *     Expression
     *     ExpressionList "," Expression
     *
     * DotNameFinish
     *     Name TypeParameterTypeList-opt
     *     "default"
     *
     * NewFinish
     *     TypeExpression NewArguments AnonClassBody-opt
     *     ArgumentList
     *
     * NewArguments
     *     ArrayIndexes ArgumentList-opt
     *     ArgumentList
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parsePostfixExpression(boolean fExtended)
        {
        Expression expr = parsePrimaryExpression(fExtended);
        while (true)
            {
            switch (peek().getId())
                {
                case COND:
                    if (peek().hasLeadingWhitespace())
                        {
                        // the trailing "?" operator MUST NOT have whitespace before it, otherwise
                        // it indicates a ternary operator
                        return expr;
                        }
// TODO CP possible handling for type?
                    expr = new NotNullExpression(expr, current());
                    break;

// TODO CP case NOT: (non-auto-narrow type)

                case INC:
                case DEC:
                    expr = new SequentialAssignExpression(expr, current());
                    break;

                case DOT:
                    {
                    expect(Id.DOT);
                    switch (peek().getId())
                        {
                        case NEW:
                            {
                            expr = parseNewExpression(expr);
                            break;
                            }

                        case AS:
                        case IS:
                            {
                            Token          keyword  = current();
                            Token          tokOpen  = expect(Id.L_PAREN);
                            TypeExpression exprType = parseExtendedTypeExpression();
                            Token          tokClose = expect(Id.R_PAREN);
                            expr = keyword.getId() == Id.AS
                                    ? new AsExpression(expr, keyword, exprType, tokClose)
                                    : new IsExpression(expr, keyword, exprType, tokClose);
                            break;
                            }

                        case BIT_AND:
                        case DEFAULT:
                        case IDENTIFIER:
                            {
                            Token noDeRef = match(Id.BIT_AND);
                            Token name    = match(Id.DEFAULT);
                            if (name == null)
                                {
                                name = expect(Id.IDENTIFIER);
                                }
                            long                 lEndPos = name.getEndPosition();
                            List<TypeExpression> params  = null;

                            Token tokPeekLT = match(Id.COMP_LT);
                            if (tokPeekLT != null)
                                {
                                putBack(tokPeekLT);
                                try (SafeLookAhead attempt = new SafeLookAhead())
                                    {
                                    params = parseTypeParameterTypeList(true, true);
                                    if (attempt.isClean())
                                        {
                                        attempt.keepResults();
                                        lEndPos = prev().getEndPosition();
                                        }
                                    else
                                        {
                                        params = null;
                                        }
                                    }
                                catch (CompilerException ignore) {}
                                }

                            if (expr instanceof NamedTypeExpression)
                                {
                                expr = new NamedTypeExpression((NamedTypeExpression) expr,
                                        Collections.singletonList(name), params, lEndPos);
                                }
                            else
                                {
                                expr = new NameExpression(expr, noDeRef, name, params, lEndPos);
                                }
                            break;
                            }

                        default:
                            // unexpected token, so pretend we were looking for a name
                            expect(Id.IDENTIFIER);
                            skipToNextStatement();
                            return expr;
                        }
                    break;
                    }

                case L_PAREN:
                case ASYNC_PAREN:
                    // ArgumentList
                    expr = new InvocationExpression(expr, peek(Id.ASYNC_PAREN),
                            parseArgumentList(true, true, false), prev().getEndPosition());
                    break;

                case L_SQUARE:
                    {
                    // ArrayDims
                    // ArrayIndex
                    expect(Id.L_SQUARE);
                    if (match(Id.R_SQUARE) != null)
                        {
                        // "SomeClass[]"
                        TypeExpression type = new ArrayTypeExpression(expr.toTypeExpression(), 0,
                                prev().getEndPosition());
                        Token tokNext = peek();
                        if (tokNext.getId() == Id.COLON && !tokNext.hasLeadingWhitespace())
                            {
                            expect(Id.COLON);
                            expr = parseComplexLiteral(type);
                            }
                        else
                            {
                            expr = type;
                            }
                        }
                    else if (match(Id.COND) == null)
                        {
                        // "someArray[3]"
                        List<Expression> indexes = parseExpressionList();
                        expr = new ArrayAccessExpression(expr, indexes, expect(Id.R_SQUARE));
                        }
                    else
                        {
                        // "SomeClass[?,?]"
                        int cExplicitDims = 1;
                        while (match(Id.R_SQUARE) == null)
                            {
                            expect(Id.COMMA);
                            expect(Id.COND);
                            ++cExplicitDims;
                            }
                        TypeExpression type = new ArrayTypeExpression(expr.toTypeExpression(),
                                cExplicitDims, prev().getEndPosition());
                        Token tokNext = peek();
                        if (tokNext.getId() == Id.COLON && !tokNext.hasLeadingWhitespace())
                            {
                            expect(Id.COLON);
                            expr = parseComplexLiteral(type);
                            }
                        else
                            {
                            expr = type;
                            }
                        }
                    break;
                    }

                default:
                    return expr;
                }
            }
        }

    NewExpression parseNewExpression(Expression left)
        {
        Token            keyword = expect(Id.NEW);
        TypeExpression   type    = null;
        List<Expression> args    = null;
        int              dims    = -1;
        StatementBlock   body    = null;
        long             lEndPos = 0;

        if (peek(Id.L_PAREN))
            {
            // this could be a type expression inside parenthesis, or it could be an
            // argument list for a "virtual new"; assume it's a virtual new, and we'll back
            // up if we were wrong
            Mark mark = mark();
            args    = parseArgumentList(true, false, false);
            lEndPos = prev().getEndPosition();

            Token.Id idNext = peek().getId();
            if (args.size() == 1 && (idNext == Id.L_PAREN || idNext == Id.L_SQUARE))
                {
                // this wasn't supposed to be an argument list, it was supposed to be a type
                restore(mark);
                args = null;
                }
            }

        if (args == null)
            {
            type    = parseTypeExpression();
            lEndPos = type.getEndPosition();

            // we always need arguments, but if the arguments are an empty array indicator,
            // e.g. "new Int[]", then we've already eaten the arguments when we parsed the type
            // expression
            boolean fArray = type instanceof ArrayTypeExpression;
            if (fArray)
                {
                if (peek(Id.L_SQUARE))
                    {
                    args    = parseArgumentList(true, false, true);
                    dims    = args.size();
                    lEndPos = prev().getEndPosition();
                    }
                else
                    {
                    args = Collections.emptyList();
                    }

                // parenthesized arguments after the dims
                List<Expression> argsTrailing = parseArgumentList(false, false, false);
                if (argsTrailing != null)
                    {
                    if (!argsTrailing.isEmpty())
                        {
                        if (args.isEmpty())
                            {
                            args = argsTrailing;
                            }
                        else
                            {
                            args.addAll(argsTrailing);
                            }
                        }
                    lEndPos = prev().getEndPosition();
                    }
                }
            else
                {
                args    = parseArgumentList(true, false, false);
                lEndPos = prev().getEndPosition();
                }

            if (left == null && peek(Id.L_CURLY))
                {
                body    = parseTypeCompositionBody(keyword);
                lEndPos = prev().getEndPosition();
                }
            }

        return new NewExpression(left, keyword, type, args, dims, body, lEndPos);
        }

    /**
     * Parse a primary expression.
     *
     * <ul><li>
     * Note: A parenthesized Expression, a TupleLiteral, and a LambdaExpression share a parse path
     * </li><li>
     * Note: The use of QualifiedName instead of a simple Name here (which would be logical and even
     *       expected since PostfixExpression takes care of the ".Name.Name" etc. suffix parsing) is
     *       used to capture the case where the expression is a type expression containing type
     *       parameters, and which the opening '<' of the type parameters would be parsed by the
     *       RelationalExpression rule if we miss handling it here. Unfortunately, that means that the
     *       TypeParameterList is parsed speculatively if the '<' opening token is encountered after
     *       a name, because it could (might/will occasionally) still be a "less than sign" and not a
     *       parameterized type.
     * </li></ul>
     * <p/><code><pre>
     * PrimaryExpression
     *     "(" Expression ")"
     *     "new" TypeExpression ArgumentList AnonClassBody-opt
     *     "construct" QualifiedName
     *     "&"-opt QualifiedName TypeParameterTypeList-opt
     *     StatementExpression
     *     SwitchExpression
     *     LambdaExpression
     *     "_"
     *     Literal
     *
     * StatementExpression
     *     StatementBlock                  # a "block expression"
     *     "throw" TernaryExpression       # non-completing
     *     "T0D0" TodoFinish-opt           # non-completing
     *     "assert"                        # non-completing
     * </pre></code>
     *
     * @param fExtended  true to allow parsing of an extended type expression
     *
     * @return an expression
     */
    Expression parsePrimaryExpression(boolean fExtended)
        {
        switch (peek().getId())
            {
            case ANY:
                {
                IgnoredNameExpression exprIgnore = new IgnoredNameExpression(current());
                return peek(Id.LAMBDA)
                        ? new LambdaExpression(Collections.singletonList(exprIgnore),
                            expect(Id.LAMBDA), parseLambdaBody(), exprIgnore.getStartPosition())
                        : exprIgnore;
                }

            case NEW:
                return parseNewExpression(null);

            case THROW:
                return new ThrowExpression(expect(Id.THROW), parseTernaryExpression(), null);

            case ASSERT:
            case ASSERT_RND:
            case ASSERT_ARG:
            case ASSERT_BOUNDS:
            case ASSERT_TODO:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                {
                Token keyword = current();
                Expression expr = null;
                switch (peek().getId())
                    {
                    case AS:
                    case SEMICOLON:
                    case COMMA:
                    case COND:
                    case COLON:
                    case R_PAREN:
                    case R_SQUARE:
                    case R_CURLY:
                        break;

                    default:
                        expr = parseTernaryExpression();
                        break;
                    }

                Expression exprMsg = null;
                if (match(Id.AS) != null)
                    {
                    exprMsg = parseTernaryExpression();
                    }

                return new ThrowExpression(keyword, expr, exprMsg);
                }

            case TODO:
                return parseTodoExpression();

            case FUNCTION:
            case IMMUTABLE:
            case AT:
                return fExtended
                    ? parseExtendedTypeExpression()
                    : parseTypeExpression();

            case PUBLIC:
            case PROTECTED:
            case PRIVATE:
            case STRUCT:
            case CONST:
            case ENUM:
            case MODULE:
            case PACKAGE:
            case SERVICE:
            case CLASS:
                if (fExtended)
                    {
                    return parseExtendedTypeExpression();
                    }
                // fall through
            default:
            case BIT_AND:
            case CONSTRUCT:
            case IDENTIFIER:
                {
                // the preamble can contain a leading "&" and a leading "construct". the "construct"
                // ends up getting relocated from the left most position where it is parsed, to the
                // right-most position in the name itself, e.g. "construct A.B.C" becomes a name
                // expression of "A.B.C.construct". even more complex, it is possible to obtain a
                // reference to the constructor itself using the notation "&construct A.B.C", which
                // means that the no-de-reference indicator also gets moved to the right with the
                // "construct"
                Token   amp       = match(Id.BIT_AND);
                Token   construct = match(Id.CONSTRUCT);
                Token   name      = expect(Id.IDENTIFIER);
                boolean fNormal   = amp == null && construct == null;

                // test for single-param implicit lambda
                if (fNormal && peek(Id.LAMBDA))
                    {
                    return new LambdaExpression(Collections.singletonList(new NameExpression(name)),
                            expect(Id.LAMBDA), parseLambdaBody(), name.getStartPosition());
                    }

                // the no-de-ref goes with the construct if there is a construct, not with the name
                Token nameNDR      = construct == null ? amp : null;
                Token constructNDR = construct != null ? amp : null;

                // parse qualified name (which is necessary because we may have to tack on a
                // "construct" to the end)
                long           lEndPos = prev().getEndPosition();
                NameExpression left    = null;
                boolean        fQuit   = false;
                Token          dot;
                while (nameNDR == null && (dot = match(Id.DOT)) != null)
                    {
                    Token nameNext = match(Id.IDENTIFIER);
                    if (nameNext == null)
                        {
                        // it's not a ".name" construct, so the name expression ended BEFORE the dot
                        putBack(dot);
                        fQuit = true;
                        break;
                        }

                    left    = new NameExpression(left, nameNDR, name, null, lEndPos);
                    nameNDR = null;                     // only gets applied once
                    name    = nameNext;
                    lEndPos = name.getEndPosition();
                    }

                if (fQuit || !fNormal)
                    {
                    // have to build the expression for the trailing name
                    NameExpression expr = new NameExpression(left, nameNDR, name,
                            parseTypeParameterTypeList(false, true), lEndPos);

                    // construct is added to the end of the list if it was specified
                    if (construct != null)
                        {
                        expr = new NameExpression(expr, constructNDR, construct, null, lEndPos);
                        }

                    return expr;
                    }

                // test for a non-auto-narrowing modifier ("!")
                Token tokNoNarrow = !peek().hasLeadingWhitespace()
                        ? match(Id.NOT)
                        : null;

                // test to see if there is a type parameter list (implying the expression is a type,
                // or a name of a method specifying "redundant return types"
                List<TypeExpression> params = null;
                Token tokPeekLT = match(Id.COMP_LT);
                if (tokPeekLT != null)
                    {
                    putBack(tokPeekLT);
                    try (SafeLookAhead attempt = new SafeLookAhead())
                        {
                        params = parseTypeParameterTypeList(true, true);
                        if (attempt.isClean()
                                // "index<size>>1" is a comparison and a shift, not a type
                                && (peek().getId() != Id.COMP_GT || peek().hasLeadingWhitespace()))
                            {
                            attempt.keepResults();
                            lEndPos = prev().getEndPosition();
                            }
                        else
                            {
                            params = null;
                            }
                        }
                    catch (CompilerException ignore) {}
                    }

                // test to see if this is a tuple literal of the form "Tuple:(", or some other
                // type literal of the form "type:["
                if (peek(Id.COLON))
                    {
                    Token colon = expect(Id.COLON);
                    if (!colon.hasLeadingWhitespace())
                        {
                        switch (peek().getId())
                            {
                            case L_PAREN:
                                if (left != null || !"Tuple".equals(name.getValueText()))
                                    {
                                    break;
                                    }
                                // fall through
                            case L_SQUARE:
                            case DIV:
                            case DIR_CUR:
                            case DIR_PARENT:
                                return parseComplexLiteral(new NamedTypeExpression(null,
                                        toList(left, name), null, tokNoNarrow, params, lEndPos));
                            }
                        }

                    putBack(colon);
                    }

                // note to future self: the reason that we have NameExpression with <params>
                // (which seems almost self-evident to ALWAYS be a type and not a name) is because
                // we have the ability to do this: "Int32 i = lit.to<Int32>();" (redundant return)
                return tokNoNarrow == null
                        ? new NameExpression(left, null, name, params, lEndPos)
                        : new NamedTypeExpression(null, toList(left, name), null, tokNoNarrow, params, lEndPos);
                }

            case L_PAREN:
                {
                // this could be a tuple literal, a parenthesized expression, or a lambda
                // expression's parameter list
                Token tokLParen = expect(Id.L_PAREN);
                if (match(Id.R_PAREN) != null)
                    {
                    // zero-argument lambda
                    return new LambdaExpression(Collections.emptyList(), expect(Id.LAMBDA),
                            parseLambdaBody(), tokLParen.getStartPosition());
                    }

                Expression expr = parseExtendedExpression();
                switch (peek().getId())
                    {
                    case COMMA:
                        // expression list indicates tuple literal or lambda expression
                        // inferred-type parameter list; the remainder of the list needs to be
                        // parsed to see if it's followed by a lambda operator
                        List<Expression> exprs = new ArrayList<>();
                        exprs.add(expr);
                        while (match(Id.COMMA) != null)
                            {
                            exprs.add(parseExpression());
                            }
                        expect(Id.R_PAREN);

                        if (peek(Id.LAMBDA))
                            {
                            return new LambdaExpression(exprs, expect(Id.LAMBDA),
                                    parseLambdaBody(), tokLParen.getStartPosition());
                            }

                        // it's a Tuple literal
                        return new TupleExpression(null, exprs, tokLParen.getStartPosition(), prev().getEndPosition());

                    case R_PAREN:
                        // this is either a parenthesized expression or a single parameter for a
                        // lambda (it's not a tuple literal)
                        expect(Id.R_PAREN);
                        if (peek(Id.LAMBDA))
                            {
                            return new LambdaExpression(Collections.singletonList(expr),
                                    expect(Id.LAMBDA), parseLambdaBody(), tokLParen.getStartPosition());
                            }
                        else
                            {
                            // just a parenthesized expression
                            return new ParenthesizedExpression(expr,
                                    tokLParen.getStartPosition(), prev().getEndPosition());
                            }

                    case IDENTIFIER:
                    case ANY:
                        // it has to be a lambda, because we just parsed an expression (which must
                        // have been a parameter type) and now we have the parameter name
                        {
                        List<Parameter> params = new ArrayList<>();
                        params.add(new Parameter(expr.toTypeExpression(), expectNameOrAny()));
                        while (match(Id.COMMA) != null)
                            {
                            TypeExpression type  = parseTypeExpression();
                            Token          name  = expectNameOrAny();
                            params.add(new Parameter(type, name));
                            }
                        expect(Id.R_PAREN);

                        return new LambdaExpression(params, expect(Id.LAMBDA),
                                parseLambdaBody(), tokLParen.getStartPosition());
                        }

                    default:
                        expect(Id.R_PAREN);
                        skipToNextStatement();
                        return expr;
                    }
                }

            case L_CURLY:
                return new StatementExpression(parseStatementBlock());

            case SWITCH:
                return parseSwitchExpression();

            case L_SQUARE:
                return parseComplexLiteral(null);

            case LIT_BIT:
            case LIT_NIBBLE:
            case LIT_CHAR:
            case LIT_STRING:
            case LIT_BINSTR:
            case LIT_INT:
            case LIT_INTA:
            case LIT_INT8:
            case LIT_INT16:
            case LIT_INT32:
            case LIT_INT64:
            case LIT_INT128:
            case LIT_INTN:
            case LIT_UINTA:
            case LIT_UINT8:
            case LIT_UINT16:
            case LIT_UINT32:
            case LIT_UINT64:
            case LIT_UINT128:
            case LIT_UINTN:
            case LIT_DEC:
            case LIT_DECA:
            case LIT_DEC32:
            case LIT_DEC64:
            case LIT_DEC128:
            case LIT_DECN:
            case LIT_FLOAT:
            case LIT_FLOAT8E4:
            case LIT_FLOAT8E5:
            case LIT_BFLOAT16:
            case LIT_FLOAT16:
            case LIT_FLOAT32:
            case LIT_FLOAT64:
            case LIT_FLOAT128:
            case LIT_FLOATN:
            case LIT_DATE:
            case LIT_TIMEOFDAY:
            case LIT_TIME:
            case LIT_TIMEZONE:
            case LIT_DURATION:
            case LIT_VERSION:
                return new LiteralExpression(current());

            case TEMPLATE:
                {
                Token            token    = current();
                long             lStart   = token.getStartPosition();
                long             lEnd     = token.getEndPosition();
                Object[]         aoParts  = (Object[]) token.getValue();
                int              cParts   = aoParts.length;
                List<Expression> listExpr = new ArrayList<>(cParts);
                for (int i = 0; i < cParts; ++i)
                    {
                    Object o = aoParts[i];
                    if (o instanceof Token[])
                        {
                        Parser parser = new Parser(this, (Token[]) o);
                        listExpr.add(parser.parseExpression());
                        if (!parser.eof())
                            {
                            Token tokNext = parser.next();
                            log(Severity.ERROR, TEMPLATE_EXTRA, lStart, lEnd, tokNext.getValueText());
                            }
                        }
                    else
                        {
                        listExpr.add(new LiteralExpression((Token) o));
                        }
                    }
                return new TemplateExpression(listExpr, lStart, lEnd);
                }

            case BIN_FILE:
            case STR_FILE:
            case DIV:
            case DIR_CUR:
            case DIR_PARENT:
                {
                Token   tokStart  = peek();
                long    lStart    = tokStart.getStartPosition();
                boolean fBin      = tokStart.getId() == Id.BIN_FILE;
                boolean fStr      = tokStart.getId() == Id.STR_FILE;
                boolean fContents = fBin | fStr;
                if (fContents)
                    {
                    assert !tokStart.hasTrailingWhitespace();
                    next();
                    }

                Token   tokFile  = parsePath();
                String  sFile    = (String) tokFile.getValue();
                boolean fDir     = !fContents && sFile.endsWith("/");
                long    lEnd     = tokFile.getEndPosition();
                Object  resource = m_source.resolvePath(sFile);

                Token   tokData = null;
                boolean fErr    = false;
                if (fContents || resource != null)
                    {
                    if (fBin)
                        {
                        byte[] abData = null;
                        try
                            {
                            abData = m_source.includeBinary(sFile);
                            }
                        catch (IOException ignore) {}
                        if (abData == null)
                            {
                            abData = new byte[0];
                            fErr   = true;
                            }
                        tokData = new Token(lStart, lEnd, Id.LIT_BINSTR, abData);
                        }
                    else if (fStr)
                        {
                        String sData = null;
                        try
                            {
                            Source source = m_source.includeString(sFile);
                            sData = source == null ? null : source.toRawString();
                            }
                        catch (IOException ignore) {}
                        if (sData == null)
                            {
                            sData = "";
                            fErr  = true;
                            }
                        tokData = new Token(lStart, lEnd, Id.LIT_STRING, sData);
                        }
                    }
                else
                    {
                    return new LiteralExpression(tokFile);
                    }

                if (fErr)
                    {
                    log(Severity.ERROR, INVALID_PATH, lStart, lEnd, sFile);
                    throw new CompilerException("no such directory or file: " + sFile);
                    }

                return fContents
                        ? new LiteralExpression(tokData)
                        : new FileExpression(null, tokFile, resource);
                }
            }
        }

    private static List<Token> toList(NameExpression left, Token name)
        {
        if (left == null)
            {
            return Collections.singletonList(name);
            }

        List<Token> names = left.getNameTokens();
        names.add(name);
        return names;
        }

    /**
     * Starting with the current token, eat all tokens that are part of a potential file or
     * directory name, and return that file name as a "literal path" token.
     *
     * @return  the file or directory name as a literal path token
     */
    Token parsePath()
        {
        StringBuilder sb     = new StringBuilder();
        Token         tokDiv = current();
        long          lPos   = tokDiv.getStartPosition();
        while (true)
            {
            sb.append(tokDiv.getId().TEXT);

            // whitespace after the divider indicates the end of the path
            if (tokDiv.hasTrailingWhitespace())
                {
                return new Token(lPos, tokDiv.getEndPosition(), Id.LIT_PATH, sb.toString());
                }

            // DIV          -> DIR_CUR | DIR_PARENT | DOT | IDENTIFIER
            // DIR_CUR      -> DIR_CUR | DIR_PARENT | DOT | IDENTIFIER
            // DIR_PARENT   -> DIR_CUR | DIR_PARENT | DOT | IDENTIFIER
            // IDENTIFIER   -> DOT | DIV
            // DOT          -> IDENTIFIER
            // note: there are no keywords in a path, so they're all treated as identifiers
            switch (peek().getId())
                {
                case DOT:
                    Token tokDot = current();
                    sb.append('.');
                    if (tokDot.hasTrailingWhitespace())
                        {
                        log(Severity.ERROR, INVALID_PATH, lPos, tokDot.getEndPosition(), sb.toString());
                        throw new CompilerException("illegal include-file path: " + sb);
                        }
                    // fall through
                case ALLOW:       case AS:         case ASSERT:    case AVOID:
                case BREAK:       case CASE:       case CATCH:     case CLASS:
                case CONDITIONAL: case CONST:      case CONSTRUCT: case CONTINUE:
                case DEFAULT:     case DELEGATES:  case DESIRED:   case DO:
                case ELSE:        case EMBEDDED:   case ENUM:      case EXTENDS:
                case FINALLY:     case FOR:        case FUNCTION:  case IF:
                case IMMUTABLE:   case IMPLEMENTS: case IMPORT:    case INCORPORATES:
                case INJECT:      case INTERFACE:  case INTO:      case IS:
                case MIXIN:       case MODULE:     case NEW:       case OPTIONAL:
                case OUTER:       case PACKAGE:    case PREFER:    case PRIVATE:
                case PROTECTED:   case PUBLIC:     case REQUIRED:  case RETURN:
                case SERVICE:     case STATIC:     case STRUCT:    case SUPER:
                case SWITCH:      case THIS:       case THROW:     // no T0D0
                case TRY:         case TYPEDEF:    case USING:     case VAL:
                case VAR:         case VOID:       case WHILE:     case IDENTIFIER:
                    untilDiv: while (true)
                        {
                        Token tokName;
                        switch (peek().getId())
                            {
                            case ALLOW:       case AS:         case ASSERT:    case AVOID:
                            case BREAK:       case CASE:       case CATCH:     case CLASS:
                            case CONDITIONAL: case CONST:      case CONSTRUCT: case CONTINUE:
                            case DEFAULT:     case DELEGATES:  case DESIRED:   case DO:
                            case ELSE:        case EMBEDDED:   case ENUM:      case EXTENDS:
                            case FINALLY:     case FOR:        case FUNCTION:  case IF:
                            case IMMUTABLE:   case IMPLEMENTS: case IMPORT:    case INCORPORATES:
                            case INJECT:      case INTERFACE:  case INTO:      case IS:
                            case MIXIN:       case MODULE:     case NEW:       case OPTIONAL:
                            case OUTER:       case PACKAGE:    case PREFER:    case PRIVATE:
                            case PROTECTED:   case PUBLIC:     case REQUIRED:  case RETURN:
                            case SERVICE:     case STATIC:     case STRUCT:    case SUPER:
                            case SWITCH:      case THIS:       case THROW:     // no T0D0
                            case TRY:         case TYPEDEF:    case USING:     case VAL:
                            case VAR:         case VOID:       case WHILE:     case IDENTIFIER:
                                tokName = current();
                                break;

                            default:
                                tokName = expect(Id.IDENTIFIER);
                                break;
                            }

                        sb.append(tokName.getValue());
                        if (tokName.hasTrailingWhitespace())
                            {
                            return new Token(lPos, tokName.getEndPosition(), Id.LIT_PATH, sb.toString());
                            }

                        Token.Id id;
                        switch (id = peek().getId())
                            {
                            case DOT:
                            case SUB:
                                tokDot = current();
                                sb.append(id.TEXT);
                                if (tokDot.hasTrailingWhitespace())
                                    {
                                    log(Severity.ERROR, INVALID_PATH, lPos, tokDot.getEndPosition(), sb.toString());
                                    throw new CompilerException("illegal include-file path: " + sb);
                                    }
                                break;

                            case DIV:
                                tokDiv = current();
                                break untilDiv;

                            default:
                                return new Token(lPos, tokName.getEndPosition(), Id.LIT_PATH, sb.toString());
                            }
                        }
                    break;

                case DIR_CUR:
                case DIR_PARENT:
                    tokDiv = current();
                    break;

                default:
                    return new Token(lPos, tokDiv.getEndPosition(), Id.LIT_PATH, sb.toString());
                }
            }
        }

    /**
     * Parse a Lambda body and turn it into a StatementBlock if necessary.
     *
     * @return a StatementBlock representing the body of the lambda
     */
    StatementBlock parseLambdaBody()
        {
        Token firstToken = peek();
        if (firstToken.getId() == Id.L_CURLY)
            {
            return parseStatementBlock();
            }

        Token fakeReturn = new Token(firstToken.getStartPosition(), firstToken.getStartPosition(), Id.RETURN);
        ReturnStatement stmt = new ReturnStatement(fakeReturn, parseExpression());
        return new StatementBlock(Collections.singletonList(stmt), stmt.getStartPosition(), stmt.getEndPosition());
        }

    /**
     * Parse a "to-do" expression.
     *
     * <p/><code><pre>
     * "T0D0" TodoFinish-opt       (note: 'O' replaced with '0' to suppress IDE highlighting)
     *
     * TodoFinish
     *     InputCharacter-not-"(" InputCharacters LineTerminator
     *     NoWhitespace "(" Expression-opt ")"
     * </pre></code>
     *
     * @return a ThrowExpression for a T0D0
     */
    ThrowExpression parseTodoExpression()
        {
        Expression message  = null;
        Token      keyword  = expect(Id.TODO);
        Token      endToken = null;
        if (keyword.getValue() == null)
            {
            if (match(Id.L_PAREN) != null)
                {
                endToken = match(Id.R_PAREN);
                if (endToken == null)
                    {
                    message = parseExpression();
                    endToken = expect(Id.R_PAREN);
                    }
                }
            }
        else
            {
            // the text is held in the String value of the "T0D0" keyword's token
            message = new LiteralExpression(keyword);

            // unfortunately, we have to pretend that the "end of line" T0D0 is followed by a
            // semicolon
            putBack(new Token(keyword.getEndPosition(), keyword.getEndPosition(), Id.SEMICOLON));
            }

        return new ThrowExpression(keyword, null, message, endToken);
        }

    /**
     * Parses a "switch" expression.
     *
     * <p/><code><pre>
     * SwitchExpression
     *     switch "(" SwitchCondition-opt ")" "{" SwitchExpressionBlocks "}"
     *
     * SwitchExpressionBlocks
     *     SwitchExpressionBlock
     *     SwitchExpressionBlocks SwitchExpressionBlock
     *
     * SwitchExpressionBlock
     *     SwitchLabels ExpressionList ;
     *
     * SwitchLabels
     *     SwitchLabel
     *     SwitchLabels SwitchLabel
     *
     * SwitchLabel
     *     "case" CaseOptionList ":"
     *     "default" ":"
     *
     * # each "case" expression may be any of:
     * #    (a) the type of the corresponding expression (or tuple field value) in the SwitchCondition;
     * #    (b) a Range of that type; or
     * #    (c) the wild-card "_" (compiled as the "blackhole" constant)
     * # a CaseExpressionList of all wild-cards is semantically equivalent to the use of a "default"
     * # label, and would predictably conflict with the same if both were specified.
     * CaseOptionList:
     *     CaseOption
     *     CaseOptionList "," CaseOption
     *
     * CaseOption:
     *     "(" CaseExpressionList "," CaseExpression ")"
     *     SafeCaseExpression
     *
     * CaseExpressionList:
     *     CaseExpression
     *     CaseExpressionList "," CaseExpression
     *
     * CaseExpression:
     *     "_"
     *     Expression
     *
     * # parse for "case TernaryExpression:" because Expression parsing looks for a possible trailing ':'
     * SafeCaseExpression:
     *     "_"
     *     TernaryExpression
     * </pre></code>
     *
     * @return a SwitchExpression
     */
    SwitchExpression parseSwitchExpression()
        {
        Token keyword = expect(Id.SWITCH);
        expect(Id.L_PAREN);
        List<AstNode> cond = parseSwitchCondition();
        expect(Id.R_PAREN);

        List<AstNode> contents  = new ArrayList<>();
        boolean       fDefault  = false;
        boolean       fNeedExpr = false;
        expect(Id.L_CURLY);
        while (true)
            {
            switch (peek().getId())
                {
                case CASE:
                    contents.add(new CaseStatement(current(), parseCaseOptionList(), expect(Id.COLON)));
                    fNeedExpr = true;
                    break;

                case DEFAULT:
                    {
                    Token tokDefault = current();
                    if (fDefault)
                        {
                        log(Severity.ERROR, REPEAT_DEFAULT,
                                tokDefault.getStartPosition(), tokDefault.getEndPosition());
                        }
                    contents.add(new CaseStatement(tokDefault, null, expect(Id.COLON)));
                    fDefault  = true;
                    fNeedExpr = true;
                    }
                    break;

                default:
                    if (!fNeedExpr)
                        {
                        log(Severity.ERROR, MISSING_CASE, peek().getStartPosition(), peek().getEndPosition());
                        throw new CompilerException("switch must start with a case");
                        }

                    contents.add(parseExpression()); // TODO it's an ExpressionList, so while match(',') ...
                    expect(Id.SEMICOLON);
                    fNeedExpr = false;
                    break;

                case R_CURLY:
                    if (contents.isEmpty())
                        {
                        // there must be at least one case
                        // this should result in the appropriate error being logged
                        expect(Id.CASE);
                        }
                    else if (fNeedExpr)
                        {
                        // the last thing in "contents" is a CaseStatement; missing the expression!
                        // this should result in the appropriate error being logged
                        contents.add(parseExpression());
                        }

                    return new SwitchExpression(keyword, cond, contents, expect(Id.R_CURLY).getEndPosition());
                }
            }
        }

    /**
     * Parse a complex literal.
     *
     * <p/><code><pre>
     * TupleLiteral
     *     "(" ExpressionList "," Expression ")"                     # compile/runtime type is Tuple
     *     TypeExpression NoWhitespace ":" "(" ExpressionList-opt ")"# type must be a Tuple
     *
     * CollectionLiteral
     *     "[" ExpressionList-opt "]"                                # compile/runtime type is Array
     *     TypeExpression NoWhitespace ":" "[" ExpressionList-opt "]"# type must be Collection, Set,
     *                                                               # List, or Array
     * MapLiteral
     *     "[" Entries-opt "]"                                       # compile/runtime type is Map
     *     TypeExpression NoWhitespace ":" "[" Entries-opt "]"       # type must be Map
     *
     * Entries
     *     Entry
     *     Entries "," Entry
     *
     * Entry
     *     Expression "=" Expression
     * </pre></code>
     *
     * @return a literal expression
     */
    Expression parseComplexLiteral(TypeExpression type)
        {
        String sType;
        if (type == null)
            {
            sType = "";
            }
        else if (type instanceof ArrayTypeExpression)
            {
            sType = "Array";
            }
        else
            {
            sType = type.toString();
            int of = sType.indexOf('<');
            if (of >= 0)
                {
                sType = sType.substring(0, of);
                }
            }

        switch (sType)
            {
            case "":
                // this could be an array, or a range, or a map
                // (fall through)
            case "Collection":
            case "Set":
            case "List":
            case "Array":
                {
                Token tokOpen   = expect(Id.L_SQUARE);
                long  lStartPos = type == null ? tokOpen.getStartPosition() : type.getStartPosition();

                List<Expression> exprs = new ArrayList<>();
                while (match(Id.R_SQUARE) == null)
                    {
                    Expression expr = parseExpression();
                    exprs.add(expr);
                    if (match(Id.COMMA) == null)
                        {
                        if (sType.isEmpty() && exprs.size() == 1)
                            {
                            // special handling for a map
                            if (peek().getId() == Id.ASN)
                                {
                                List<Expression> keys   = new ArrayList<>();
                                List<Expression> values = new ArrayList<>();
                                while (match(Id.R_SQUARE) == null)
                                    {
                                    keys.add(keys.isEmpty() ? expr : parseExpression());
                                    expect(Id.ASN);
                                    values.add(parseExpression());
                                    if (match(Id.COMMA) == null)
                                        {
                                        expect(Id.R_SQUARE);
                                        break;
                                        }
                                    }

                                long           ofMap    = tokOpen.getStartPosition();
                                Token          tokName  = new Token(ofMap, ofMap, Id.IDENTIFIER, "Map");
                                TypeExpression exprType = new NamedTypeExpression(null,
                                        Collections.singletonList(tokName), null, null, null, ofMap);
                                return new MapExpression(exprType, keys, values, prev().getEndPosition());
                                }
                            }

                        expect(Id.R_SQUARE);
                        break;
                        }
                    }
                return new ListExpression(type, exprs, lStartPos, prev().getEndPosition());
                }

            case "Range":
            case "Interval":
                {
                // parseRangeExpression() logic
                Expression expr1     = parseBitwiseExpression(false);
                Token      tokDotDot = switch (peek().getId())
                    {
                    case E_RANGE_I,
                         I_RANGE_E,
                         E_RANGE_E -> current();
                    default        -> expect(Id.I_RANGE_I);
                    };
                Expression expr2     = parseBitwiseExpression(false);
                return new RelOpExpression(expr1, tokDotDot, expr2);
                }

            case "Map":
                {
                expect(Id.L_SQUARE);
                List<Expression> keys   = new ArrayList<>();
                List<Expression> values = new ArrayList<>();
                while (match(Id.R_SQUARE) == null)
                    {
                    keys.add(parseExpression());
                    expect(Id.ASN);
                    values.add(parseExpression());
                    if (match(Id.COMMA) == null)
                        {
                        expect(Id.R_SQUARE);
                        break;
                        }
                    }
                return new MapExpression(type, keys, values, prev().getEndPosition());
                }

            case "Tuple":
                {
                expect(Id.L_PAREN);

                List<Expression> exprs = null;
                while (match(Id.R_PAREN) == null)
                    {
                    if (exprs == null)
                        {
                        exprs = new ArrayList<>();
                        }
                    else
                        {
                        expect(Id.COMMA);
                        }
                    exprs.add(parseExpression());
                    }
                return new TupleExpression(type, exprs, type.getStartPosition(),
                        prev().getEndPosition());
                }

            case "Path":
                return new LiteralExpression(parsePath());

            case "File":
            case "Directory":
            case "FileStore":
                {
                Token   tokFile  = parsePath();
                String  sFile    = (String) tokFile.getValue();
                boolean fReqFile = "File".equals(sType);
                boolean fReqDir  = sFile.endsWith("/") || !fReqFile;
                long    lStart   = type.getStartPosition();
                long    lEnd     = tokFile.getEndPosition();
                Object  resource = m_source.resolvePath(sFile);

                if (resource == null                                        // path must exist
                        || fReqFile && fReqDir                              // can't be both
                        || fReqDir  && !(resource instanceof ResourceDir)   // dir is expected
                        || fReqFile && !(resource instanceof File))         // file is expected
                    {
                    log(Severity.ERROR, INVALID_PATH, lStart, lEnd, sFile);
                    if (resource == null)
                        {
                        throw new CompilerException("no such file: " + sFile);
                        }
                    }
                return new FileExpression(type, tokFile, resource);
                }

            default:
                {
                Expression expr = parseExpression();
                log(Severity.ERROR, BAD_CUSTOM, expr.getStartPosition(), expr.getEndPosition(), type, expr);
                return expr;
                }
            }
        }

    /**
     * Parse a type expression.
     *
     * <p/><code><pre>
     * TypeExpression
     *     IntersectingTypeExpression
     * </pre></code>
     *
     * @return a TypeExpression
     */
    public TypeExpression parseTypeExpression()
        {
        return parseIntersectingTypeExpression(false);
        }

    /**
     * Parse an extended type expression.
     *
     * <p/><code><pre>
     * ExtendedTypeExpression
     *     ExtendedIntersectingTypeExpression
     * </pre></code>
     *
     * @return a TypeExpression
     */
    public TypeExpression parseExtendedTypeExpression()
        {
        return parseIntersectingTypeExpression(true);
        }

    /**
     * Parse a type expression of the form "Type + Type" or "Type - Type".
     *
     * <p/><code><pre>
     * IntersectingTypeExpression
     *     UnionedTypeExpression
     *     IntersectingTypeExpression + UnionedTypeExpression
     *     IntersectingTypeExpression - UnionedTypeExpression
     *
     * ExtendedIntersectingTypeExpression
     *     ExtendedUnionedTypeExpression
     *     ExtendedIntersectingTypeExpression + ExtendedUnionedTypeExpression
     *     ExtendedIntersectingTypeExpression - ExtendedUnionedTypeExpression
     * </pre></code>
     *
     * @param fExtended  true to parse for the "extended" form instead of the "limited" form
     *
     * @return a type expression
     */
    TypeExpression parseIntersectingTypeExpression(boolean fExtended)
        {
        TypeExpression expr = parseUnionedTypeExpression(fExtended);
        Token tokOp;
        do
            {
            if ((tokOp = match(Id.ADD)) != null || (tokOp = match(Id.SUB)) != null)
                {
                expr = new BiTypeExpression(expr, tokOp, parseUnionedTypeExpression(fExtended));
                }
            }
        while (tokOp != null);
        return expr;
        }

    /**
     * Parse a type expression of the form "Type | Type", otherwise .
     *
     * <p/><code><pre>
     * UnionedTypeExpression
     *     NonBiTypeExpression
     *     UnionedTypeExpression | NonBiTypeExpression
     *
     * ExtendedUnionedTypeExpression
     *     ExtendedNonBiTypeExpression
     *     ExtendedUnionedTypeExpression | ExtendedNonBiTypeExpression
     * </pre></code>
     *
     * @param fExtended  true to parse for the "extended" form instead of the "limited" form
     *
     * @return a type expression
     */
    TypeExpression parseUnionedTypeExpression(boolean fExtended)
        {
        TypeExpression expr = parseNonBiTypeExpression(fExtended);
        while (peek(Id.BIT_OR))
            {
            expr = new BiTypeExpression(expr, expect(Id.BIT_OR), parseNonBiTypeExpression(fExtended));
            }
        return expr;
        }

    /**
     * Parse any type expression that does NOT look like "Type + Type" or "Type | Type".
     *
     * <p/><code><pre>
     * NonBiTypeExpression
     *     "(" TypeExpression ")"
     *     AnnotatedTypeExpression
     *     NamedTypeExpression
     *     FunctionTypeExpression
     *     NonBiTypeExpression NoWhitespace "?"
     *     NonBiTypeExpression ArrayDims
     *     NonBiTypeExpression ArrayIndexes            # ArrayIndexes is not consumed by this construction
     *     "immutable" NonBiTypeExpression
     *
     * ExtendedNonBiTypeExpression
     *     "(" ExtendedTypeExpression ")"
     *     ExtendedAnnotatedTypeExpression
     *     TypeAccessModifier-opt NamedTypeExpression
     *     FunctionTypeExpression
     *     ExtendedNonBiTypeExpression NoWhitespace "?"
     *     ExtendedNonBiTypeExpression ArrayDims
     *     ExtendedNonBiTypeExpression ArrayIndexes            # ArrayIndexes is not consumed by this construction
     *     "immutable" ExtendedNonBiTypeExpression-opt
     *     "const"
     *     "enum"
     *     "module"
     *     "package"
     *     "service"
     *     "class"
     *
     * NamedTypeExpression
     *     QualifiedName TypeParameterTypeList-opt
     *
     * ArrayDims
     *     "[" DimIndicators-opt "]"
     *
     * DimIndicators
     *     DimIndicator
     *     DimIndicators "," DimIndicator
     *
     * DimIndicator
     *     "?"
     *
     * ArrayIndexes
     *     "[" ExpressionList "]"
     *
     * ExpressionList
     *     Expression
     *     ExpressionList "," Expression
     * </pre></code>
     *
     * @param fExtended  true to parse for the "extended" form instead of the "limited" form
     *
     * @return a type expression
     */
    TypeExpression parseNonBiTypeExpression(boolean fExtended)
        {
        TypeExpression type;
        Token tokAccess = null;
        switch (peek().getId())
            {
            case L_PAREN:
                expect(Id.L_PAREN);
                type = parseExtendedTypeExpression();
                expect(Id.R_PAREN);
                break;

            case AT:
                type = parseAnnotatedTypeExpression(fExtended);
                break;

            case FUNCTION:
                type = parseFunctionTypeExpression();
                break;

            case IMMUTABLE:
                {
                Token tokImmut = current();

                // the trailing type is optional in the "extended" form of the type syntax
                if (fExtended)
                    {
                    switch (peek().getId())
                        {
                        case AS:
                        case R_PAREN:
                        case COMP_GT:
                        case COMMA:
                        case BIT_OR:
                        case ADD:
                        case SUB:
                            return new KeywordTypeExpression(tokImmut);
                        }
                    }

                type = new DecoratedTypeExpression(tokImmut, parseNonBiTypeExpression(fExtended));
                break;
                }

            case CONST:
            case ENUM:
            case MODULE:
            case PACKAGE:
            case SERVICE:
            case CLASS:
                {
                Token tokKeyword = current();
                if (!fExtended)
                    {
                    log(Severity.ERROR, EXT_TYPE_SYNTAX, tokKeyword.getStartPosition(),
                            tokKeyword.getEndPosition(), tokKeyword.getValueText());
                    }
                type = new KeywordTypeExpression(tokKeyword);
                return type;
                }

            case ANY:
                return new KeywordTypeExpression(current());

            case STRUCT:
            case PUBLIC:
            case PROTECTED:
            case PRIVATE:
                tokAccess = current();
                if (!fExtended)
                    {
                    log(Severity.ERROR, EXT_TYPE_SYNTAX, tokAccess.getStartPosition(),
                            tokAccess.getEndPosition(), tokAccess.getValueText());
                    }
                // fall through
            default:
                type = parseNamedTypeExpression(tokAccess);
                break;
            }

        while (true)
            {
            switch (peek().getId())
                {
                case L_SQUARE:
                    // this could be either:
                    //  -> NonBiTypeExpression ArrayDims
                    //  -> NonBiTypeExpression ArrayIndexes
                    // in the case of the ArrayIndexes, we do NOT consume that portion of the
                    // expression; we use it to give us a dimension count, as if it were ArrayDims
                    Mark mark = mark();

                    expect(Id.L_SQUARE);
                    int cDims    = 0;
                    int cIndexes = 0;
                    while (match(Id.R_SQUARE) == null)
                        {
                        if (cDims + cIndexes > 0)
                            {
                            expect(Id.COMMA);
                            }

                        Token dim = peek(); // just for error reporting
                        if (match(Id.COND) == null)
                            {
                            parseExpression();
                            if (cIndexes == 0 && cDims > 0)
                                {
                                // just log the first one that deviates
                                log(Severity.ERROR, ALL_OR_NO_DIMS, dim.getStartPosition(), dim.getEndPosition());
                                }
                            ++cIndexes;
                            }
                        else // we ate the "?"
                            {
                            if (cDims == 0 && cIndexes > 0)
                                {
                                // just log the first one that deviates
                                log(Severity.ERROR, ALL_OR_NO_DIMS, dim.getStartPosition(), dim.getEndPosition());
                                }
                            ++cDims;
                            }
                        }
                    long lEndPos = prev().getEndPosition();
                    type = new ArrayTypeExpression(type, cDims + cIndexes, lEndPos);

                    // if there were only indexes, then we need to leave them in place because the
                    // type expression does not consume them
                    if (cDims == 0 && cIndexes > 0)
                        {
                        restore(mark);
                        return type;
                        }
                    break;

                case COND:
                    if (!peek().hasLeadingWhitespace())
                        {
                        type = new NullableTypeExpression(type, expect(Id.COND).getEndPosition());
                        }
                    else
                        {
                        return type;
                        }
                    break;

                default:
                    return type;
                }
            }
        }

    /**
     * Parse a type expression that is preceded by an annotation.
     *
     * <p/><code><pre>
     * AnnotatedTypeExpression
     *     Annotation NonBiTypeExpression
     *
     * ExtendedAnnotatedTypeExpression
     *     Annotation ExtendedNonBiTypeExpression
     * </pre></code>
     *
     * @param fExtended  true to parse for the "extended" form instead of the "limited" form
     *
     * @return an AnnotatedTypeExpression
     */
    AnnotatedTypeExpression parseAnnotatedTypeExpression(boolean fExtended)
        {
        AnnotationExpression annotation = parseAnnotation(true);
        TypeExpression type = parseNonBiTypeExpression(fExtended);

        return new AnnotatedTypeExpression(annotation, type);
        }

    /**
     * Parse a function type expression.
     *
     * <p/><code><pre>
     * FunctionTypeExpression
     *     "function" ReturnList FunctionTypeFinish
     *
     * FunctionTypeFinish
     *     Name ParameterTypeList
     *     ParameterTypeList Name
     * </pre></code>
     *
     * @return a FunctionTypeExpression
     */
    FunctionTypeExpression parseFunctionTypeExpression()
        {
        Token function = expect(Id.FUNCTION);

        // return values
        Token           conditional = match(Id.CONDITIONAL);
        List<Parameter> listReturn  = parseReturnList();

        // see if the parameters precede the name
        List<TypeExpression> listParam = parseParameterTypeList(false);

        if (listParam == null)
            {
            // name optionally comes before or after the parameters
            Token name = expect(Id.IDENTIFIER);
            listParam = parseParameterTypeList(true);

            // pretend the name is the next token (as if we didn't eat it already)
            putBack(name);
            }

        return new FunctionTypeExpression(function, conditional, listReturn, listParam, prev().getEndPosition());
        }

    /**
     * Parse a type expression in the form:
     *
     *   "name.name.name<param, param>.name!<param, param>"
     *
     * <p/><code><pre>
     * NamedTypeExpression
     *     NamedTypeExpressionPart
     *     NamedTypeExpression '.' Annotations-opt NamedTypeExpressionPart
     *
     * NamedTypeExpressionPart
     *     QualifiedName NoAutoNarrowModifier-opt TypeParameterTypeList-opt
     *
     * NoAutoNarrowModifier
     *     NoWhitespace "!"
     * </pre></code>
     *
     * @param tokAccess  PUBLIC, PROTECTED, PRIVATE, STRUCT, or (usually) null
     *
     * @return a NamedTypeExpression
     */
    NamedTypeExpression parseNamedTypeExpression(Token tokAccess)
        {
        NamedTypeExpression expr = null;
        do
            {
            if (expr != null)
                {
                // TODO check for annotations
                }

            // QualifiedName
            List<Token> names = parseQualifiedName();

            if (tokAccess != null && expr != null)
                {
                log(Severity.ERROR, NO_CHILD_ACCESS, tokAccess.getStartPosition(),
                        tokAccess.getEndPosition(), tokAccess);
                }

            // NoAutoNarrowModifier
            Token tokNarrow = !peek().hasLeadingWhitespace()
                    ? match(Id.NOT)
                    : null;

            if (tokNarrow != null && expr != null)
                {
                log(Severity.ERROR, NONNARROW_CHILD, tokNarrow.getStartPosition(),
                        tokNarrow.getEndPosition());
                }

            // TypeParameterTypeList
            List<TypeExpression> params = parseTypeParameterTypeList(false, true);

            if (expr == null)
                {
                expr = new NamedTypeExpression(null, names, tokAccess, tokNarrow,
                        params, prev().getEndPosition());
                }
            else
                {
                expr = new NamedTypeExpression(expr, names, params, prev().getEndPosition());
                }
            }
        while (match(Id.DOT) != null);

        return expr;
        }

    /**
     * Parse a dot-delimited list of names.
     *
     * @param fRequired  pass true if the name is required; false if it is optional
     *
     * @return a list of zero or more identifier tokens
     */
    List<Token> parseQualifiedName(boolean fRequired)
        {
        if (!fRequired)
            {
            Token tokTest = match(Id.IDENTIFIER);
            if (tokTest == null)
                {
                return Collections.emptyList();
                }
            else
                {
                putBack(tokTest);
                }
            }

        return parseQualifiedName();
        }

    /**
     * Parse a dot-delimited list of names.
     *
     * <p/><code><pre>
     * QualifiedName
     *    Name
     *    QualifiedName "." Name
     * </pre></code>
     *
     * @return a list of zero or more identifier tokens
     */
    List<Token> parseQualifiedName()
        {
        List<Token> names = new ArrayList<>();
        do
            {
            names.add(expect(Id.IDENTIFIER));
            }
        while (match(Id.DOT) != null);
        return names;
        }

    /**
     * Parse a sequence of modifiers, including annotations
     *
     * <p/><code><pre>
     * Modifiers
     *     Modifier
     *     Modifiers Modifier
     *
     * Modifier
     *     "static"
     *     AccessModifier
     *     Annotation
     *
     * AccessModifier
     *     "public"
     *     "protected"
     *     "private"
     *
     * Annotation
     *     "@" NamedTypeExpression ArgumentList-opt
     * </pre></code>
     *
     * @return a List&lt;Token | Annotation&gt;
     */
    List[] parseModifiers()
        {
        return parseModifiers(false);
        }

    /**
     * Mostly a continuation of the above, but also supporting the following parsing:
     *
     * <p/><code><pre>
     * PropertyAccessModifier
     *     AccessModifier
     *     AccessModifier "/" AccessModifier
     * </pre></code>
     *
     * Also verifies that modifiers are not repeated or obviously conflicting.
     *
     * @param couldBeProperty
     *
     * @return a List&lt;Token | Annotation | '/'&gt; (in the case of a property, there could be
     *         something like "static, public, '/', private")
     */
    List[] parseModifiers(boolean couldBeProperty)
        {
        List<Token>                modifiers   = null;
        List<AnnotationExpression> annotations = null;
        boolean                    err         = false;
        Token                      access      = null;
        while (true)
            {
            switch (peek().getId())
                {
                case STATIC:
                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                    Token   modifier = current();
                    boolean isAccess = modifier.getId() != Id.STATIC;
                    if (modifiers == null)
                        {
                        modifiers = new ArrayList<>();
                        }
                    else if (!err && modifiers.contains(modifier))
                        {
                        err = true;
                        log(Severity.ERROR, REPEAT_MODIFIER, modifier.getStartPosition(),
                                modifier.getEndPosition(), modifier);
                        }
                    else if (!err && isAccess && access != null)
                        {
                        err = true;
                        log(Severity.ERROR, MODIFIER_CONFLICT, modifier.getStartPosition(),
                                modifier.getEndPosition(), access, modifier);
                        }
                    modifiers.add(modifier);
                    if (couldBeProperty && peek(Id.DIV))
                        {
                        modifiers.add(expect(Id.DIV));
                        Token second = match(Id.PUBLIC);
                        if (second == null)
                            {
                            second = match(Id.PROTECTED);
                            if (second == null)
                                {
                                second = expect(Id.PRIVATE);
                                }
                            else if (modifier.getId() == Id.PRIVATE)
                                {
                                // cannot be private/protected
                                log(Severity.ERROR, MODIFIER_CONFLICT, modifier.getStartPosition(),
                                        modifier.getEndPosition(), modifier, second);
                                }
                            }
                        else if (modifier.getId() != Id.PUBLIC)
                            {
                            // cannot be protected/public or private/public
                            log(Severity.ERROR, MODIFIER_CONFLICT, modifier.getStartPosition(),
                                    modifier.getEndPosition(), modifier, second);
                            }
                        modifiers.add(second);
                        }
                    if (isAccess)
                        {
                        access = modifier;
                        }
                    break;

                case AT:
                    if (annotations == null)
                        {
                        annotations = new ArrayList<>();
                        }
                    annotations.add(parseAnnotation(true));
                    break;

                default:
                    return (modifiers != null || annotations != null)
                            ? new List[] {modifiers, annotations}
                            : null;
                }
            }
        }

    /**
     * Parse an annotation.
     *
     * <p/><code><pre>
     * Annotation
     *     "@" NamedTypeExpression ArgumentList-opt
     * </pre></code>
     *
     * @param required  true iff the annotation is required
     *
     * @return an annotation, or null if no annotation was encountered
     */
    AnnotationExpression parseAnnotation(boolean required)
        {
        long lStartPos = peek().getStartPosition();
        if (match(Id.AT, required) == null)
            {
            return null;
            }

        // while the annotation is technically a named type expression, it only allows a qualified
        // name (and none of the other things that are normally allowed in a named type expression)
        NamedTypeExpression type = new NamedTypeExpression(null, parseQualifiedName(),
                null, null, null, prev().getEndPosition());

        // a trailing argument list is only assumed to be part of the annotation if there is
        // no whitespace separating the annotation from the arguments
        List<Expression> args = null;
        Token token = peek();
        if (token != null && token.getId() == Id.L_PAREN && !token.hasLeadingWhitespace())
            {
            args = parseArgumentList(true, false, false);
            }

        long lEndPos = args == null ? type.getEndPosition() : prev().getEndPosition();
        return new AnnotationExpression(type, args, lStartPos, lEndPos);
        }

    /**
     * If the next token is a &quot;&lt;&quot;, then parse a list of formal type parameters.
     *
     * <p/><code><pre>
     * TypeParameterList
     *     "<" TypeParameters ">"
     *
     * TypeParameters
     *     TypeParameter
     *     TypeParameters "," TypeParameter
     *
     * TypeParameter
     *     Name TypeParameterConstraint-opt
     *
     * TypeParameterConstraint
     *     "extends" Type
     * </pre></code>
     *
     * @param required  true iff the angle brackets are required
     *
     * @return a list of zero or more type parameters, or null if there were no angle brackets
     */
    List<Parameter> parseTypeParameterList(boolean required)
        {
        List<Parameter> typeParams = null;
        if (match(Id.COMP_LT, required) != null)
            {
            typeParams = new ArrayList<>();
            boolean first = true;
            while (match(Id.COMP_GT) == null)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                Token          param = expect(Id.IDENTIFIER);
                TypeExpression type  = null;
                if (match(Id.EXTENDS) != null)
                    {
                    type = parseExtendedTypeExpression();
                    }
                typeParams.add(new Parameter(type, param));
                }
            }
        return typeParams;
        }

    /**
     * For a parameterized type, parse the list of the types of its type parameters. For example,
     * for {@code Map<String, Int>}, this would parse the "{@code <String, Int>}" portion and
     * produce a list of two types: {@code String, Int}.
     *
     * <p/><code><pre>
     * TypeParameterTypeList
     *     "<" TypeParameterTypes ">"
     *
     * TypeParameterTypes
     *     TypeParameterType
     *     TypeParameterTypes "," TypeParameterType
     *
     * TypeParameterType
     *     TypeExpression
     * </pre></code>
     *
     * @param required  true iff the angle brackets are required
     *
     * @return a list of zero or more types, or null if there were no angle brackets
     */
    List<TypeExpression> parseTypeParameterTypeList(boolean required, boolean fAllowTypeSequence)
        {
        List<TypeExpression> types = null;
        if (match(Id.COMP_LT, required) != null)
            {
            if (match(Id.COMP_GT) != null)
                {
                types = Collections.emptyList();
                }
            else
                {
                types = parseTypeExpressionList(fAllowTypeSequence);
                expect(Id.COMP_GT);
                }
            }
        return types;
        }

    /**
     * Parse a list of type expressions.
     *
     * <p/><code><pre>
     * TypeExpressionList
     *     TypeExpression
     *     TypeExpressionList "," TypeExpression
     * </pre></code>
     *
     * @return a list of type expressions
     */
    List<TypeExpression> parseTypeExpressionList(boolean fAllowTypeSequence)
        {
        List<TypeExpression> types = new ArrayList<>();
        while (true)
            {
            if (!types.isEmpty() && match(Id.COMMA) == null)
                {
                return types;
                }

            if (fAllowTypeSequence && peek(Id.COMP_LT))
                {
                Token tokStart = peek();
                List<TypeExpression> listSeq = parseTypeParameterTypeList(true, false);
                Token tokEnd   = prev();
                types.add(new TupleTypeExpression(listSeq, tokStart.getStartPosition(), tokEnd.getEndPosition()));
                }
            else
                {
                types.add(parseExtendedTypeExpression());
                }
            }
        }


    /**
     * Parse a list of package injections, starting with the opening parenthesis.
     *
     * <p/><code><pre>
     * ResourceList
     *     "inject" "(" ResourceListContents-opt ")"
     *
     * ResourceListContents
     *     Resources ","-opt
     *
     * Resources
     *     Resource
     *     Resources "," Resource
     *
     * Resource
     *     TypeExpression ResourceFinish
     *
     * ResourceFinish
     *     Name
     *     "_"
     * </pre></code>
     *
     * @return a list of Parameter objects, or  null if no "inject" clause is encountered
     */
    List<Parameter> parseResourceList()
        {
        if (match(Id.INJECT) == null)
            {
            return null;
            }

        List<Parameter> resources = new ArrayList<>();
        expect(Id.L_PAREN);

        // check for an empty list of resources
        if (match(Id.R_PAREN) != null)
            {
            return resources;
            }

        do
            {
            TypeExpression type  = parseTypeExpression();
            Token          name  = expectNameOrAny();
            resources.add(new Parameter(type, name));
            }
        while (match(Id.R_PAREN, (match(Id.COMMA) == null)) == null);
        return resources;
        }

    /**
     * Parse a sequence of parameters, starting with the opening parenthesis.
     *
     * <p/><code><pre>
     * ParameterList
     *     "(" Parameters ")"
     *
     * Parameters
     *     Parameter
     *     Parameters "," Parameter
     *
     * Parameter
     *     Type Name DefaultValue-opt
     *
     * DefaultValue
     *     "=" Expression
     * </pre></code>
     *
     * @param required  true iff the parenthesis are required
     *
     * @return a list of Parameter objects, or null if no parenthesis were encountered
     */
    List<Parameter> parseParameterList(boolean required)
        {
        List<Parameter> params = null;
        if (match(Id.L_PAREN, required) != null)
            {
            params = new ArrayList<>();
            if (match(Id.R_PAREN) == null)
                {
                do
                    {
                    TypeExpression type  = parseTypeExpression();
                    Token          name  = expect(Id.IDENTIFIER);
                    Expression     value = null;
                    if (match(Id.ASN) != null)
                        {
                        value = parseExpression();
                        }
                    params.add(new Parameter(type, name, value));
                    }
                while (match(Id.R_PAREN, (match(Id.COMMA) == null)) == null);
                }
            }
        return params;
        }

    /**
     * Parse a list of parameter types (without parameter names).
     *
     * <p/><code><pre>
     * ParameterTypeList
     *     "(" TypeExpressionList-opt ")"
     * </pre></code>
     *
     * @param required
     * @return
     */
    List<TypeExpression> parseParameterTypeList(boolean required)
        {
        List<TypeExpression> types = null;
        if (match(Id.L_PAREN, required) != null)
            {
            types = peek(Id.R_PAREN)
                    ? Collections.emptyList()
                    : parseTypeExpressionList(false);
            expect(Id.R_PAREN);
            }
        return types;
        }

    /**
     * Parse an argument list.
     *
     * <p/><code><pre>
     * ArgumentList
     *     "(" Arguments-opt ")"
     *
     * Arguments
     *     Argument ","-opt
     *     Arguments "," Argument ","-opt
     *
     * Argument
     *     NamedArgument-opt ArgumentExpression
     *
     * # note: the "?" argument allows functions to specify arguments that they are NOT binding
     * ArgumentExpression
     *     "?"
     *     "<" TypeExpression ">" "?"
     *     Expression
     *
     * NamedArgument
     *     Name "="
     * </pre></code>
     *
     *
     * @param required        true iff the parenthesis are required
     * @param allowCurrying   true iff the "?" argument and its variations are allowed
     * @param allowArraySize  true iff the argument(s) can be inside '[' and ']'
     *
     * @return a list of arguments, or null if no parenthesis were encountered
     */
    List<Expression> parseArgumentList(boolean required, boolean allowCurrying, boolean allowArraySize)
        {
        Token.Id idClose;
        boolean  fArray = false;
        switch (peek().getId())
            {
            case ASYNC_PAREN:
                expect(Id.ASYNC_PAREN);
                idClose = Id.R_PAREN;
                break;

            case L_PAREN:
                expect(Id.L_PAREN);
                idClose = Id.R_PAREN;
                break;

            case L_SQUARE:
                if (!allowArraySize)
                    {
                    return null;
                    }

                expect(Id.L_SQUARE);
                idClose = Id.R_SQUARE;
                fArray  = true;
                break;

            default:
                if (required)
                    {
                    // this generates an error for the missing arguments list
                    expect(Id.L_PAREN);
                    }
                return null;
            }

        List<Expression> args = new ArrayList<>();
        if (match(idClose) == null)
            {
            do
                {
                Token label = null;
                if (!fArray)
                    {
                    // special case where the parameter names are being specified with the arguments
                    if (peek(Id.IDENTIFIER))
                        {
                        Token name = expect(Id.IDENTIFIER);
                        if (match(Id.ASN) == null)
                            {
                            // oops, it wasn't a "name=value" argument
                            putBack(name);
                            }
                        else
                            {
                            label = name;
                            }
                        }
                    }

                Expression expr;
                if (allowCurrying && !fArray)
                    {
                    switch (peek().getId())
                        {
                        case ANY:
                            {
                            Token tokUnbound = expect(Id.ANY);
                            expr = new NonBindingExpression(tokUnbound.getStartPosition(),
                                    tokUnbound.getEndPosition(), null);
                            }
                            break;

                        case COMP_LT:
                            {
                            Token          tokOpen    = expect(Id.COMP_LT);
                            TypeExpression type       = parseTypeExpression();
                            Token          tokClose   = expect(Id.COMP_GT);
                            Token          tokUnbound = expect(Id.ANY);
                            expr = new NonBindingExpression(tokOpen.getStartPosition(),
                                    tokUnbound.getEndPosition(), type);
                            }
                            break;

                        default:
                            expr = parseExpression();
                            break;
                        }
                    }
                else
                    {
                    expr = parseExpression();
                    }

                args.add(label == null ? expr : new LabeledExpression(label, expr));
                }
            while (match(idClose, (match(Id.COMMA) == null)) == null);
            }

        return args;
        }

    /**
     * Parse a declared list of return types.
     *
     * <p/><code><pre>
     * ReturnList
     *     "void"
     *     SingleReturnList
     *     "(" MultiReturnList ")"
     *
     * SingleReturnList
     *     TypeExpression
     *
     * MultiReturnList
     *     MultiReturn
     *     MultiReturnList "," MultiReturn
     *
     * MultiReturn
     *     TypeExpression Name-opt
     * </pre></code>
     */
    List<Parameter> parseReturnList()
        {
        List<Parameter> listReturn;
        if (match(Id.VOID) != null)
            {
            listReturn = Collections.emptyList();
            }
        else if (match(Id.L_PAREN) == null)
            {
            listReturn = Collections.singletonList(new Parameter(parseTypeExpression()));
            }
        else
            {
            listReturn = new ArrayList<>();
            do
                {
                listReturn.add(new Parameter(parseTypeExpression(), matchNameOrAny()));
                }
            while (match(Id.R_PAREN, (match(Id.COMMA) == null)) == null);
            }
        return listReturn;
        }

    /**
     * Parse a sequence of version override clauses.
     *
     * <p/><code><pre>
     * VersionRequirement
     *     Version VersionOverrides-opt
     *
     * VersionOverrides
     *     VersionOverride
     *     VersionOverrides "," VersionOverride
     *
     * VersionOverride
     *     VersionOverrideVerb Versions
     *
     * VersionOverrideVerb
     *     "allow"
     *     "avoid"
     *     "prefer"
     *
     * Versions
     *     Version
     *     Versions, Version
     * </pre></code>
     *
     * @param required  true if a version requirement must be present next in the stream of tokens
     *
     * @return a list of version overrides, or null if no version requirement is encountered
     */
    List<VersionOverride> parseVersionRequirement(boolean required)
        {
        // start with the initial version requirement (no preceding verb)
        LiteralExpression exprVer = parseVersionLiteral(required);
        if (exprVer == null)
            {
            return null;
            }

        List<VersionOverride> overrides = new ArrayList<>();
        overrides.add(new VersionOverride(exprVer));

        Set<Version> setGood = new HashSet<>();
        Set<Version> setBad  = new HashSet<>();

        // the initial version requirement is obviously "good"
        setGood.add(exprVer.getVersion());

        // this is a little more complicated parsing because the keywords are context sensitive
        // (so we need to use match() to grab them)
        Token verb;
        while ((verb = match(Id.ALLOW )) != null ||
               (verb = match(Id.AVOID )) != null ||
               (verb = match(Id.PREFER)) != null)
            {
            boolean first = true;
            while (first || match(Id.COMMA) != null)
                {
                exprVer = parseVersionLiteral(true);
                overrides.add(new VersionOverride(verb, exprVer));

                Version ver = exprVer.getVersion();
                if ((verb.getId() == Id.AVOID ? setGood : setBad).contains(ver))
                    {
                    log(Severity.ERROR, Compiler.CONFLICTING_VERSIONS, verb.getStartPosition(),
                            prev().getEndPosition(), ver.toString());
                    }
                (verb.getId() == Id.AVOID ? setBad : setGood).add(ver);

                first = false;
                }
            }

        return overrides;
        }

    /**
     * Parse a version literal.
     *
     * <p/><code><pre>
     * VersionString
     *     NonGASuffix
     *     VersionNumbers VersionFinish-opt
     *
     * VersionNumbers
     *     DigitsNoUnderscores
     *     VersionNumbers "." DigitsNoUnderscores
     *
     * VersionFinish:
     *      "." NonGASuffix
     *
     * NonGASuffix
     *       NonGAPrefix DigitsNoUnderscores-opt
     *
     * NonGAPrefix:
     *     "dev"           # developer build (default compiler stamp)
     *     "ci"            # continuous integration build (automated build, automated test)
     *     "qc"            # build selected for internal Quality Control
     *     "alpha"         # build selected for external alpha test (pre-release)
     *     "beta"          # build selected for external beta test (pre-release)
     *     "rc"            # build selected as a release candidate (pre-release; GA pending)
     * </pre></code>
     *
     * @param fRequired  true if a version requirement must be present next in the stream of tokens
     *
     * @return a VersionExpression
     */
    LiteralExpression parseVersionLiteral(boolean fRequired)
        {
        Token tokVer = match(Id.LIT_VERSION, fRequired);
        return tokVer == null ? null : new LiteralExpression(tokVer);
        }

    /**
     * Attempt to get out of whatever parsing mess we got ourselves into by
     * figuring out where the current statement ends, and starting anew from
     * there.
     */
    void skipToNextStatement()
        {
        while (true)
            {
            switch (peek().getId())
                {
                case SEMICOLON:
                    next();
                    return;

                case L_CURLY:
                case L_PAREN:
                case L_SQUARE:
                    skipEnclosed(current().getId());
                    break;

                case R_CURLY:
                    return;

                case ASSERT:
                case ASSERT_RND:
                case ASSERT_ARG:
                case ASSERT_BOUNDS:
                case ASSERT_TODO:
                case ASSERT_ONCE:
                case ASSERT_TEST:
                case ASSERT_DBG:
                case BREAK:
                case CLASS:
                case CONST:
                case CONSTRUCT:
                case CONTINUE:
                case DO:
                case ENUM:
                case IF:
                case IMPORT:
                case INTERFACE:
                case MIXIN:
                case MODULE:
                case PACKAGE:
                case RETURN:
                case SERVICE:
                case SWITCH:
                case THROW:
                case TODO:
                case TRY:
                case TYPEDEF:
                case USING:
                case WHILE:
                    return;

                default:
                    next();
                    break;
                }
            }
        }

    /**
     * Skip some enclosed amount of code between some likely-balanced set of
     * parenthesis / curlies / brackets.
     *
     * @param idOpen  the opening parenthesis / curlies / brackets
     */
    void skipEnclosed(Id idOpen)
        {
        while (true)
            {
            switch (peek().getId())
                {
                case L_CURLY:
                case L_PAREN:
                case L_SQUARE:
                    skipEnclosed(current().getId());
                    break;

                case R_CURLY:
                    if (idOpen == Id.L_CURLY)
                        {
                        next();
                        }
                    // whether or not we were looking for a right curly, it is
                    // likely to signify an end of something significant
                    return;

                case R_PAREN:
                    next();
                    if (idOpen == Id.L_PAREN)
                        {
                        return;
                        }
                    break;

                case R_SQUARE:
                    next();
                    if (idOpen == Id.L_SQUARE)
                        {
                        return;
                        }
                    break;

                default:
                    next();
                    break;
                }
            }
        }

    // ----- token stream handling ---------------------------------------------

    /**
     * Obtain the current token, but without advancing the token stream.
     *
     * @return the current token
     */
    protected Token peek()
        {
        Token token = m_tokenPutBack == null ? m_token : m_tokenPutBack;
        if (token == null)
            {
            // pretend there's one more closing curly brace
            m_token = token = new Token(m_source.getPosition(), m_source.getPosition(), Id.R_CURLY);
            }
        return token;
        }

    /**
     * Look for a token that matches the specified ID. Do not change the parsing position.
     *
     * @param id  the id of the token to match
     *
     * @return true iff the next token matches
     */
    protected boolean peek(Token.Id id)
        {
        return peek().getId() == id;
        }

    /**
     * Look for a token that is either an identifier or the "_" token. Do not change the parsing
     * position.
     *
     * @return true iff the next token is either an identifier or the "_" token
     */
    protected boolean peekNameOrAny()
        {
        Token.Id id = peek().getId();
        return id == Id.IDENTIFIER || id == Id.ANY;
        }

    /**
     * Obtain the current token, and then advance to the next token.
     *
     * @return the current token
     */
    protected Token current()
        {
        final Token token = peek();
        next();
        m_tokenPrev = token;
        return token;
        }

    /**
     * Advance to and obtain the next token.
     *
     * @return the next token (which is now the "current" token)
     */
    protected Token next()
        {
        if (m_tokenPutBack != null)
            {
            m_tokenPutBack = null;
            return m_token;
            }

        // weed out the comments (but store off the most recently encountered doc comment in case
        // anyone needs it later)
        while (m_lexer.hasNext())
            {
            m_token = m_lexer.next();
            switch (m_token.getId())
                {
                case ENC_COMMENT:
                    String sComment = (String) m_token.getValue();
                    if (sComment.startsWith("*"))
                        {
                        m_doc = m_token;
                        }
                    break;

                case EOL_COMMENT:
                    break;

                default:
                    return m_token;
                }
            }

        if (m_token != null && m_token.getStartPosition() != m_token.getEndPosition())
            {
            // pretend there's one more closing curly brace
            m_token = new Token(m_token.getEndPosition(), m_token.getEndPosition(), Id.R_CURLY);
            return m_token;
            }

        log(Severity.ERROR, UNEXPECTED_EOF, m_source.getPosition(), m_source.getPosition());
        throw new CompilerException("unexpected EOF");
        }

    /**
     * Rewind one token. This cannot be used to rewind more than one token; if more than one step
     * of look-ahead is required, use mark() and restore().
     *
     * @param token  the token to "put back", which should be the most recently obtained token
     */
    protected void putBack(Token token)
        {
        assert m_tokenPutBack == null;

        // undo context-sensitive keyword conversion
        token = token.desensitize();

        // undo peeling conversion
        Token tokenUnpeeled = token.anneal(m_token);
        if (tokenUnpeeled == null)
            {
            m_tokenPutBack = token;
            }
        else
            {
            m_token = tokenUnpeeled;
            }
        }

    protected static class Mark
        {
        long    pos;
        Token   token;
        Token   putBack;
        Token   lastMatch;
        Token   doc;
        boolean noRec;
        }

    protected Mark mark()
        {
        Mark mark = new Mark();
        mark.pos       = m_lexer.mark();
        mark.token     = m_token        == null ? null : m_token       .clone();
        mark.putBack   = m_tokenPutBack == null ? null : m_tokenPutBack.clone();
        mark.lastMatch = m_tokenPrev    == null ? null : m_tokenPrev   .clone();
        mark.doc       = m_doc;
        mark.noRec     = m_fAvoidRecovery;
        return mark;
        }

    protected void restore(Mark mark)
        {
        m_lexer.restore(mark.pos);
        m_token          = mark.token;
        m_tokenPutBack   = mark.putBack;
        m_tokenPrev      = mark.lastMatch;
        m_doc            = mark.doc;
        m_fAvoidRecovery = mark.noRec;
        }

    /**
     * Look for a token that matches the specified ID. The behavior of what to do if there is no
     * match is based on the "required" flag.
     *
     * @param id        the id of the token to match
     * @param required  true if the token is required
     *
     * @return the next token if it matches, or null iff required is false and the token does not
     *         match
     */
    protected Token match(Token.Id id, boolean required)
        {
        return required ? expect(id) : match(id);
        }

    /**
     * If the current token in the token stream matches the specified identity,
     * then return the current token and advance to the next; if the current
     * token does not match, then do not advance and return null.
     *
     * @param id  the identity of the token being looked for
     *
     * @return a token with the specified id, or null if the id did not match
     */
    protected Token match(Token.Id id)
        {
        Token token = peek();
        if (token.getId() == id)
            {
            return current();
            }

        if (token.getId() == Id.IDENTIFIER && id.ContextSensitive && token.getValue().equals(id.TEXT))
            {
            // advance to the next token
            next();

            // return the previously "current" token
            m_tokenPrev = token = token.convertToKeyword();
            return token;
            }

        token = token.peel(id, m_source);
        if (token != null)
            {
            m_tokenPrev = token;
            }
        return token;
        }

    /**
     * Helper to match either a VAR or VAL context-sensitive token.
     *
     * @return a token that is either VAR or VAL, or null if neither is the next token
     */
    protected Token matchVarOrVal()
        {
        Token token = match(Id.VAR);
        if (token != null)
            {
            return token;
            }
        return match(Id.VAL);
        }

    /**
     * Look for a token that is either an identifier or the "_" token.
     *
     * @return a token that is either an identifier or the "_" token, or null if neither is the next
     *         token
     */
    protected Token matchNameOrAny()
        {
        Token token = match(Id.ANY);
        if (token == null)
            {
            token = match(Id.IDENTIFIER);
            }
        return token;
        }

    /**
     * Return the most recently matched token.
     *
     * @return the token most recently returned from the match method
     */
    protected Token prev()
        {
        return m_tokenPrev;
        }

    /**
     * If the current token in the token stream matches the specified identity,
     * then return the current token and advance to the next; otherwise log a
     * "token expected" error.
     *
     * @param id  the identity of the token being looked for
     *
     * @return a token with the specified id
     */
    protected Token expect(Token.Id id)
        {
        final Token token = match(id);
        if (token != null)
            {
            return token;
            }

        long lPos = m_tokenPrev == null ? m_token.getEndPosition() : m_tokenPrev.getEndPosition();
        log(Severity.ERROR, EXPECTED_TOKEN, lPos, lPos, id, m_token.getId());

        throw new CompilerException("expected token: " + id + " (found: " + m_token.getId() + ")");
        }

    /**
     * Require a token that is either an identifier or the "_" token.
     *
     * @return a token that is either an identifier or the "_" token
     */
    protected Token expectNameOrAny()
        {
        Token token = match(Id.ANY);
        if (token == null)
            {
            token = expect(Id.IDENTIFIER);
            }
        return token;
        }

    /**
     * @return true if the token stream is exhausted
     */
    protected boolean eof()
        {
        return !(m_lexer.hasNext() || notEof(m_token) || notEof(m_tokenPutBack));
        }

    private boolean notEof(Token token)
        {
        return token != null
            && !(token.getStartPosition() == token.getEndPosition() && token.getId() == Id.R_CURLY);
        }

    /**
     * Take whatever the most recent documentation comment is.
     *
     * @return a token containing the most recently encountered documentation, or null
     */
    protected Token takeDoc()
        {
        Token doc = m_doc;
        m_doc = null;
        return doc;
        }

    /**
     * Log an error.
     *
     * @param severity
     * @param sCode
     * @param lPosStart
     * @param lPosEnd
     * @param aoParam
     */
    protected void log(Severity severity, String sCode, long lPosStart, long lPosEnd, Object... aoParam)
        {
        if (m_lookAhead != null)
            {
            m_lookAhead.log(severity, sCode, aoParam, lPosStart, lPosEnd);
            }
        else if (m_errorListener.log(severity, sCode, aoParam, m_source, lPosStart, lPosEnd))
            {
            m_fAvoidRecovery = true;
            throw new CompilerException("error list is full: " + m_errorListener);
            }
        }

    public class SafeLookAhead
            implements AutoCloseable
        {
        public SafeLookAhead()
            {
            m_oldLookAhead = m_lookAhead;
            m_lookAhead    = this;
            m_mark         = mark();
            }

        public void log(Severity severity, String sCode, Object[] aoParam, long lPosStart, long lPosEnd)
            {
            if (severity.ordinal() >= Severity.ERROR.ordinal())
                {
                m_err = new ErrorList.ErrorInfo(severity, sCode, aoParam, m_source, lPosStart, lPosEnd);
                m_fKeepResults = false;
                throw new CompilerException("err=" + m_err);
                }
            }

        public boolean isClean()
            {
            return m_err == null;
            }

        public void keepResults()
            {
            m_fKeepResults = true;
            }

        @Override
        public void close()
            {
            assert m_lookAhead == this;
            m_lookAhead = m_oldLookAhead;

            if (m_fKeepResults)
                {
                assert m_err == null;
                }
            else
                {
                restore(m_mark);
                }
            }

        SafeLookAhead       m_oldLookAhead;
        Mark                m_mark;
        ErrorList.ErrorInfo m_err;
        boolean             m_fKeepResults;
        }

    /**
     * @return true iff it's ok to try to recover from a parsing error
     */
    protected boolean recoverable()
        {
        return !eof() && !m_fAvoidRecovery && m_lookAhead == null;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        Source source = m_source;
        sb.append(source.getSimpleFileName());

        Token token = peek();
        if (token == null)
            {
            sb.append(' ')
              .append(source.getLine()+1)
              .append(':')
              .append(source.getOffset()+1);
            }
        else
            {
            sb.append(' ')
              .append(Source.calculateLine(token.getStartPosition())+1)
              .append(':')
              .append(Source.calculateOffset(token.getStartPosition())+1)
              .append(' ')
              .append(token);
            }

        return sb.toString();
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Unknown fatal error.
     */
    public static final String FATAL_ERROR       = "PARSER-01";
    /**
     * Unexpected End-Of-File (token exhaustion).
     */
    public static final String UNEXPECTED_EOF    = "PARSER-02";
    /**
     * Expected a particular token.
     */
    public static final String EXPECTED_TOKEN    = "PARSER-03";
    /**
     * Bad version value.
     */
    public static final String BAD_VERSION       = "PARSER-04";
    /**
     * Bad hex value.
     */
    public static final String BAD_HEX_LITERAL   = "PARSER-05";
    /**
     * Unsupported custom literal type: {0}.
     */
    public static final String BAD_CUSTOM        = "PARSER-06";
    /**
     * Cannot have module or package in a method.
     */
    public static final String NO_TOP_LEVEL      = "PARSER-07";
    /**
     * Multiple assignment list required.
     */
    public static final String NOT_MULTI_ASN     = "PARSER-08";
    /**
     * Empty statement is illegal.
     */
    public static final String NO_EMPTY_STMT     = "PARSER-09";
    /**
     * Child type cannot be specified as non-narrowing.
     */
    public static final String NONNARROW_CHILD   = "PARSER-10";
    /**
     * Child type cannot have an access specifier (\"{0}\").
     */
    public static final String NO_CHILD_ACCESS   = "PARSER-11";
    /**
     * Case statement required first in a switch.
     */
    public static final String MISSING_CASE      = "PARSER-12";
    /**
     * All array dimensions need to be blank or '?', or all need to be expressions; no mixing and
     * matching.
     */
    public static final String ALL_OR_NO_DIMS    = "PARSER-15";
    /**
     * Expected an End-Of-File (nothing else allowed to be here).
     */
    public static final String EXPECTED_EOF      = "PARSER-16";
    /**
     * Expected to find a type declaration.
     */
    public static final String NO_TYPE_FOUND     = "PARSER-17";
    /**
     * Statements not allowed outside of module declaration.
     */
    public static final String MODULE_NOT_ROOT   = "PARSER-18";
    /**
     * Modifier (like "static" or "public") is repeated.
     */
    public static final String REPEAT_MODIFIER   = "PARSER-19";
    /**
     * Modifiers conflict (like "private" and "public").
     */
    public static final String MODIFIER_CONFLICT = "PARSER-20";
    /**
     * Default switch branch is repeated.
     */
    public static final String REPEAT_DEFAULT    = "PARSER-21";
    /**
     * Expression cannot be assigned to.
     */
    public static final String NOT_ASSIGNABLE    = "PARSER-22";
    /**
     * Unexpected token following expression in template: {0}.
     */
    public static final String TEMPLATE_EXTRA    = "PARSER-23";
    /**
     * Invalid path: {0}.
     */
    public static final String INVALID_PATH      = "PARSER-24";
    /**
     * Incompatible chained equality operators: Cannot mix \"==\" and \"!=\".
     */
    public static final String BAD_CHAINED_EQ    = "PARSER-25";
    /**
     * Incompatible chained ordering operators: Cannot mix \"<\"/\"<=\" and \">\"/\">=\".
     */
    public static final String BAD_CHAINED_CMP   = "PARSER-26";
    /**
     * Unexpected keyword {0} indicates an extended type; use parenthesis around the type to enable
     * the extended type syntax.
     */
    public static final String EXT_TYPE_SYNTAX   = "PARSER-27";


    // ----- data members --------------------------------------------------------------------------

    /**
     * The Source to parse.
     */
    private final Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private ErrorListener m_errorListener;

    /**
     * The lexical analyzer.
     */
    private final Lexer m_lexer;

    /**
     * The "put back" token.
     */
    private Token m_tokenPutBack;

    /**
     * The previous token (often the most recently matched token).
     */
    private Token m_tokenPrev;

    /**
     * The current token.
     */
    private Token m_token;

    /**
     * The most recent doc comment.
     */
    private Token m_doc;

    /**
     * The top-most (outermost) type declaration of the compilation unit, such
     * as a module.
     */
    private StatementBlock m_root;

    /**
     * True once parsing has occurred.
     */
    private boolean m_fDone;

    /**
     * Disable parsing recovery.
     */
    private boolean m_fAvoidRecovery;

    /**
     * Object supporting unpredictable amount of look-ahead.
     */
    private SafeLookAhead m_lookAhead;
    }