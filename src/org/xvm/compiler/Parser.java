package org.xvm.compiler;


import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.*;

import org.xvm.util.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.xvm.util.Handy.hexitValue;
import static org.xvm.util.Handy.isHexit;
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

        Token simpleName = expect(Id.IDENTIFIER);

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
        while (true)
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
                    List[] twolists = parseModifiers(true);
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
                            {
                            // it's a property or a method, but the property type is parenthesized
                            // or the method return types are parenthesized, which means we have to
                            // either find a list of types inside the parenthesis, or we have to go
                            // past the name and find a '<' or '(' to know it's a method
                            Mark pos = mark();

                            // assume it's a method
                            List<TypeExpression> returns = parseReturnList();
                            Token                name    = match(Id.IDENTIFIER);
                            if (name != null && (peek().getId() == Id.COMP_LT || peek().getId() == Id.L_PAREN))
                                {
                                stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                        null, returns, null, name);
                                }
                            else
                                {
                                // unfortunately, it wasn't a method, so we need to rewind and
                                // reparse as a property
                                restore(pos);
                                TypeExpression type = parseTypeExpression();
                                name = expect(Id.IDENTIFIER);
                                stmt = parsePropertyDeclarationFinish(doc, modifiers, annotations, type, name);
                                }
                            }
                            break;

                        case COMP_LT:
                            {
                            // it's definitely a method
                            List<Token>          typeVars = parseTypeVariableList(true);
                            List<TypeExpression> returns  = parseReturnList();
                            Token                name     = expect(Id.IDENTIFIER);
                            stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                    typeVars, returns, null, name);
                            }
                            break;

                        case CONSTRUCT:
                            {
                            Token keyword = expect(Id.CONSTRUCT);
                            Token name    = expect(Id.IDENTIFIER);
                            stmt = parseMethodDeclarationAfterName(doc, modifiers,
                                    annotations, null, null, keyword, name);
                            }
                            break;

                        default:
                            {
                            // it's a constant, property, or method
                            TypeExpression type = parseTypeExpression();
                            Token          name = expect(Id.IDENTIFIER);

                            if (peek().getId() == Id.COMP_LT || peek().getId() == Id.L_PAREN)
                                {
                                // '<' indicates redundant return type list
                                // '(' indicates parameters
                                stmt = parseMethodDeclarationAfterName(doc, modifiers, annotations,
                                        null, Collections.singletonList(type), null, name);
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
            List<Annotation> annotations, List<Token> typeVars, List<TypeExpression> returns,
            Token keyword, Token name)
        {
        List<TypeExpression> redundantReturns = parseTypeParameterTypeList(false);
        List<Parameter> params      = parseParameterList(true);
        BlockStatement  body        = match(Id.SEMICOLON) == null ? parseBlockStatement() : null;
        BlockStatement  stmtFinally = null;
        if (body != null && keyword != null && match(Id.FINALLY) != null)
            {
            stmtFinally = parseBlockStatement();
            }
        return new MethodDeclarationStatement(modifiers, annotations, typeVars, returns, name,
                redundantReturns, params, body, stmtFinally, doc);
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
                    methodName, null, params, parseBlockStatement(), null, null);
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
     * Expression
     *     TernaryExpression
     *     TernaryExpression ":" Expression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseExpression()
        {
        Expression expr = parseTernaryExpression();
        if (peek().getId() == Id.COLON)
            {
            expr = new BiExpression(expr, current(), parseExpression());
            }
        return expr;
        }

    /**
     * Parse a ternary expression, which is the "a ? b : c" expression.
     *
     * <p/><code><pre>
     * TernaryExpression
     *     ElvisExpression
     *     ElvisExpression Whitespace "?" TernaryExpression ":" TernaryExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseTernaryExpression()
        {
        Expression expr = parseElvisExpression();
        if (peek().getId() == Id.COND && peek().hasLeadingWhitespace())
            {
            expect(Id.COND);
            Expression exprThen = parseTernaryExpression();
            expect(Id.COLON);
            Expression exprElse = parseTernaryExpression();
            expr = new TernaryExpression(expr, exprThen, exprElse);
            }
        return expr;
        }

    /**
     * Parse an "elvis" expression, which is of the form "a ?: b".
     *
     * <p/><code><pre>
     * ElvisExpression
     *     OrExpression
     *     OrExpression ?: ElvisExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseElvisExpression()
        {
        Expression expr = parseOrExpression();
        if (peek().getId() == Id.COND_ELSE)
            {
            expr = new BiExpression(expr, current(), parseElvisExpression());
            }
        return expr;
        }

    /**
     * Parse a logical "or" expression.
     *
     * <p/><code><pre>
     * OrExpression
     *     AndExpression
     *     OrExpression || AndExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseOrExpression()
        {
        Expression expr = parseAndExpression();
        while (peek().getId() == Id.COND_OR)
            {
            expr = new BiExpression(expr, current(), parseAndExpression());
            }
        return expr;
        }

    /**
     * Parse a logical "and" expression.
     *
     * <p/><code><pre>
     * AndExpression
     *     BitOrExpression
     *     AndExpression && BitOrExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseAndExpression()
        {
        Expression expr = parseBitOrExpression();
        while (peek().getId() == Id.COND_AND)
            {
            expr = new BiExpression(expr, current(), parseBitOrExpression());
            }
        return expr;
        }

    /**
     * Parse a bitwise "or" expression.
     *
     * <p/><code><pre>
     * BitOrExpression
     *     BitXorExpression
     *     BitOrExpression | BitXorExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseBitOrExpression()
        {
        Expression expr = parseBitXorExpression();
        while (peek().getId() == Id.BIT_OR)
            {
            expr = new BiExpression(expr, current(), parseBitXorExpression());
            }
        return expr;
        }

    /**
     * Parse a bitwise "xor" expression.
     *
     * <p/><code><pre>
     * BitXorExpression
     *     BitAndExpression
     *     BitXorExpression ^ BitAndExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseBitXorExpression()
        {
        Expression expr = parseBitAndExpression();
        while (peek().getId() == Id.BIT_XOR)
            {
            expr = new BiExpression(expr, current(), parseBitAndExpression());
            }
        return expr;
        }

    /**
     * Parse a bitwise "and" expression.
     *
     * <p/><code><pre>
     * BitAndExpression
     *     EqualityExpression
     *     BitAndExpression & EqualityExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseBitAndExpression()
        {
        Expression expr = parseEqualityExpression();
        while (peek().getId() == Id.BIT_AND)
            {
            expr = new BiExpression(expr, current(), parseEqualityExpression());
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
     * @return an expression
     */
    Expression parseEqualityExpression()
        {
        Expression expr = parseRelationalExpression();
        while (peek().getId() == Id.COMP_EQ || peek().getId() == Id.COMP_NEQ)
            {
            expr = new BiExpression(expr, current(), parseRelationalExpression());
            }
        return expr;
        }

    /**
     * Parse a relational expression.
     *
     * <p/><code><pre>
     * RelationalExpression
     *     RangeExpression
     *     RelationalExpression "<" RangeExpression
     *     RelationalExpression ">" RangeExpression
     *     RelationalExpression "<=" RangeExpression
     *     RelationalExpression ">=" RangeExpression
     *     RelationalExpression "<=>" RangeExpression
     *     RelationalExpression "instanceof" TypeExpression
     *     RelationalExpression "as" TypeExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseRelationalExpression()
        {
        Expression expr = parseRangeExpression();
        while (true)
            {
            switch (peek().getId())
                {
                case COMP_LT:
                case COMP_GT:
                case COMP_LTEQ:
                case COMP_GTEQ:
                case COMP_ORD:
                    expr = new BiExpression(expr, current(), parseRangeExpression());
                    break;

                case AS:
                case INSTANCEOF:
                    expr = new BiExpression(expr, current(), parseTypeExpression());
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse a range or interval expression.
     *
     * <p/><code><pre>
     * RangeExpression
     *     ShiftExpression
     *     RangeExpression ".." ShiftExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseRangeExpression()
        {
        Expression expr = parseShiftExpression();
        while (peek().getId() == Id.DOTDOT)
            {
            expr = new BiExpression(expr, current(), parseShiftExpression());
            }
        return expr;
        }

    /**
     * Parse a bitwise shift expression.
     *
     * <p/><code><pre>
     * ShiftExpression
     *     AdditiveExpression
     *     ShiftExpression "<<" AdditiveExpression
     *     ShiftExpression ">>" AdditiveExpression
     *     ShiftExpression ">>>" AdditiveExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseShiftExpression()
        {
        Expression expr = parseAdditiveExpression();
        while (true)
            {
            switch (peek().getId())
                {
                case SHL:
                case SHR:
                case USHR:
                    expr = new BiExpression(expr, current(), parseRangeExpression());
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse an addition or substraction expression.
     *
     * <p/><code><pre>
     * AdditiveExpression
     *     MultiplicativeExpression
     *     AdditiveExpression "+" MultiplicativeExpression
     *     AdditiveExpression "-" MultiplicativeExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseAdditiveExpression()
        {
        Expression expr = parseMultiplicativeExpression();
        while (peek().getId() == Id.ADD || peek().getId() == Id.SUB)
            {
            expr = new BiExpression(expr, current(), parseMultiplicativeExpression());
            }
        return expr;
        }

    /**
     * Parse a multiplication / division / modulo expression.
     *
     * <p/><code><pre>
     * MultiplicativeExpression
     *     PrefixExpression
     *     MultiplicativeExpression "*" PrefixExpression
     *     MultiplicativeExpression "/" PrefixExpression
     *     MultiplicativeExpression "%" PrefixExpression
     *     MultiplicativeExpression "/%" PrefixExpression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parseMultiplicativeExpression()
        {
        Expression expr = parsePrefixExpression();
        while (true)
            {
            switch (peek().getId())
                {
                case MUL:
                case DIV:
                case MOD:
                case DIVMOD:
                    expr = new BiExpression(expr, current(), parsePrefixExpression());
                    break;

                default:
                    return expr;
                }
            }
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
     *     "&" PrefixExpression
     *     "new" TypeExpression ArgumentList-opt
     * </pre></code>
     *
     * @return an expression
     */
    Expression parsePrefixExpression()
        {
        switch (peek().getId())
            {
            case INC:
            case DEC:
            case ADD:
            case SUB:
            case NOT:
            case BIT_NOT:
            case BIT_AND:
                return new PrefixExpression(current(), parsePrefixExpression());

            case NEW:
                return new NewExpression(current(), parseTypeExpression(), parseArgumentList(false));

            default:
                return parsePostfixExpression();
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
     *     PostfixExpression ArgumentList
     *     PostfixExpression ArrayDims
     *     PostfixExpression ArrayIndex
     *     PostfixExpression NoWhitespace "?"
     *     PostfixExpression "." Name
     *     PostfixExpression ".new" ArgumentList-opt
     *     PostfixExpression ".instanceof" "(" TypeExpression ")"
     *     PostfixExpression ".as" "(" TypeExpression ")"
     *
     * ArrayDims
     *     ArrayDim
     *     ArrayDims ArrayDim
     *
     * ArrayDim
     *     "[" DimIndicators-opt "]"
     *
     * DimIndicators
     *     DimIndicator
     *     DimIndicators "," DimIndicator
     *
     * DimIndicator
     *     "?"
     *
     * ArrayIndex
     *     "[" ExpressionList "]"
     *
     * ExpressionList
     *     Expression
     *     ExpressionList "," Expression
     * </pre></code>
     *
     * @return an expression
     */
    Expression parsePostfixExpression()
        {
        Expression expr = parsePrimaryExpression();
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
                    // fall through;
                case INC:
                case DEC:
                    expr = new PostfixExpression(expr, current());
                    break;

                case DOT:
                    {
                    expect(Id.DOT);
                    switch (peek().getId())
                        {
                        case NEW:
                            {
                            Token keyword = expect(Id.NEW);
                            expr = new NewExpression(expr, keyword, parseTypeExpression(), parseArgumentList(false));
                            break;
                            }

                        case AS:
                        case INSTANCEOF:
                            {
                            Token keyword = current();
                            expect(Id.L_PAREN);
                            expr = new BiExpression(expr, keyword, parseTypeExpression());
                            expect(Id.R_PAREN);
                            break;
                            }

                        case IDENTIFIER:
                            if (expr instanceof NameExpression)
                                {
                                ((NameExpression) expr).names.add(expect(Id.IDENTIFIER));
                                }
                            else
                                {
                                expr = new DotNameExpression(expr, expect(Id.IDENTIFIER));
                                }
                            break;

                        default:
                            // unexpected token, so pretend we were looking for a name
                            expect(Id.IDENTIFIER);
                            skipToNextStatement();
                            return expr;
                        }
                    break;
                    }

                case L_PAREN:
                    // ArgumentList
                    expr = new InvocationExpression(expr, parseArgumentList(true));
                    break;

                case L_SQUARE:
                    {
                    // ArrayDims
                    // ArrayIndex
                    expect(Id.L_SQUARE);
                    if (match(Id.R_SQUARE) != null)
                        {
                        // "SomeClass[]"
                        expr = new ArrayTypeExpression(expr.toTypeExpression(), 0);
                        }
                    else if (match(Id.COND) != null)
                        {
                        // "SomeClass[?,?]"
                        int cExplicitDims = 1;
                        while (match(Id.R_SQUARE) == null)
                            {
                            expect(Id.COMMA);
                            expect(Id.COND);
                            ++cExplicitDims;
                            }
                        expr = new ArrayTypeExpression(expr.toTypeExpression(), cExplicitDims);
                        }
                    else
                        {
                        // "someArray[3]"
                        List<Expression> indexes = parseExpressionList();
                        expect(Id.R_SQUARE);
                        expr = new ArrayAccessExpression(expr, indexes);
                        }
                    break;
                    }

                default:
                    return expr;
                }
            }
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
     *       parametized type.
     * </li></ul>
     * <p/><code><pre>
     * PrimaryExpression
     *     QualifiedNameName TypeParameterTypeList-opt
     *     Literal
     *     LambdaExpression
     *     "_"
     *     "(" Expression ")"
     *     "T0D0" TodoMessage-opt       (note: 'O' replaced with '0' to suppress IDE highlighting)
     * </pre></code>
     *
     * @return an expression
     */
    Expression parsePrimaryExpression()
        {
        switch (peek().getId())
            {
            case IGNORED:
                return new IgnoredNameExpression(current());

            default:
            case IDENTIFIER:
                {
                NameExpression exprName = new NameExpression(expect(Id.IDENTIFIER));
                Token dot;
                while ((dot = match(Id.DOT)) != null)
                    {
                    Token name = match(Id.IDENTIFIER);
                    if (name == null)
                        {
                        putback(dot);
                        return exprName;
                        }
                    else
                        {
                        exprName.names.add(name);
                        }
                    }

                // test to see if there is a type parameter list (implying the expression is a type)
                Expression expr = exprName;
                if (peek().getId() == Id.COMP_LT)
                    {
                    try (SafeLookAhead attempt = new SafeLookAhead())
                        {
                        List<TypeExpression> params = parseTypeParameterTypeList(true);
                        if (attempt.isClean())
                            {
                            attempt.keepResults();
                            expr = new NamedTypeExpression(null, exprName.names, params);
                            }
                        }
                    }

                //test to see if this is a tuple literal of the form "Tuple:(", or some other
                // type literal of the form "type:{"
                Token colon = match(Id.COLON);
                if (colon != null)
                    {
                    if (!colon.hasLeadingWhitespace() && !colon.hasTrailingWhitespace() &&
                            (peek().getId() == Id.L_CURLY ||
                            (peek().getId() == Id.L_PAREN && expr.toString().equals("Tuple"))))
                        {
                        expr = parseCustomLiteral(expr.toTypeExpression());
                        }
                    else
                        {
                        putback(colon);
                        }
                    }

                return expr;
                }

            case L_PAREN:
                {
                /// this could be a tuple literal, a parenthesized expression, or a lambda
                // expression's parameter list
                expect(Id.L_PAREN);
                Expression expr = parseExpression();
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

                        if (peek().getId() == Id.LAMBDA)
                            {
                            return new ImplicitLambdaExpression(exprs, expect(Id.LAMBDA), parseLambdaBody());
                            }

                        // it's a Tuple literal
                        return new TupleExpression(exprs);

                    case R_PAREN:
                        // this is either a parenthesized expression or a single parameter for a
                        // lambda (it's not a tuple literal)
                        expect(Id.R_PAREN);
                        if (peek().getId() == Id.LAMBDA)
                            {
                            return new ImplicitLambdaExpression(Collections.EMPTY_LIST,
                                    expect(Id.LAMBDA), parseLambdaBody());
                            }
                        else
                            {
                            // just a parenthesized expression
                            return expr;
                            }

                    case IDENTIFIER:
                        // it has to be a lambda, because we just parse an expression (which must
                        // have been a parameter type) and now we have the parameter name
                        {
                        List<Parameter> params = new ArrayList<>();
                        params.add(new Parameter(expr.toTypeExpression(), expect(Id.IDENTIFIER)));
                        while (match(Id.COMMA) != null)
                            {
                            TypeExpression type  = parseTypeExpression();
                            Token          name  = expect(Id.IDENTIFIER);
                            params.add(new Parameter(type, name));
                            }
                        expect(Id.R_PAREN);

                        return new ExplicitLambdaExpression(params, expect(Id.LAMBDA), parseLambdaBody());
                        }

                    default:
                        expect(Id.R_PAREN);
                        skipToNextStatement();
                        return expr;
                    }
                }

            case L_CURLY:
                return parseCustomLiteral(null);

            case LIT_CHAR:
            case LIT_STRING:
            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                return new LiteralExpression(current());

            case TODO:
                return parseTodoExpression();
            }
        }

    /**
     * Parse a Lambda body and turn it into a BlockStatement if necessary.
     *
     * @return a BlockStatement representing the body of the lambda
     */
    BlockStatement parseLambdaBody()
        {
        Token firstToken = peek();
        if (firstToken.getId() == Id.L_CURLY)
            {
            return parseBlockStatement();
            }

        Token fakeReturn = new Token(firstToken.getStartPosition(), firstToken.getStartPosition(), Id.RETURN);
        ReturnStatement stmt = new ReturnStatement(fakeReturn, parseExpression());
        return new BlockStatement(Collections.singletonList(stmt));
        }

    /**
     * Parse a "to-do" expression.
     *
     * <p/><code><pre>
     * "T0D0" TodoMessage-opt       (note: 'O' replaced with '0' to suppress IDE highlighting)
     *
     * TodoMessage
     *     "(" Expression ")"
     * </pre></code>
     *
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
     * Parse a complex literal.
     *
     * <p/><code><pre>
     * # Whitespace allowed
     * BinaryLiteral
     *     "Binary:{" Nibbles-opt "}"
     *
     * Nibbles
     *     Nibble
     *     Nibbles Nibble
     *
     * Nibble: one of ...
     *     "0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "A" "a" "B" "b" "C" "c" "D" "d" "E" "e" "F" "f"
     *
     * TupleLiteral
     *     "(" ExpressionList "," Expression ")"
     *     "Tuple:(" ExpressionList-opt ")"
     *     "Tuple:{" ExpressionList-opt "}"
     *
     * ListLiteral
     *     "{" ExpressionList-opt "}"
     *     "List:{" ExpressionList-opt "}"
     *
     * MapLiteral
     *     "Map:{" Entries-opt "}"
     *
     * Entries
     *     Entry
     *     Entries "," Entry
     *
     * Entry
     *     Expression "=" Expression
     *
     * CustomLiteral
     *     TypeExpression NoWhitespace ":{" Expression "}"
     * </pre></code>
     *
     * @return
     */
    Expression parseCustomLiteral(TypeExpression exprType)
        {
        switch (exprType == null ? "List" : exprType.toString())
            {
            case "Binary":
                {
                // special note: at this point, the current token (already parsed) is the opening
                // curly bracket, so the lexer has already eaten any whitespace after that, and is
                // ready to eat the hex contents of the literal itself; unfortunately, this means
                // that we know the explicit details about how both the current/next token handling
                // works on the parser, and to some extent how the lexer works as well, but it is
                // context-sensitive parsing in the first place, which is already the bane of
                // parsing purists everywhere
                m_lexer.expectHex();
                long lPosStart = m_source.getPosition();

                expect(Id.L_CURLY);
                StringBuilder sb = new StringBuilder();
                Token literal;
                while ((literal = match(Id.LIT_STRING)) != null)
                    {
                    for (char ch : ((String) literal.getValue()).toCharArray())
                        {
                        if (isHexit(ch))
                            {
                            sb.append(ch);
                            }
                        else if (!Lexer.isWhitespace(ch))
                            {
                            log(Severity.ERROR, BAD_HEX_LITERAL, null,
                                    literal.getStartPosition(), literal.getEndPosition());
                            }
                        }
                    }
                long lPosEnd = m_source.getPosition();
                expect(Id.R_CURLY);

                int    cch  = sb.length();
                int    ofch = 0;
                int    cb   = (cch + 1) / 2;
                int    ofb  = 0;
                byte[] ab   = new byte[cb];
                if ((cch & 0x1) != 0)
                    {
                    // odd number of characters means that the first nibble is a pre-pended zero
                    ab[ofb++] = (byte) hexitValue(sb.charAt(ofch++));
                    }
                while (ofb < cb)
                    {
                    ab[ofb++] = (byte) ((hexitValue(sb.charAt(ofch++)) << 4)
                                       + hexitValue(sb.charAt(ofch++)));
                    }

                return new BinaryExpression(ab, lPosStart, lPosEnd);
                }

            case "List":
                {
                expect(Id.L_CURLY);
                List<Expression> exprs = null;
                while (match(Id.R_CURLY) == null)
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
                return new ListExpression(exprs);
                }

            case "Map":
                {
                expect(Id.L_CURLY);
                List<Expression> keys   = null;
                List<Expression> values = null;
                while (match(Id.R_CURLY) == null)
                    {
                    if (keys == null)
                        {
                        keys   = new ArrayList<>();
                        values = new ArrayList<>();
                        }
                    else
                        {
                        expect(Id.COMMA);
                        }
                    keys.add(parseExpression());
                    expect(Id.ASN);
                    values.add(parseExpression());
                    }
                return new MapExpression(keys, values);
                }

            case "Tuple":
                {
                Token.Id close = Id.R_PAREN;
                if (match(Id.L_PAREN) == null)
                    {
                    expect(Id.L_CURLY);
                    close = Id.R_CURLY;
                    }

                List<Expression> exprs = null;
                while (match(close) == null)
                    {
                    if (exprs == null)
                        {
                        exprs = new ArrayList<>();
                        }
                    exprs.add(parseExpression());
                    }
                return new TupleExpression(exprs);
                }

            default:
                {
                Token      open  = expect(Id.L_CURLY);
                Expression expr  = parseExpression();
                Token      close = expect(Id.R_CURLY);
                // custom type parsing is not supported in the prototype compiler
                log(Severity.ERROR, BAD_CUSTOM, new Object[] {exprType, expr},
                        open.getStartPosition(), close.getEndPosition());
                return expr;
                }
            }
        }

    /**
     * Parse a type expression.
     *
     * <p/><code><pre>
     * TypeExpression
     *     UnionedTypeExpression
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
     * UnionedTypeExpression
     *     IntersectingTypeExpression
     *     UnionedTypeExpression + IntersectingTypeExpression
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseUnionedTypeExpression()
        {
        TypeExpression expr = parseIntersectingTypeExpression();
        while (peek().getId() == Id.ADD)
            {
            expr = new BiTypeExpression(expr, expect(Id.ADD), parseIntersectingTypeExpression());
            }
        return expr;
        }

    /**
     * Parse a type expression of the form "Type | Type", otherwise .
     *
     * <p/><code><pre>
     * IntersectingTypeExpression
     *     NonBiTypeExpression
     *     IntersectingTypeExpression | NonBiTypeExpression
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseIntersectingTypeExpression()
        {
        TypeExpression expr = parseNonBiTypeExpression();
        while (peek().getId() == Id.BIT_OR)
            {
            expr = new BiTypeExpression(expr, expect(Id.BIT_OR), parseNonBiTypeExpression());
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
     *     NonBiTypeExpression "?"
     *     NonBiTypeExpression ArrayDim
     *     NonBiTypeExpression "..."
     *     "conditional" NonBiTypeExpression
     *     "immutable" NonBiTypeExpression
     *
     * NamedTypeExpression
     *     QualifiedName TypeParameterTypeList-opt
     *
     * ArrayDim
     *     "[" DimIndicators-opt "]"
     *
     * DimIndicators
     *     DimIndicator
     *     DimIndicators "," DimIndicator
     *
     * DimIndicator
     *     "?"
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

            case CONDITIONAL:
            case IMMUTABLE:
                type = new DecoratedTypeExpression(current(), parseNonBiTypeExpression());
                break;

            default:
                type = parseNamedTypeExpression();
                break;
            }

        while (true)
            {
            switch (peek().getId())
                {
                case L_SQUARE:
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
                    type = new ArrayTypeExpression(type, cExplicitDims);
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
                    return type;
                }
            }
        }

    /**
     * Parse a type expression that is preceded by an annotation.
     *
     * <p/><code><pre>
     * AnnotatedTypeExpression
     *     Annotation TypeExpression
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
        List<TypeExpression> listReturn = parseReturnList();

        // see if the parameters precede the name
        List<TypeExpression> listParam = parseParameterTypeList(false);

        if (listParam == null)
            {
            // name optionally comes before or after the parameters
            Token name = expect(Id.IDENTIFIER);
            listParam = parseParameterTypeList(true);

            // pretend the name is the next token (as if we didn't eat it
            putback(name);
            }

        return new FunctionTypeExpression(function, listReturn, listParam);
        }

    /**
     * Parse a type expression in the form:
     *
     *   "immutable name.name.name<param, param>"
     *
     * <p/><code><pre>
     * NamedTypeExpression
     *     QualifiedName TypeParameterTypeList-opt
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
        List<Expression>    args = null;

        // a trailing argument list is only assumed to be part of the annotation if there is
        // no whitespace separating the annotation from the arguments
        Token token = peek();
        if (token != null && token.getId() == Id.L_PAREN && !token.hasLeadingWhitespace())
            {
            args = parseArgumentList(true);
            }

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
     * If the next token is a &quot;&lt;&quot;, then parse a list of type variables. These aren't
     * expressions; they are just variable names.
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
    List<TypeExpression> parseTypeParameterTypeList(boolean required)
        {
        List<TypeExpression> types = null;
        if (match(Id.COMP_LT, required) != null)
            {
            if (peek().getId() != Id.COMP_GT)
                {
                types = parseTypeExpressionList();
                }
            expect(Id.COMP_GT);
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
    List<TypeExpression> parseTypeExpressionList()
        {
        List<TypeExpression> types = new ArrayList<>();
        types.add(parseTypeExpression());
        while (match(Id.COMMA) != null)
            {
            types.add(parseTypeExpression());
            }
        return types;
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
            types = peek().getId() == Id.R_PAREN
                    ? Collections.EMPTY_LIST
                    : parseTypeExpressionList();
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
     *     Argument
     *     Arguments "," Argument
     *
     * Argument
     *     NamedArgument-opt ArgumentExpression
     *
     * # note: the "?" argument allows functions to specify arguments that they are NOT binding
     * ArgumentExpression
     *     "?"
     *     Expression
     *
     * NamedArgument
     *     Name "="
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

                // special case where the parameter names are being specified with the arguments
                if (peek().getId() == Id.IDENTIFIER)
                    {
                    Token name = expect(Id.IDENTIFIER);
                    if (match(Id.ASN) == null)
                        {
                        // oops, it wasn't a "name=value" argument
                        putback(name);
                        }
                    else
                        {
                        args.add(new NamedExpression(name, parseExpression()));
                        continue;
                        }
                    }

                args.add(parseExpression());
                }
            }
        return args;
        }

    /**
     * Parse a declared list of return types.
     *
     * <p/><code><pre>
     * ReturnList
     *     SingleReturnList
     *     MultiReturnList
     *
     * SingleReturnList
     *     TypeExpression
     *
     * MultiReturnList
     *     "(" TypeExpressionList ")"
     * </pre></code>
     */
    List<TypeExpression> parseReturnList()
        {
        List<TypeExpression> listReturn;
        if (match(Id.L_PAREN) == null)
            {
            listReturn = Collections.singletonList(parseTypeExpression());
            }
        else
            {
            listReturn = parseTypeExpressionList();
            expect(Id.R_PAREN);
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
        return m_tokenPutback == null ? m_token : m_tokenPutback;
        }

    /**
     * Obtain the current token.
     *
     * @return the current token
     */
    protected Token current()
        {
        final Token token = peek();
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
        if (m_tokenPutback != null)
            {
            m_tokenPutback = null;
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

    protected void putback(Token token)
        {
        assert m_tokenPutback == null;
        m_tokenPutback = token;
        }

    private class Mark
        {
        long    pos;
        Token   token;
        Token   putback;
        Token   doc;
        boolean norec;
        }

    protected Mark mark()
        {
        Mark mark = new Mark();
        mark.pos     = m_lexer.getPosition();
        mark.token   = m_token;
        mark.putback = m_tokenPutback;
        mark.doc     = m_doc;
        mark.norec   = m_fAvoidRecovery;
        return mark;
        }

    protected void restore(Mark mark)
        {
        m_lexer.setPosition(mark.pos);
        m_token           = mark.token;
        m_tokenPutback    = mark.putback;
        m_doc             = mark.doc;
        m_fAvoidRecovery  = mark.norec;
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
        if (peek().getId() == id)
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

        // TODO remove this so there can be more than one error reported
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
    /**
     * Bad hex value.
     */
    public static final String BAD_HEX_LITERAL= "PARSER-05";
    /**
     * Unsupported custom literal.
     */
    public static final String BAD_CUSTOM     = "PARSER-06";


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
     * The "put back" token.
     */
    private Token m_tokenPutback;

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

    private SafeLookAhead m_lookAhead;
    }