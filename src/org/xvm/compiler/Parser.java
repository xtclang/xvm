package org.xvm.compiler;


import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.*;

import org.xvm.util.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.xvm.util.Handy.parseDelimitedString;


/**
 * A recursive descent parser for Ecstasy source code.
 *
 * @author cp 2016.11.03
 */
public class Parser
    {
    // ----- constructors ------------------------------------------------------

    /**
     * Construct an XTC lexical analyzer.
     *
     * @param source   the source to parse
     * @param listener the error listener
     */
    public Parser(Source source, ErrorListener listener)
        {
        if (source == null)
            {
            throw new IllegalArgumentException("Source required");
            }
        if (listener == null)
            {
            throw new IllegalArgumentException("ErrorListener required");
            }

        m_source        = source;
        m_errorListener = listener;
        m_lexer         = new Lexer(source, listener);
        }


    // ----- parsing -----------------------------------------------------------

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
    public Statement parseSource()
        {
        // parsing can only occur once
        if (!m_fDone)
            {
            // set the completion flag at this point (in case an exception occurs
            // during parsing
            m_fDone = true;

            // prime the token stream
            next();

            try
                {
                List<Statement> list = parseAliasStatements();

                Statement toptype = parseTypeDeclarationStatement();
                if (list == null)
                    {
                    m_root = toptype;
                    }
                else
                    {
                    list.add(toptype);
                    m_root = new BlockStatement(list);
                    }
                }
            catch (UnsupportedOperationException e)
                {
                // temporary exception handling while compiler is being built
                throw new CompilerException(e);
                }
            }

        return m_root;
        }

    /**
     * At the file level, it is possible to encounter any combination of import
     * and typedef statements.
     *
     * <p/><code><pre>
     * AliasStatements
     *     AliasStatement
     *     AliasStatements AliasStatement
     *
     * AliasStatement
     *     ImportStatement
     *     TypeDefStatement
     * </pre></code>
     *
     * @return a list of statements, or null
     */
    List<Statement> parseAliasStatements()
        {
        List<Statement> list = null;
        try
            {
            while (true)
                {
                if (peek().getId() == Id.IMPORT)
                    {
                    if (list == null)
                        {
                        list = new ArrayList<>();
                        }
                    list.add(parseImportStatement());
                    }
                else if (peek().getId() == Id.TYPEDEF)
                    {
                    if (list == null)
                        {
                        list = new ArrayList<>();
                        }
                    list.add(parseTypeDefStatement());
                    }
                else
                    {
                    return list;
                    }
                }
            }
        catch (CompilerException e)
            {
            if (recoverable())
                {
                skipToNextStatement();
                }
            return list;
            }
        }

    /**
     * Parse an import statement.
     *
     * <p/><code><pre>
     * ImportStatement
     *     "import" QualifiedName ImportAlias-opt ";"
     *
     * ImportAlias
     *     "as" Name
     * </pre></code>
     *
     * @return an ImportStatement
     */
    ImportStatement parseImportStatement()
        {
        List<Token> qualifiedName = new ArrayList<>();
        Token       simpleName    = null;

        expect(Id.IMPORT);

        // parse qualified name
        boolean first = true;
        while (first || (match(Id.DOT) != null))
            {
            simpleName = expect(Id.IDENTIFIER);
            qualifiedName.add(simpleName);
            first = false;
            }

        // optional alias override
        if (match(Id.AS) != null)
            {
            // parse simple name
            simpleName = expect(Id.IDENTIFIER);
            }

        expect(Id.SEMICOLON);

        return new ImportStatement(simpleName, qualifiedName);
        }

    /**
     * Parse a typedef statement.
     *
     * <p/><code><pre>
     * TypeDefStatement
     *     "typedef" Type Name ";"
     * </pre></code>
     *
     * @return a TypedefStatement
     */
    TypedefStatement parseTypeDefStatement()
        {
        expect(Id.TYPEDEF);

        TypeExpression type = parseTypeExpression();

        Token simpleName = type instanceof FunctionTypeExpression
                ? ((FunctionTypeExpression) type).name
                : expect(Id.IDENTIFIER);

        expect(Id.SEMICOLON);

        return new TypedefStatement(simpleName, type);
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
     *     "trait"
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
     *
     * TypeCompositionBody
     *     "{" EnumList-opt TypeCompositionComponents-opt "}"
     *     ";"
     * </pre></code>
     *
     * @return a TypeDeclaration
     */
    TypeDeclarationStatement parseTypeDeclarationStatement()
        {
        Token doc = takeDoc();

        // modifiers (including annotations)
        List<Token> modifiers = null;
        List<Annotation> annotations = null;
        List[] twolists = parseModifiers();
        if (twolists != null)
            {
            // note to self: this language needs multiple return values 
            modifiers = twolists[0];
            annotations = twolists[1];
            }

        return parseTypeDeclarationStatementAfterModifiers(doc, modifiers, annotations);
        }

    /**
     * (This is just a continuation of the above method.)
     */
    TypeDeclarationStatement parseTypeDeclarationStatementAfterModifiers
            (Token doc, List<Token> modifiers, List<Annotation> annotations)
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
            case TRAIT:
            case MIXIN:
                category = current();
                name     = expect(Id.IDENTIFIER);
                break;

            default:
                category  = expect(Id.MODULE);
                qualified = parseQualifiedName();
                name      = qualified.get(0);
            }

        // optional type parameters
        List<Parameter> typeParams = parseTypeParameterList(false);

        // optional constructor definition
        List<Parameter> constructorParams = parseParameterList(false);

        // sequence of compositions
        List<Composition> composition = new ArrayList<>();
        ParsingComposition: while (true)
            {
            if (match(Id.EXTENDS) != null)
                {
                TypeExpression type = parseTypeExpression();
                List<Expression> args = parseArgumentList(false);
                composition.add(new Composition.Extends(type, args));
                }
            else if (match(Id.IMPLEMENTS) != null)
                {
                do
                    {
                    composition.add(new Composition.Implements(parseTypeExpression()));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.DELEGATES) != null)
                {
                do
                    {
                    TypeExpression type = parseTypeExpression();
                    expect(Id.L_PAREN);
                    Expression delegate = parseExpression();
                    expect(Id.R_PAREN);
                    composition.add(new Composition.Delegates(type, delegate));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.INCORPORATES) != null)
                {
                do
                    {
                    TypeExpression type = parseTypeExpression();
                    List<Expression> args = parseArgumentList(false);
                    composition.add(new Composition.Incorporates(type, args));
                    }
                while (match(Id.COMMA) != null);
                }
            else if (match(Id.INTO) != null)
                {
                composition.add(new Composition.Into(parseTypeExpression()));
                }
            else if (match(Id.IMPORT) != null)
                {
                List<Token>           qualifiedModule = parseQualifiedName();
                NamedTypeExpression   module          = new NamedTypeExpression(null, qualifiedModule, null);
                List<VersionOverride> versionSpecs    = parseVersionRequirement(false);
                composition.add(new Composition.Import(module, versionSpecs));
                }
            else
                {
                break;
                }
            }

        // TypeCompositionBody
        BlockStatement body = null;
        if (peek().getId() == Id.L_CURLY)
            {
            body = parseTypeCompositionBody(category);
            }
        else
            {
            expect(Id.SEMICOLON);
            }

        return new TypeDeclarationStatement(modifiers, annotations, category, name,
                qualified, typeParams, constructorParams, composition, body, doc);
        }

    /**
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
    BlockStatement parseTypeCompositionBody(Token category)
        {
        List<Statement> stmts = new ArrayList<>();
        expect(Id.L_CURLY);
        if (category.getId() == Id.ENUM)
            {
            do
                {
                Token doc = takeDoc();

                // annotations
                List<Annotation> annotations = null;
                while (true)
                    {
                    Annotation annotation = parseAnnotation(false);
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
                List<TypeExpression> typeParams = parseTypeParameterTypeList(false);

                // argument list
                List<Expression> args = parseArgumentList(false);

                BlockStatement body = null;
                if (match(Id.L_CURLY) != null)
                    {
                    body = parseTypeCompositionComponents(new ArrayList<>());
                    }

                stmts.add(new EnumDeclaration(annotations, name, typeParams, args, body, doc));
                }
            while (match(Id.COMMA) != null);

            if (peek().getId() != Id.R_CURLY)
                {
                expect(Id.SEMICOLON);
                }
            }

        return parseTypeCompositionComponents(stmts);
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
     *     TypdefStatement
     *     ImportStatement
     *     TypeComposition
     *     PropertyDeclaration
     *     MethodDeclaration
     *     ConstantDeclaration
     * </pre></code>
     *
     * @return a BlockStatement
     */
    BlockStatement parseTypeCompositionComponents(List<Statement> stmts)
        {
        while (match(Id.R_CURLY) == null)
            {
            Statement stmt;
            switch (peek().getId())
                {
                case IMPORT:
                    stmt = parseImportStatement();
                    break;

                case TYPEDEF:
                    stmt = parseTypeDefStatement();
                    break;

                default:
                    {
                    Token doc = takeDoc();

                    // constant starts with "static" (a modifier)
                    // method starts with annotations/modifiers
                    // property starts with annotations/modifiers
                    // typecomp starts with annotations/modifiers
                    List<Token>      modifiers   = null;
                    List<Annotation> annotations = null;
                    List[] twolists = parseModifiers();
                    if (twolists != null)
                        {
                        // note to self: this language needs multiple return values
                        modifiers   = twolists[0];
                        annotations = twolists[1];
                        }

                    // both constant and property have a TypeExpression next
                    // method has "TypeVariableList-opt ReturnList" next (so "<" is a give-away)
                    // - ReturnList could be either a TypeExpression or a "(", so "(" is a give-away
                    // typecomp has a category keyword next
                    switch (peek().getId())
                        {
                        case MODULE:
                        case PACKAGE:
                        case CLASS:
                        case INTERFACE:
                        case SERVICE:
                        case CONST:
                        case ENUM:
                        case TRAIT:
                        case MIXIN:
                            // it's definitely a type composition
                            stmt = parseTypeDeclarationStatementAfterModifiers(doc, modifiers, annotations);
                            break;

                        case L_PAREN:
                        case COMP_LT:
                            {
                            // it's definitely a method
                            List<Token>          typeVars = parseTypeVariableList(false);
                            List<TypeExpression> returns  = parseReturnList();
                            Token                name     = match(Id.IDENTIFIER);
                            stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                    typeVars, returns, name);
                            }
                            break;

                        case CONSTRUCT:
                            {
                            Token keyword = expect(Id.CONSTRUCT);
                            Token name    = match(Id.IDENTIFIER);
                            stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                    null, null, name);
                            }
                            break;

                        default:
                            {
                            // it's a constant, property, or method
                            TypeExpression type = parseTypeExpression();
                            Token name = match(Id.IDENTIFIER);

                            if (peek().getId() == Id.L_PAREN)
                                {
                                // parenthesis means parameters, means it's a method
                                stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                        null, Collections.singletonList(type), name);
                                }
                            else if (peek().getId() == Id.ASN && modifiers != null
                                    && modifiers.size() == 1 && modifiers.get(0).getId() == Id.STATIC)
                                {
                                // "static" modifier and "=" means it's a constant
                                expect(Id.ASN);
                                Expression value = parseExpression();
                                expect(Id.SEMICOLON);
                                stmt = new ConstantDeclaration(modifiers.get(0), type, name, value, doc);
                                }
                            else
                                {
                                // it's a property
                                stmt = parsePropertyDeclarationFinish(doc, modifiers, annotations, type, name);
                                }
                            }
                        }
                    }
                }
            stmts.add(stmt);
            }

        return new BlockStatement(stmts);
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
    MethodDeclarationStatement parseMethodDeclarationAfterName(Token doc, List<Token> modifiers,
            List<Annotation> annotations, List<Token> typeVars, List<TypeExpression> returns, Token name)
        {
        List<Parameter> params = parseParameterList(true);
        BlockStatement  body   = match(Id.SEMICOLON) == null ? parseBlockStatement() : null;
        return new MethodDeclarationStatement(modifiers, annotations, typeVars, returns, name,
                params, body, doc);
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
     * @return a BlockStatement
     */
    PropertyDeclarationStatement parsePropertyDeclarationFinish(Token doc, List<Token> modifiers,
            List<Annotation> annotations, TypeExpression type, Token name)
        {
        Expression     value = null;
        BlockStatement body  = null;
        if (match(Id.ASN) != null)
            {
            // "=" Expression ";"
            value = parseExpression();
            expect(Id.SEMICOLON);
            }
        else if (match(Id.DOT) != null)
            {
            // "." Name Parameters MethodBody
            Token           methodName = expect(Id.IDENTIFIER);
            List<Parameter> params     = parseParameterList(true);
            MethodDeclarationStatement method = new MethodDeclarationStatement(null, null, null, null,
                    methodName, params, parseBlockStatement(), null);
            body = new BlockStatement(Collections.singletonList(method));
            }
        else if (match(Id.L_CURLY) != null)
            {
            body = parseBlockStatementRemainder(new ArrayList<>());
            }
        else
            {
            expect(Id.SEMICOLON);
            }

        return new PropertyDeclarationStatement(modifiers, annotations, type, name, value, body, doc);
        }

    /**
     * Parse a block statement.
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a BlockStatement
     */
    BlockStatement parseBlockStatement()
        {
        expect(Id.L_CURLY);
        return parseBlockStatementRemainder(new ArrayList<Statement>());
        }

    /**
     * Parse the remainder of a block statement.
     *
     * @return a list of Statements
     */
    BlockStatement parseBlockStatementRemainder(List<Statement> stmts)
        {
        // TODO temporarily skip processing inside the statement blocks
//        while (match(Id.R_CURLY) == null)
//            {
//            stmts.add(parseStatement());
//            }
        skipEnclosed(Id.L_CURLY);

        return new BlockStatement(stmts);
        }

    /**
     * Parse an Ecstasy statement.
     *
     * <p/><code><pre>
     * Statement
     *     TODO
     * </pre></code>
     *
     * @return
     */
    Statement parseStatement()
        {
        switch (peek().getId())
            {
            case RETURN:
                return parseReturnStatement();

            case STATIC:
                {
                // property declaration statement
                Token modifier = expect(Id.STATIC);
                TypeExpression type = parseTypeExpression();
                Token name = expect(Id.IDENTIFIER);
                // TODO the "property declaration finish" portion
                return null;
                }

            default:
                Expression expr = parseExpression();
                if (expr instanceof TypeExpression)
                    {
                    // variable declaration statement
                    Token name = expect(Id.IDENTIFIER);
                    Expression value = match(Id.ASN) == null ? null : parseExpression();
                    return new VariableDeclarationStatement((TypeExpression) expr, name, value);
                    }
                else
                    {
                    // expression statement
                    expect(Id.SEMICOLON);
                    return new ExpressionStatement(expr);
                    }
            }
        }

    /**
     * Parse a return statement
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a return statement
     */
    ReturnStatement parseReturnStatement()
        {
        Token keyword = expect(Id.RETURN);
        switch (peek().getId())
            {
            case SEMICOLON:
                expect(Id.SEMICOLON);
                return new ReturnStatement(keyword);

            case L_PAREN:
                {
                expect(Id.L_PAREN);
                // this is either a tuple of multiple values or a single expression
                List<Expression> exprs = parseExpressionList();
                expect(Id.R_PAREN);
                expect(Id.SEMICOLON);
                return new ReturnStatement(keyword, exprs);
                }

            case IDENTIFIER:
                if (peek().getValue().equals("Tuple"))
                    {
                    // literal tuple
                    // TODO
                    }
                // fall through
            default:
                {
                // expression list
                List<Expression> exprs = parseExpressionList();
                expect(Id.SEMICOLON);
                return new ReturnStatement(keyword, exprs);
                }
            }
        }

    /**
     * Parse any expression.
     *
     * <p/><code><pre>
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
     * Parse any expression.
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseExpression()
        {
        // TODO for now, just parse a name or a literal
        switch (peek().getId())
            {
            case IDENTIFIER:
                // TODO this is just a place-holder, because lots of expressions start with a name
                return new NameExpression(parseQualifiedName());

            case LIT_CHAR:
            case LIT_STRING:
            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                return new LiteralExpression(current());

            case TODO:
                return parseTodoExpression();

            default:
                // TODO
                throw new UnsupportedOperationException("parse expr");
            }
        }

    /**
     * Parse a type expression.
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a TypeExpression
     */
    TypeExpression parseTypeExpression()
        {
        return parseUnionedTypeExpression();
        }

    /**
     * Parse a type expression of the form "Type + Type".
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseUnionedTypeExpression()
        {
        TypeExpression expr = parseIntersectingTypeExpression();
        Token operator = match(Id.ADD);
        if (operator != null)
            {
            expr = new BiTypeExpression(expr, operator, parseIntersectingTypeExpression());
            }
        return expr;
        }

    /**
     * Parse a type expression of the form "Type | Type", otherwise .
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseIntersectingTypeExpression()
        {
        TypeExpression expr = parseNonBiTypeExpression();
        Token operator = match(Id.BIT_OR);
        if (operator != null)
            {
            expr = new BiTypeExpression(expr, operator, parseNonBiTypeExpression());
            }
        return expr;
        }

    /**
     * Parse any type expression that does NOT look like "Type + Type" or "Type | Type".
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseNonBiTypeExpression()
        {
        TypeExpression type;
        switch (peek().getId())
            {
            case L_PAREN:
                expect(Id.L_PAREN);
                type = parseTypeExpression();
                expect(Id.R_PAREN);
                break;

            case AT:
                type = parseAnnotatedTypeExpression();
                break;

            case FUNCTION:
                type = parseFunctionTypeExpression();
                break;

            case IMMUTABLE:
            default:
                type = parseNamedTypeExpression();
                break;
            }

        ParseSuffixes: while (true)
            {
            switch (peek().getId())
                {
                case L_SQUARE:
                    expect(Id.L_SQUARE);
                    // TODO could be an integer index e.g. ReturnValues[0]
                    expect(Id.R_SQUARE);
                    type = new ArrayTypeExpression(type);
                    break;

                case COND:
                    expect(Id.COND);
                    type = new NullableTypeExpression(type);
                    break;

                case ELLIPSIS:
                    expect(Id.ELLIPSIS);
                    type = new SequenceTypeExpression(type);
                    break;

                default:
                    break ParseSuffixes;
                }
            }

        return type;
        }

    /**
     * TODO
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return
     */
    AnnotatedTypeExpression parseAnnotatedTypeExpression()
        {
        Annotation annotation = parseAnnotation(true);
        TypeExpression type = parseTypeExpression();

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
     *     Name ParameterList
     *     ParameterList Name
     * </pre></code>
     *
     * @return a FunctionTypeExpression
     */
    FunctionTypeExpression parseFunctionTypeExpression()
        {
        Token function = expect(Id.FUNCTION);

        // return values
        List<TypeExpression> listReturn = parseReturnList();

        // see if the parameters preced the name
        List<Parameter> listParam = parseParameterList(false);

        // name optionally comes before or after the parameters
        Token name = match(Id.IDENTIFIER);

        if (listParam == null)
            {
            listParam = parseParameterList(true);
            }

        return new FunctionTypeExpression(function, name, listReturn, listParam);
        }

    /**
     * Type expression in the form:
     * "immutable name.name.name<param extends type, param extends type>"
     *
     * <p/><code><pre>
     * </pre></code>
     *
     * @return a NamedTypeExpression
     */
    NamedTypeExpression parseNamedTypeExpression()
        {
        Token immutable = match(Id.IMMUTABLE);
        List<Token> names = parseQualifiedName();
        List<TypeExpression> params = parseTypeParameterTypeList(false);
        return new NamedTypeExpression(immutable, names, params);
        }

    /**
     * TODO no pun intended
     *
     * <p/><code><pre>
     * </pre></code>

     * @return a TodoExpression
     */
    TodoExpression parseTodoExpression()
        {
        expect(Id.TODO);
        Expression message = null;
        if (match(Id.L_PAREN) != null)
            {
            message = parseExpression();
            expect(Id.R_PAREN);
            }
        return new TodoExpression(message);
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
     * @param couldBeProperty
     *
     * @return a List&lt;Token | Annotation&gt;
     */
    List[] parseModifiers(boolean couldBeProperty)
        {
        List<Token>      modifiers   = null;
        List<Annotation> annotations = null;
        while (true)
            {
            switch (peek().getId())
                {
                case STATIC:
                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                    if (modifiers == null)
                        {
                        modifiers = new ArrayList<>();
                        }
                    modifiers.add(current());
                    if (couldBeProperty && peek().getId() == Id.DIV)
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
                            }
                        modifiers.add(second);
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
     * @param required  true iff the annnotation is required
     *
     * @return an annotation, or null if no annotation was encountered
     */
    Annotation parseAnnotation(boolean required)
        {
        if (match(Id.AT, required) == null)
            {
            return null;
            }

        NamedTypeExpression type = parseNamedTypeExpression();

        List<Expression> args = parseArgumentList(false);

        return new Annotation(type, args);
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
                    type = parseTypeExpression();
                    }
                typeParams.add(new Parameter(type, param));
                }
            }
        return typeParams;
        }

    /**
     * TODO
     *
     * @param required  true iff the angle brackets are required
     *
     * @return a list of zero or more types, or null if there were no angle brackets
     */
    List<TypeExpression> parseTypeParameterTypeList(boolean required)
        {
        List<TypeExpression> types = null;
        if (match(Id.COMP_LT, required) != null)
            {
            types = new ArrayList<>();
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

                types.add(parseTypeExpression());
                }
            }
        return types;
        }

    /**
     * If the next token is a &quot;&lt;&quot;, then parse a list of type variables.
     *
     * <p/><code><pre>
     * TypeVariableList
     *     "<" TypeVariables ">"
     *
     * TypeVariables
     *     TypeVariable
     *     TypeVariables "," TypeVariable
     *
     * TypeVariable
     *     Name
     * </pre></code>
     *
     * @param required  true iff the angle brackets are required
     *
     * @return a list of zero or more type variables, or null if there were no angle brackets
     */
    List<Token> parseTypeVariableList(boolean required)
        {
        List<Token> names = null;
        if (match(Id.COMP_LT, required) != null)
            {
            names = new ArrayList<>();
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

                names.add(expect(Id.IDENTIFIER));
                }
            }
        return names;
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
            boolean first = true;
            while (match(Id.R_PAREN) == null)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                TypeExpression type  = parseTypeExpression();
                Token          name  = expect(Id.IDENTIFIER);
                Expression     value = null;
                if (match(Id.ASN) != null)
                    {
                    value = parseExpression();
                    }
                params.add(new Parameter(type, name, value));
                }
            }
        return params;
        }

    /**
     * Parse an argument list.
     *
     * <p/><code><pre>
     * ArgumentList
     *     "(" Arguments-opt ")"
     *
     * Arguments
     *     Argument
     *     Arguments "," Argument
     *
     * Argument
     *     Expression
     * </pre></code>
     *
     *
     * @param required  true iff the parenthesis are required
     *
     * @return a list of arguments, or null if no parenthesis were encountered
     */
    List<Expression> parseArgumentList(boolean required)
        {
        List<Expression> args = null;
        if (match(Id.L_PAREN, required) != null)
            {
            args = new ArrayList<>();
            boolean first = true;
            while (match(Id.R_PAREN) == null)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                args.add(parseExpression());
                }
            }
        return args;
        }

    /**
     * Parse a declared list of return types.
     *
     * ReturnList
     *     SingleReturnList
     *     MultiReturnList
     *
     * SingleReturnList
     *     Type
     *
     * MultiReturnList
     *     "(" Returns ")"
     *
     * Returns
     *     Return
     *     Returns "," Return
     *
     * Return
     *     Type Name-opt
     */
    List<TypeExpression> parseReturnList()
        {
        // return values
        List<TypeExpression> listReturn = new ArrayList<>();
        if (match(Id.L_PAREN) == null)
            {
            listReturn.add(parseTypeExpression());
            }
        else
            {
            boolean fFirst = true;
            while (match(Id.R_PAREN) == null)
                {
                if (fFirst)
                    {
                    fFirst = false;
                    }
                else
                    {
                    expect(Id.COMMA);
                    }

                listReturn.add(parseTypeExpression());
                }
            }
        return listReturn;
        }

    /**
     * Parse a sequence of version
     *
     * <p/><code><pre>
     * VersionRequirement
     *     Version VersionOverrides-opt
     *
     * VersionOverrides
     *     VersionOverride
     *     VersionOverrides VersionOverride
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
     *
     * Version
     *     VersionFinish
     *     Version . VersionFinish
     *
     * VersionFinish:
     *     NonGAPrefix-opt DigitsNoUnderscores
     *     NonGAPrefix DigitsNoUnderscores-opt
     *
     * NonGAPrefix:
     *     "dev"
     *     "ci"
     *     "alpha"
     *     "beta"
     *     "rc"
     * </pre></code>
     *
     * @param required  true if a version requirement must be present next in the stream of tokens
     *
     * @return a list of version overrides, or null if no version requirement is encountered
     */
    List<VersionOverride> parseVersionRequirement(boolean required)
        {
        // start with the initial version requirement (no preceding verb)
        Version ver = parseVersion(required);
        if (ver == null)
            {
            return null;
            }

        List<VersionOverride> overrides = new ArrayList<>();
        overrides.add(new VersionOverride(ver));

        // this is a little more complicated parsing because the keywords are context sensitive
        Token verb;
        while ((verb = match(Id.ALLOW )) != null ||
               (verb = match(Id.AVOID )) != null ||
               (verb = match(Id.PREFER)) != null)
            {
            boolean first = true;
            while (first || match(Id.COMMA) != null)
                {
                overrides.add(new VersionOverride(verb, parseVersion(true)));
                first = false;
                }
            }

        return overrides;
        }

    /**
     * Parse a single version id.
     *
     * <p/><code><pre>
     * # note: the StringLiteral must contain a VersionString
     * Version
     *     StringLiteral
     *
     * VersionString
     *     VersionFinish
     *     VersionString . VersionFinish
     *
     * VersionFinish:
     *     NonGAPrefix-opt DigitsNoUnderscores
     *     NonGAPrefix DigitsNoUnderscores-opt
     *
     * NonGAPrefix:
     *     "dev"
     *     "ci"
     *     "alpha"
     *     "beta"
     *     "rc"
     * </pre></code>
     *
     * @param required true iff the version must appear next in the stream of tokens
     *
     * @return a Version
     */
    Version parseVersion(boolean required)
        {
        Version version = null;
        Token   token   = match(Id.LIT_STRING, required);
        if (token != null)
            {
            String[] parts = parseDelimitedString((String) token.getValue(), '.');
            for (int i = 0, c = parts.length; i < c; ++i)
                {
                // each of the parts has to be an integer, except for the last which can start with
                // a non-GA designator
                if (!Version.isValidVersionPart(parts[i], i == c-1))
                    {
                    log(Severity.ERROR, BAD_VERSION, null, token.getStartPosition(), token.getEndPosition());
                    break;
                    }
                }

            // return a version even if the version is crap
            version = new Version(token);
            }
        return version;
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
                case TRAIT:
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

    protected Token peek()
        {
        return m_token;
        }

    /**
     * Obtain the current token.
     *
     * @return the current token
     */
    protected Token current()
        {
        final Token token = m_token;
        next();
        return token;
        }

    /**
     * Advance to and obtain the next token.
     *
     * @return the next token (which is now the "current" token)
     */
    protected Token next()
        {
        // weed out the comments (but store off the most recently encountered doc comment in case
        // anyone needs it later)
        while (m_lexer.hasNext())
            {
            m_token = m_lexer.next();
            switch (m_token.getId())
                {
                case ENC_COMMENT:
                    String sComment = (String) m_token.getValue();
                    if (sComment.length() > 0 && sComment.startsWith("*"))
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

        log(Severity.ERROR, UNEXPECTED_EOF, null, m_source.getPosition(), m_source.getPosition());
        throw new CompilerException("unexpected EOF");
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
        if (m_token.getId() == id)
            {
            return current();
            }

        final Token token = m_token;
        if (token.getId() == Id.IDENTIFIER && id.ContextSensitive && token.getValue().equals(id.TEXT))
            {
            // advance to the next token
            next();

            // return the previously "current" token
            return token.convertToKeyword();
            }

        return token.peel(id, m_source);
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

        log(Severity.ERROR, EXPECTED_TOKEN, new Token.Id[] {id, m_token.getId()},
                m_token.getStartPosition(), m_token.getEndPosition());
        throw new CompilerException("expected token: " + id + " (found: " + token + ")");
        }

    /**
     * @return true if the token stream is exhausted
     */
    protected boolean eof()
        {
        return !m_lexer.hasNext();
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
     * @param aoParam
     * @param lPosStart
     * @param lPosEnd
     */
    protected void log(Severity severity, String sCode, Object[] aoParam, long lPosStart, long lPosEnd)
        {
        if (m_errorListener.log(severity, sCode, aoParam, m_source, lPosStart, lPosEnd))
            {
            m_fAvoidRecovery = true;
            throw new CompilerException("error list is full: " + m_errorListener);
            }
        }

    /**
     * @return true iff it's ok to try to recover from a parsing error
     */
    protected boolean recoverable()
        {
        return !eof() && !m_fAvoidRecovery;
        }


    // ----- constants ---------------------------------------------------------

    /**
     * Unknown fatal error.
     */
    public static final String FATAL_ERROR    = "PARSER-01";
    /**
     * Unexpected End-Of-File (token exhaustion).
     */
    public static final String UNEXPECTED_EOF = "PARSER-02";
    /**
     * Expected a particular token.
     */
    public static final String EXPECTED_TOKEN = "PARSER-03";
    /**
     * Bad version value.
     */
    public static final String BAD_VERSION    = "PARSER-04";


    // ----- data members ------------------------------------------------------

    /**
     * The Source to parse.
     */
    private final Source m_source;

    /**
     * The ErrorListener to report errors to.
     */
    private final ErrorListener m_errorListener;

    /**
     * The lexical analyzer.
     */
    private final Lexer m_lexer;

    /**
     * The current token.
     */
    private Token m_token;

    /**
     * The most recent doc comment.
     */
    private Token m_doc;

    /**
     * The top-most (outer-most) type declaration of the compilation unit, such
     * as a module.
     */
    private Statement m_root;

    /**
     * True once parsing has occurred.
     */
    private boolean m_fDone;

    private boolean m_fAvoidRecovery;
    }