package org.xvm.compiler;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Component;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler.Stage;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.*;

import org.xvm.util.Handy;
import org.xvm.util.Severity;

import static org.xvm.util.Handy.hexitValue;
import static org.xvm.util.Handy.isHexit;
import static org.xvm.util.Handy.parseDelimitedString;


/**
 * A recursive descent parser for Ecstasy source code.
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
    public StatementBlock parseSource()
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
                List<Statement> stmts = parseTypeCompositionComponents(null, new ArrayList<>(), true);
                m_root = new StatementBlock(stmts, m_source, stmts.get(0).getStartPosition(),
                        stmts.get(stmts.size()-1).getEndPosition());
                }
            catch (UnsupportedOperationException e)
                {
                // temporary exception handling while compiler is being built
                throw new CompilerException(e);
                }

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
     * Temporary: Allows a type to be parsed from a string in the debugger. (REMOVE LATER!)
     *
     * @param ctx  the component that links this in to a real module
     *
     * @return the TypeConstant represented by the parsed String
     */
    public TypeConstant parseType(Component ctx)
        {
        next();
        TypeExpression type = parseTypeExpression();

        Token var = new Token(type.getEndPosition(), type.getEndPosition(), Id.IDENTIFIER, "test");
        Statement stmt = new VariableDeclarationStatement(type, var, null);
        StatementBlock body = new StatementBlock(Arrays.asList(stmt));
        TypeCompositionStatement module = new TypeCompositionStatement(m_source,
                type.getStartPosition(), type.getEndPosition(), null, null, null,
                new Token(0, 0, Id.MODULE), null, null, null, null, null, body, null);
        module.buildDumpModule(ctx);
        boolean fOK = new StageMgr(module, Stage.Registered, m_errorListener).processComplete()
                   && new StageMgr(module, Stage.Resolved  , m_errorListener).processComplete()
                   && new StageMgr(module, Stage.Validated , m_errorListener).processComplete();
        assert fOK;
        return type.ensureTypeConstant();
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
        List<Token> modifiers = null;
        List<Annotation> annotations = null;
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
            Expression exprCondition, Token doc, List<Token> modifiers, List<Annotation> annotations)
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
        List<Composition> compositions = new ArrayList<>();
        parseConditionalComposition(null, compositions);

        // TypeCompositionBody
        StatementBlock body = null;
        long           lEndPos;
        if (peek().getId() == Id.L_CURLY)
            {
            body    = parseTypeCompositionBody(category);
            lEndPos = body.getEndPosition();
            }
        else
            {
            lEndPos = getLastMatch().getEndPosition();
            expect(Id.SEMICOLON);
            }

        return new TypeCompositionStatement(m_source, lStartPos, lEndPos, exprCondition, modifiers,
                annotations, category, name, qualified, typeParams, constructorParams, compositions,
                body, doc);
        }

    /**
     * Parse any compositions, including any conditions that surround them.
     *
     * @param exprCondition  the condition (or null if none) that applies to any found compositions
     * @param compositions   a list of compositions to contribute to
     */
    void parseConditionalComposition(Expression exprCondition, List<Composition> compositions)
        {
        boolean fAny;
        do
            {
            if (peek().getId() == Id.IF)
                {
                Token tokIf = expect(Id.IF);
                expect(Id.L_PAREN);
                Expression exprIf = parseCondition();
                expect(Id.R_PAREN);

                // then ...
                expect(Id.L_CURLY);
                Token tokAnd = null;
                if (exprCondition == null)
                    {
                    parseConditionalComposition(exprIf, compositions);
                    }
                else
                    {
                    tokAnd = new Token(tokIf.getStartPosition(), tokIf.getEndPosition(), Id.COND_AND);
                    parseConditionalComposition(new CmpExpression(exprCondition, tokAnd, exprIf), compositions);
                    }
                expect(Id.R_CURLY);

                // else ...
                Token tokElse = match(Id.ELSE);
                if (tokElse != null)
                    {
                    Token      tokNot   = new Token(tokElse.getStartPosition(), tokElse.getEndPosition(), Id.NOT);
                    Expression exprElse = new PrefixExpression(tokNot, exprIf);
                    boolean    fElseIf  = peek().getId() == Id.IF;
                    if (!fElseIf)
                        {
                        expect(Id.L_CURLY);
                        }
                    if (exprCondition == null)
                        {
                        parseConditionalComposition(exprElse, compositions);
                        }
                    else
                        {
                        parseConditionalComposition(new CmpExpression(exprCondition, tokAnd, exprElse), compositions);
                        }
                    if (!fElseIf)
                        {
                        expect(Id.R_CURLY);
                        }
                    }

                fAny = true;
                }
            else
                {
                fAny = parseComposition(exprCondition, compositions);
                }
            }
        while (fAny);
        }

    /**
     * Parse any compositions found, but do not handle any conditional statements.
     *
     * @param exprCondition  the condition (or null if none) that applies to any found compositions
     * @param compositions   a list of compositions to contribute to
     *
     * @return true if at least one composition was parsed
     */
    boolean parseComposition(Expression exprCondition, List<Composition> compositions)
        {
        boolean fAny = false;
        while (true)
            {
            // the keywords below require "match()" to extract them, because they are context
            // sensitive
            Token keyword;
            if ((keyword = match(Id.IMPLEMENTS)) != null)
                {
                do
                    {
                    compositions.add(new Composition.Implements(exprCondition, keyword, parseTypeExpression()));
                    }
                while (match(Id.COMMA) != null);
                fAny = true;
                }
            else if ((keyword = match(Id.DELEGATES)) != null)
                {
                do
                    {
                    TypeExpression type = parseTypeExpression();
                    expect(Id.L_PAREN);
                    Token tokProp = expect(Id.IDENTIFIER);
                    Token tokEnd = expect(Id.R_PAREN);
                    compositions.add(new Composition.Delegates(exprCondition, keyword, type,
                            tokProp, tokEnd.getEndPosition()));
                    }
                while (match(Id.COMMA) != null);
                fAny = true;
                }
            else if ((keyword = match(Id.INCORPORATES)) != null)
                {
                // "incorporates" IncorporatesFinish
                // IncorporatesFinish
                //     "conditional" QualifiedName TypeParameterList ArgumentList-opt
                //     TypeExpression ArgumentList-opt
                do
                    {
                    TypeExpression  type;
                    List<Parameter> constraints = null;
                    if (match(Id.CONDITIONAL) == null)
                        {
                        type = parseTypeExpression();
                        }
                    else
                        {
                        // parse the type parameter list e.g. "<KeyType extends Int, ValueType>",
                        // and turn it into a type parameter name list e.g. "<KeyType, ValueType>"
                        List<Token>          names       = parseQualifiedName();
                                             constraints = parseTypeParameterList(true);
                        List<TypeExpression> paramnames  = new ArrayList<>();
                        for (Parameter param : constraints)
                            {
                            Token       tokName       = param.getNameToken();
                            List<Token> listParamName = Collections.singletonList(tokName);
                            paramnames.add(new NamedTypeExpression(null, listParamName,
                                    null, null, null, tokName.getEndPosition()));
                            }
                        type = new NamedTypeExpression(null, names, null, null, paramnames, getLastMatch().getEndPosition());
                        }
                    List<Expression> args = parseArgumentList(false, false, false);
                    compositions.add(new Composition.Incorporates(exprCondition, keyword, type, args, constraints));
                    }
                while (match(Id.COMMA) != null);
                fAny = true;
                }
            else if ((keyword = match(Id.INTO)) != null)
                {
                compositions.add(new Composition.Into(exprCondition, keyword, parseTypeExpression()));
                fAny = true;
                }
            else // not context sensitive keywords
                {
                switch (peek().getId())
                    {
                    case EXTENDS:
                        {
                                         keyword = expect(Id.EXTENDS);
                        TypeExpression   type    = parseTypeExpression();
                        List<Expression> args    = parseArgumentList(false, false, false);
                        compositions.add(new Composition.Extends(exprCondition, keyword, type, args));
                        fAny = true;
                        }
                        break;

                    case IMPORT:
                    case IMPORT_EMBED:
                    case IMPORT_REQ:
                    case IMPORT_WANT:
                    case IMPORT_OPT:
                        {
                                              keyword  = current();
                        List<Token>           names    = parseQualifiedName();
                        NamedTypeExpression   module   = new NamedTypeExpression(null, names, null,
                                null, null, names.get(names.size()-1).getEndPosition());
                        List<VersionOverride> versions = parseVersionRequirement(false);
                        compositions.add(new Composition.Import(exprCondition, keyword, module,
                                versions, getLastMatch().getEndPosition()));
                        fAny = true;
                        }
                        break;

                    case DEFAULT:
                        {
                        keyword = expect(Id.DEFAULT);
                        expect(Id.L_PAREN);
                        compositions.add(new Composition.Default(exprCondition, keyword,
                                parseExpression(), expect(Id.R_PAREN).getEndPosition()));
                        fAny = true;
                        }
                        break;

                    default:
                        return fAny;
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
                List<Expression> args = parseArgumentList(false, false, false);

                StatementBlock body = null;
                if (match(Id.L_CURLY) != null)
                    {
                    Token tokLCurly2 = getLastMatch();
                    body = new StatementBlock(parseTypeCompositionComponents(null, new ArrayList<>(), false),
                            tokLCurly2.getStartPosition(), getLastMatch().getEndPosition());
                    }

                long lEndPos = getLastMatch().getEndPosition();

                stmts.add(new TypeCompositionStatement(annotations, name, typeParams, args, body,
                        doc, lStartPos, lEndPos));
                }
            while (match(Id.COMMA) != null);

            if (peek().getId() != Id.R_CURLY)
                {
                expect(Id.SEMICOLON);
                }
            }

        return new StatementBlock(parseTypeCompositionComponents(null, stmts, false),
                tokLCurly.getStartPosition(), getLastMatch().getEndPosition());
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
        NextComponent: while (match(Id.R_CURLY) == null)
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

                case IF:
                    {
                    Token tokIf = expect(Id.IF);
                    expect(Id.L_PAREN);
                    Expression exprIf = parseCondition();
                    expect(Id.R_PAREN);

                    // then ...
                    expect(Id.L_CURLY);
                    Token tokAnd = null;
                    if (exprCondition == null)
                        {
                        parseTypeCompositionComponents(exprIf, stmts, fFileLevel);
                        }
                    else
                        {
                        tokAnd = new Token(tokIf.getStartPosition(), tokIf.getEndPosition(), Id.COND_AND);
                        parseTypeCompositionComponents(new CmpExpression(exprCondition, tokAnd, exprIf), stmts, fFileLevel);
                        }
                    // the '}' is eaten by the recursive call to parseTypeCompositionComponents

                    // else ...
                    Token tokElse = match(Id.ELSE);
                    if (tokElse != null)
                        {
                        Token      tokNot   = new Token(tokElse.getStartPosition(), tokElse.getEndPosition(), Id.NOT);
                        Expression exprElse = new PrefixExpression(tokNot, exprIf);
                        boolean    fElseIf  = peek().getId() == Id.IF;
                        if (!fElseIf)
                            {
                            expect(Id.L_CURLY);
                            }
                        if (exprCondition == null)
                            {
                            parseTypeCompositionComponents(exprElse, stmts, fFileLevel);
                            }
                        else
                            {
                            parseTypeCompositionComponents(new CmpExpression(exprCondition, tokAnd, exprElse), stmts, fFileLevel);
                            }
                        // the '}' is eaten by the recursive call to parseTypeCompositionComponents

                        // TODO what about "else if" (the else won't have a '}')
//                        if (!fElseIf)
//                            {
//                            expect(Id.R_CURLY);
//                            }
                        }

                    // there is no "if statement" per se; instead, the expression was simply pushed
                    // down to any type composition components that were encountered inside the if
                    continue NextComponent;
                    }

                default:
                    {
                    Token start = peek();

                    stmt = parseTypeCompositionComponent(exprCondition, false);
                    fFoundType = true;

                    if (fFileLevel)
                        {
                        // module cannot have any statements before it in the source
                        if (stmt instanceof TypeCompositionStatement)
                            {
                            if (((TypeCompositionStatement) stmt).getCategory().getId() == Id.MODULE
                                    && !stmts.isEmpty())
                                {
                                log(Severity.ERROR, MODULE_NOT_ROOT, start.getStartPosition(),
                                        start.getEndPosition());
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
        List<Token>      modifiers   = null;
        List<Annotation> annotations = null;
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
                    for (Annotation annotation : annotations)
                        {
                        annotation.log(m_errorListener, Severity.ERROR, Compiler.ANNOTATION_UNEXPECTED);
                        }
                    }

                // evaluate modifers looking for an access modifier
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
                                modifier.log(m_errorListener, m_source, Severity.ERROR, Compiler.KEYWORD_UNEXPECTED);
                                break;
                            }
                        }
                    }

                return parseTypeDefStatement(exprCondition, tokAccess);

            case L_PAREN:
                {
                // it's a property or a method, but the property type is parenthesized (odd!) or
                // the method return types are parenthesized (multi-return), which means we have to
                // either find a list of types inside the parenthesis, or we have to go
                // past the name and find a '<' or '(' to know it's a method. additionally, if
                // we're inside a method, it could be a multi-assignment (tuple result), a
                // variable declaration starting with a parenthesized type (odd!), or just an
                // expression. start by assuming it's just a list of expressions (we can always
                // change them into types later), and switch to a statement mode the first time
                // that we find something that could be a type declaration (i.e. some instance of
                // "expression expression" instead of "expression ,")
                List<TypeExpression> returns = null;

                if (fInMethod && modifiers == null && annotations == null)
                    {
                    Mark             mark  = mark();
                    List<Expression> exprs = new ArrayList<>();
                    List<Statement>  stmts = null;

                    // eliminate the possibility of a multi-assignment first, because it is the most
                    // insanely complicated possibility
                    Token start = expect(Id.L_PAREN);
                    ParseList: while (true)
                        {
                        Expression expr = parseExpression();
                        exprs.add(expr);

                        if (peek().getId() != Id.COMMA && peek().getId() != Id.R_PAREN)
                            {
                            // so there appears to be a second expression to parse, which indicates
                            // that the first one had to be a type
                            if (stmts == null)
                                {
                                // build a statements list to match the expressions list (except for
                                // the last expression)
                                stmts = new ArrayList<>();
                                for (int i = 0, c = exprs.size() - 1; i < c; ++i)
                                    {
                                    stmts.add(new ExpressionStatement(exprs.get(i), false));
                                    }
                                }

                            // create a statement that represents a variable declaration
                            stmts.add(new VariableDeclarationStatement(expr.toTypeExpression(),
                                    expect(Id.IDENTIFIER), null, null));
                            }
                        else
                            {
                            // this one was just an expression, but previous ones were more than
                            // that (i.e. at least one created a statement), so we need to keep the
                            // list of statements in sync with the list of expressions (these will
                            // eventually all be used as "l-values" for an assignment)
                            if (stmts != null)
                                {
                                stmts.add(new ExpressionStatement(expr, false));
                                }
                            }

                        // check if we're all done with the list
                        if (match(Id.R_PAREN) != null)
                            {
                            break ParseList;
                            }

                        expect(Id.COMMA);
                        }

                    // peek ahead to see if this is an multi-assignment that we didn't see coming
                    if (peek().getId() == Id.ASN && stmts == null)
                        {
                        // even though there were no new variable declarations, it does appear to
                        // be a multi-declaration/assignment
                        stmts = new ArrayList<>();
                        for (Expression expr : exprs)
                            {
                            stmts.add(new ExpressionStatement(expr, false));
                            }
                        }

                    // at this point, if there are statements, then it has to be a
                    // multi-declaration/assignment
                    if (stmts != null)
                        {
                        if (stmts.size() <= 1)
                            {
                            log(Severity.ERROR, NOT_MULTI_ASN, start.getStartPosition(), peek().getEndPosition());
                            throw new CompilerException("multi assignment has only " + stmts.size() + " l-values");
                            }

                        expect(Id.ASN);
                        Expression value = parseExpression();
                        expect(Id.SEMICOLON);
                        return new MultipleDeclarationStatement(stmts, value, start.getStartPosition());
                        }

                    // at this point, all we encountered was a list of expressions inside of
                    // parenthesis:
                    // - if there were multiple expressions, then it has to be either a method
                    //   declaration or an expression statement (for example, a tuple literal)
                    // - if there was only one expression, then it could be an expression statement,
                    //   a type expression for a property or variable declaration (or even a method
                    //   declaration); regardless, it has to be re-parsed as a single exception from
                    //   the beginning, because there must have been a reason to have the
                    //   parenthesis there in the first place
                    if (exprs.size() == 1)
                        {
                        restore(mark);
                        exprs.set(0, parseExpression());
                        }

                    // the method/property/variable declaration statement will have a "name" next;
                    // otherwise, we were wrong, and this was just an expression statement
                    if (peek().getId() == Id.IDENTIFIER)
                        {
                        // the expressions should have been parsed as types
                        returns = new ArrayList<>();
                        for (int i = 0, c = exprs.size(); i < c; ++i)
                            {
                            returns.add(exprs.get(i).toTypeExpression());
                            }
                        }
                    else
                        {
                        // the entire thing should have been parsed as an expression statement
                        Expression expr;
                        if (exprs.size() == 1)
                            {
                            expr = exprs.get(0);
                            }
                        else
                            {
                            restore(mark);
                            expr = parseExpression();
                            }
                        expect(Id.SEMICOLON);
                        return new ExpressionStatement(expr);
                        }
                    }

                // there are two possible paths: parseTypeExpression on the whole thing (because
                // it's the type of a property) or call parseTypeExpression on each item in the
                // parenthesized list (because it's the return types from a method)

                // assume it's a method
                if (returns == null || returns.size() > 0)
                    {
                    if (returns == null)
                        {
                        // look for multiple return types
                        try (SafeLookAhead attempt = new SafeLookAhead())
                            {
                            returns = parseReturnList();
                            if (attempt.isClean() && returns != null && returns.size() > 1)
                                {
                                attempt.keepResults();
                                }
                            else
                                {
                                // we struck out
                                returns = null;
                                }
                            }
                        catch (CompilerException e) {}
                        }

                    if (returns != null)
                        {
                        return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc,
                                modifiers, annotations, null, null, returns, expect(Id.IDENTIFIER));
                        }
                    }

                // if it wasn't a method, re-parse as a property
                assert returns == null || returns.size() == 1;
                TypeExpression type = returns == null ? parseTypeExpression() : returns.get(0);
                Token          name = expect(Id.IDENTIFIER);

                // if we're in a method and there is no modifier (i.e. static), then this is a
                // local variable declaration
                if (fInMethod && modifiers == null)
                    {
                    return parseVariableDeclarationAfterName(annotations, type, name);
                    }

                return parsePropertyDeclarationFinish(lStartPos, exprCondition, doc, modifiers, annotations, type, name);
                }

            case COMP_LT:
                {
                // it's definitely a method or a function
                List<Parameter>      typeVars    = parseTypeParameterList(true);
                Token                conditional = match(Id.CONDITIONAL);
                List<TypeExpression> returns     = parseReturnList();
                Token                name        = expect(Id.IDENTIFIER);
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

            case CONDITIONAL:
            case VOID:
            default:
                {
                // method may have a "conditional" return value
                Token conditional = match(Id.CONDITIONAL);

                if (match(Id.VOID) != null)
                    {
                    return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc, modifiers,
                            annotations, null, conditional, Collections.emptyList(), expect(Id.IDENTIFIER));
                    }

                TypeExpression type;
                if (fInMethod && modifiers == null && annotations == null && conditional == null)
                    {
                    Expression expr = parseExpression();

                    Statement stmt = parsePossibleExpressionOrAssignmentStatement(expr);
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

                // it's a constant, property, or method
                Token name = expect(Id.IDENTIFIER);
                if (conditional != null || peek().getId() == Id.COMP_LT || peek().getId() == Id.L_PAREN)
                    {
                    // '<' indicates redundant return type list
                    // '(' indicates parameters
                    return parseMethodDeclarationAfterName(lStartPos, exprCondition, doc, modifiers,
                            annotations, null, conditional, Collections.singletonList(type), name);
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
            case COND_ASN:
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
    Statement parseVariableDeclarationAfterName(List<Annotation> annotations, TypeExpression type, Token name)
        {
        Expression value = null;
        if (match(Id.ASN) != null)
            {
            value = parseExpression();
            }
        expect(Id.SEMICOLON);

        // apply any annotations to the variable type; "@A @B @C T" is "@A of (@B of (@C of T))"
        if (annotations != null)
            {
            for (int i = annotations.size() - 1; i >= 0; --i)
                {
                Annotation annotation = annotations.get(i);
                type = new AnnotatedTypeExpression(annotation, type);
                }
            }

        return new VariableDeclarationStatement(type, name, value);
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
    MethodDeclarationStatement parseMethodDeclarationAfterName(long lStartPos,
            Expression exprCondition, Token doc, List<Token> modifiers, List<Annotation> annotations,
            List<Parameter> typeVars, Token conditional, List<TypeExpression> returns, Token name)
        {
        List<TypeExpression> redundantReturns = parseTypeParameterTypeList(false);
        List<Parameter>      params           = parseParameterList(true);
        long                 lEndPos          = getLastMatch().getEndPosition();
        StatementBlock       body             = match(Id.SEMICOLON) == null ? parseStatementBlock() : null;
        StatementBlock       stmtFinally      = null;

        if (body != null)
            {
            // check for "constructor finally" block
            if (name.getId() == Id.CONSTRUCT && match(Id.FINALLY) != null)
                {
                stmtFinally = parseStatementBlock();
                lEndPos = stmtFinally.getEndPosition();
                }
            else
                {
                lEndPos = body.getEndPosition();
                }
            }

        return new MethodDeclarationStatement(lStartPos, lEndPos, exprCondition, modifiers, annotations,
                typeVars, conditional, returns, name, redundantReturns, params, body, stmtFinally, doc);
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
            List<Annotation> annotations, TypeExpression type, Token name)
        {
        Expression     value   = null;
        StatementBlock body    = null;
        long           lEndPos;
        if (match(Id.ASN) != null)
            {
            // "=" Expression ";"
            value   = parseExpression();
            lEndPos = value.getEndPosition();
            expect(Id.SEMICOLON);
            }
        else if (match(Id.DOT) != null)
            {
            // "." Name Parameters MethodBody
            Token                      methodName = expect(Id.IDENTIFIER);
            List<Parameter>            params     = parseParameterList(true);
            StatementBlock             block      = parseStatementBlock();
            MethodDeclarationStatement method     = new MethodDeclarationStatement(
                    methodName.getStartPosition(), block.getEndPosition(), null, null, null, null,
                    null, null, methodName, null, params, block, null, null);
            body    = new StatementBlock(Collections.singletonList(method),
                    method.getStartPosition(), method.getEndPosition());
            lEndPos = body.getEndPosition();
            }
        else if (peek().getId() == Id.L_CURLY)
            {
            // pretend we're parsing a class (use the property name token as the basis)
            body    = parseTypeCompositionBody(new Token(name.getStartPosition(),
                                               name.getEndPosition(), Id.CLASS));
            lEndPos = body.getEndPosition();
            }
        else
            {
            lEndPos = getLastMatch().getEndPosition();
            expect(Id.SEMICOLON);
            }

        return new PropertyDeclarationStatement(lStartPos, lEndPos, exprCondition,
                modifiers, annotations, type, name, value, body, doc);
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

        return new StatementBlock(stmts, tokStart.getStartPosition(), getLastMatch().getEndPosition());
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
     *     AssertInstruction expression ";"
     *
     * AssertInstruction
     *     "assert"
     *     "assert:once"
     *     "assert:test"
     *     "assert:debug"
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
            case ASSERT_ALL:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                return parseAssertStatement();

            case BREAK:
            case CONTINUE:
                {
                Token keyword = current();
                Token name    = match(Id.IDENTIFIER);
                expect(Id.SEMICOLON);
                return new ShortCircuitStatement(keyword, name);
                }

            case DEBUG:
                {
                Token keyword = current();
                expect(Id.SEMICOLON);
                return new DebugStatement(keyword);
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
                if (match(Id.COLON) != null)
                    {
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
     *     AssertInstruction expression ";"
     *
     * AssertInstruction
     *     "assert"
     *     "assert:always"
     *     "assert:once"
     *     "assert:test"
     *     "assert:debug"
     * </pre></code>
     *
     * @return an "assert" statement
     */
    Statement parseAssertStatement()
        {
        Token keyword = current();

        Statement cond = null;
        if (peek().getId() != Id.SEMICOLON)
            {
            cond = parseConditionalDeclaration(false);
            }

        expect(Id.SEMICOLON);
        return new AssertStatement(keyword, cond);
        }

    /**
     * Parse a "do" statement.
     *
     * <p/><code><pre>
     * DoStatement
     *     "do" StatementBlock "while" "(" ConditionalDeclaration-opt Expression ")" ";"
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
        ConditionalStatement cond = parseConditionalDeclaration(false);
        long lEndPos = expect(Id.R_PAREN).getEndPosition();
        expect(Id.SEMICOLON);
        return new WhileStatement(keyword, cond, block, lEndPos);
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
     *     ConditionalDeclarationList
     *
     * ConditionalDeclarationList
     *     ConditionalDeclaration Expression
     *     ConditionalDeclarationList "," ConditionalDeclaration Expression
     *
     * VariableInitializationList
     *     VariableInitializer
     *     VariableInitializationList "," VariableInitializer
     *
     * VariableInitializer
     *     TypeExpression-opt Name VariableInitializerFinish
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

        // figure out if we're parsing for the conditional declaration or the three-part "for"
        List<Statement> init;
        if (peek().getId() != Id.SEMICOLON)
            {
            init = new ArrayList<>();
            int cConds = 0;
            do
                {
                long      lPos = peek().getStartPosition();
                Statement stmt = parseConditionalDeclaration(true);
                init.add(stmt);

                if (stmt instanceof AssignmentStatement
                            && ((AssignmentStatement) stmt).isConditional() ||
                    stmt instanceof VariableDeclarationStatement
                            && ((VariableDeclarationStatement) stmt).isConditional())
                    {
                    ++cConds;
                    }

                // all need to be of the same type: all conditional, or none conditional
                if (cConds != 0 && cConds != init.size())
                    {
                    log(Severity.ERROR, ALL_OR_NO_CONDS, lPos, peek().getStartPosition());
                    }
                }
            while (match(Id.COMMA) != null);

            if (match(Id.R_PAREN) != null)
                {
                // this is the "for (var : iterable) {...}" style
                return new ForStatement2(keyword, init, parseStatementBlock());
                }
            }
        else
            {
            init = Collections.EMPTY_LIST;
            }

        // parse the second part
        expect(Id.SEMICOLON);
        Expression expr = (peek().getId() == Id.SEMICOLON) ? null : parseExpression();
        expect(Id.SEMICOLON);

        // parse the third part
        List<Statement> update = new ArrayList<>();
        while (peek().getId() != Id.R_PAREN)
            {
            if (!update.isEmpty())
                {
                expect(Id.COMMA);
                }

            Expression exprUpdate = parseExpression();
            switch (peek().getId())
                {
                case R_PAREN:
                case COMMA:
                    update.add(new ExpressionStatement(exprUpdate, false));
                    break;

                case COND_ASN:
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
                    update.add(new AssignmentStatement(exprUpdate, current(), parseExpression(), false));
                    break;

                default:
                    update.add(new AssignmentStatement(exprUpdate, expect(Id.ASN), parseExpression(), false));
                    break;
                }
            }

        expect(Id.R_PAREN);
        return new ForStatement(keyword, init, expr, update, parseStatementBlock());
        }

    /**
     * Parse an "if" statement.
     *
     * <p/><code><pre>
     * IfStatement
     *     "if" "(" ConditionalDeclaration-opt Expression ")" StatementBlock ElseStatement-opt
     *
     * ConditionalDeclaration
     *     TypeExpression-opt Name ":"
     *
     * ElseStatement
     *     "else" StatementBlock
     * </pre></code>
     *
     * @return an "if" statement
     */
    Statement parseIfStatement()
        {
        Token keyword = expect(Id.IF);
        expect(Id.L_PAREN);
        ConditionalStatement cond = parseConditionalDeclaration(false);
        expect(Id.R_PAREN);
        StatementBlock block = parseStatementBlock();

        if (match(Id.ELSE) == null)
            {
            return new IfStatement(keyword, cond, block);
            }
        else
            {
            Statement stmtElse = peek().getId() == Id.IF ? parseIfStatement() : parseStatementBlock();
            return new IfStatement(keyword, cond, block, stmtElse);
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
    ImportStatement parseImportStatement(Expression exprCond)
        {
        List<Token> qualifiedName = new ArrayList<>();
        Token       simpleName    = null;
        Token       keyword       = expect(Id.IMPORT);

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
     *     switch "(" SwitchCondition-opt ")" "{" SwitchBlocks-opt SwitchLabels-opt "}"
     *
     * SwitchCondition
     *     VariableInitializer
     *     Expression
     *
     * SwitchBlocks
     *     SwitchBlock
     *     SwitchBlocks SwitchBlock
     *
     * SwitchBlock
     *     SwitchLabels Statements
     *
     * SwitchLabels
     *     SwitchLabel
     *     SwitchLabels SwitchLabel
     *
     * # expression must be a "constant expression", i.e. compiler has to be able to determine the value
     * SwitchLabel
     *     "case" Expression ":"
     *     "default" ":"
     * </pre></code>
     *
     * @return a switch statement
     */
    Statement parseSwitchStatement()
        {
        Token keyword = expect(Id.SWITCH);
        expect(Id.L_PAREN);

        Statement cond = null;
        if (match(Id.R_PAREN) == null)
            {
            // while the switch does not allow a conditional declaration, it does allow all of the
            // other capabilities expressed in a conditional declaration
            cond = parseConditionalDeclaration(true);
            if (cond instanceof VariableDeclarationStatement &&
                    ((VariableDeclarationStatement) cond).isConditional())
                {
                log(Severity.ERROR, NO_CONDITIONAL, keyword.getStartPosition(),
                        peek().getEndPosition());
                throw new CompilerException("conditional is not allowed");
                }
            expect(Id.R_PAREN);
            }

        Token tokLCurly = expect(Id.L_CURLY);
        List<Statement> stmts = new ArrayList<>();
        while (true)
            {
            switch (peek().getId())
                {
                case CASE:
                    stmts.add(new CaseStatement(current(), parseTernaryExpressionList(), expect(Id.COLON)));
                    break;

                case DEFAULT:
                    stmts.add(new CaseStatement(current(), null, expect(Id.COLON)));
                    break;

                default:
                    if (stmts.isEmpty())
                        {
                        log(Severity.ERROR, MISSING_CASE, peek().getStartPosition(), peek().getEndPosition());
                        throw new CompilerException("switch must start with a case");
                        }
                    stmts.add(parseStatement());
                    break;

                case R_CURLY:
                    Token tokRCurly = expect(Id.R_CURLY);
                    return new SwitchStatement(keyword, cond, new StatementBlock(stmts,
                            tokLCurly.getStartPosition(), tokRCurly.getEndPosition()));
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
        List<Statement> resources = null;
        if (match(Id.L_PAREN) != null)
            {
            resources = parseVariableInitializationList(true);
            expect(Id.R_PAREN);
            }

        StatementBlock block = parseStatementBlock();

        List<CatchStatement> catches = new ArrayList<>();
        while (match(Id.CATCH) != null)
            {
            long lStartPos = getLastMatch().getStartPosition();
            expect(Id.L_PAREN);
            VariableDeclarationStatement var = new VariableDeclarationStatement(
                    parseTypeExpression(), expect(Id.IDENTIFIER), null, null);
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

        TypeExpression type = parseTypeExpression();

        // optional "as" keyword
        match(Id.AS);

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
        List<Statement> resources = parseVariableInitializationList(true);
        expect(Id.R_PAREN);
        return new TryStatement(keyword, resources, parseStatementBlock(), null, null);
        }

    /**
     * Parse a "while" statement.
     *
     * <p/><code><pre>
     * WhileStatement
     *     "while" "(" ConditionalDeclaration-opt Expression ")" StatementBlock
     * </pre></code>
     *
     * @return a "while" statement
     */
    Statement parseWhileStatement()
        {
        Token keyword = expect(Id.WHILE);
        expect(Id.L_PAREN);
        ConditionalStatement cond = parseConditionalDeclaration(false);
        expect(Id.R_PAREN);
        StatementBlock block = parseStatementBlock();
        return new WhileStatement(keyword, cond, block);
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
     * @param required  true iff at least one VariableInitializer is required
     *
     * @return a statement representing the variable initializer
     */
    List<Statement> parseVariableInitializationList(boolean required)
        {
        if (!required)
            {
            switch (peek().getId())
                {
                case COMMA:
                case SEMICOLON:
                case R_PAREN:
                    return null;
                }
            }

        List<Statement> list = new ArrayList<>();
        list.add(parseVariableInitializer());
        while (match(Id.COMMA) != null)
            {
            list.add(parseVariableInitializer());
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
     * @return a statement representing the variable initializer
     */
    Statement parseVariableInitializer()
        {
        Expression expr = parseExpression();
        if (peek().getId() == Id.ASN)
            {
            return new AssignmentStatement(expr, current(), parseExpression(), false);
            }

        TypeExpression type = expr.toTypeExpression();
        Token name = expect(Id.IDENTIFIER);
        return new VariableDeclarationStatement(type, name, expect(Id.ASN), parseExpression());
        }

    /**
     * Parse a ConditionalDeclaration, followed by an expression; use VariableDeclarationStatement
     * or AssignmentStatement to represent the conditional declaration and assignment.
     * <p/>
     * If it turns out that there isn't a ConditionalDeclaration, then parse what is found as a
     * VariableDeclarationStatement, an AssignmentStatement, or an ExpressionStatement, based on
     * what is found.
     *
     * <p/><code><pre>
     * ConditionalDeclaration
     *     TypeExpression-opt Name ":"
     * </pre></code>
     *
     * @param fAllowAsn  allow assignment operator
     *
     * @return
     */
    ConditionalStatement parseConditionalDeclaration(boolean fAllowAsn)
        {
        Expression expr = parseExpression();
        switch (peek().getId())
            {
            case ASN:
                if (!fAllowAsn)
                    {
                    log(Severity.ERROR, NO_ASSIGNMENT, peek().getStartPosition(), peek().getEndPosition());
                    throw new CompilerException("assignment disallowed");
                    }
                // fall through
            case COLON:
                // it's an assignment or conditional expression, but it doesn't declare a new var
                return new AssignmentStatement(expr, current(), parseExpression(), false);

            case COMMA:     // TODO could indicate multiple declaration/assignment
            case SEMICOLON:
            case R_PAREN:
                // definitely stop if there is ',', ';', or ')'
                // it's not a conditional declaration; it's just an expression
                return new ExpressionStatement(expr, false);

            default:
                break;
            }

        TypeExpression type = expr.toTypeExpression();
        Token name = expect(Id.IDENTIFIER);

        // could be either ':' or '='
        // TODO an Id.COMMA would indicate multiple declaration/assignment
        Token op = fAllowAsn ? match(Id.ASN) : null;
        if (op == null)
            {
            op = expect(Id.COLON);
            }

        return new VariableDeclarationStatement(type, name, op, parseExpression());
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
    Expression parseCondition()
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
            expr = new ElseExpression(expr, current(), parseExpression());
            }
        return expr;
        }

    /**
     * Parse an expression list, but one that does not look for a trailing ':'.
     *
     * <p/><code><pre>
     * TernaryExpressionList:
     *     TernaryExpression
     *     TernaryExpressionList "," TernaryExpression
     * </pre></code>
     *
     * @return
     */
    List<Expression> parseTernaryExpressionList()
        {
        Expression expr = parseTernaryExpression();
        if (peek().getId() != Id.COMMA)
            {
            return Collections.singletonList(expr);
            }

        ArrayList<Expression> list = new ArrayList<>();
        list.add(expr);
        while (match(Id.COMMA) != null)
            {
            list.add(parseTernaryExpression());
            }

        return list;
        }

    /**
     * Parse a ternary expression, which is the "a ? b : c" expression.
     *
     * <p/><code><pre>
     * TernaryExpression
     *     ElvisExpression
     *     ElvisExpression Whitespace "?" ElvisExpression ":" TernaryExpression
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
            Expression exprThen = parseElvisExpression();
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
            expr = new ElseExpression(expr, current(), parseElvisExpression());
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
            expr = new CondOpExpression(expr, current(), parseAndExpression());
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
            expr = new CondOpExpression(expr, current(), parseBitOrExpression());
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
            expr = new RelOpExpression(expr, current(), parseBitXorExpression());
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
            expr = new RelOpExpression(expr, current(), parseBitAndExpression());
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
            expr = new RelOpExpression(expr, current(), parseEqualityExpression());
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
            expr = new CmpExpression(expr, current(), parseRelationalExpression());
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
                    expr = new CmpExpression(expr, current(), parseRangeExpression());
                    break;

                case AS:
                case IS:
                    expr = new AsExpression(expr, current(), parseTypeExpression());
                    break;

                case INSTANCEOF:
                    expr = new IsExpression(expr, current(), parseTypeExpression());
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
            expr = new RelOpExpression(expr, current(), parseShiftExpression());
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
                    expr = new RelOpExpression(expr, current(), parseRangeExpression());
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
     * @return an expression
     */
    Expression parseAdditiveExpression()
        {
        Expression expr = parseMultiplicativeExpression();
        while (peek().getId() == Id.ADD || peek().getId() == Id.SUB)
            {
            expr = new RelOpExpression(expr, current(), parseMultiplicativeExpression());
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
                    expr = new RelOpExpression(expr, current(), parsePrefixExpression());
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
     *     "throw" PrefixExpression
     *     "++" PrefixExpression
     *     "--" PrefixExpression
     *     "+" PrefixExpression
     *     "-" PrefixExpression
     *     "!" PrefixExpression
     *     "~" PrefixExpression
     *     "&" PrefixExpression
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
                return new PrefixExpression(current(), parsePrefixExpression());

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
     *     PostfixExpression ".new" ArgumentList
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
                            Token            keyword = expect(Id.NEW);
                            TypeExpression   type    = parseTypeExpression();
                            long             lEndPos = type.getEndPosition();
                            List<Expression> args    = Collections.EMPTY_LIST;
                            if (!(type instanceof ArrayTypeExpression
                                    && ((ArrayTypeExpression) type).getDimensions() == 0))
                                {
                                args    = parseArgumentList(true, false, true);
                                lEndPos = getLastMatch().getEndPosition();
                                }
                            expr = new NewExpression(expr, keyword, type, args, lEndPos);
                            break;
                            }

                        case AS:
                        case IS:
                        case INSTANCEOF:
                            {
                            Token keyword = current();
                            expect(Id.L_PAREN);
                            expr = keyword.getId() == Id.AS
                                    ? new AsExpression(expr, keyword, parseTypeExpression())
                                    : new IsExpression(expr, keyword, parseTypeExpression());
                            expect(Id.R_PAREN);
                            break;
                            }

                        case BIT_AND:
                        case IDENTIFIER:
                            {
                            Token                noDeRef = match(Id.BIT_AND);
                            Token                name    = expect(Id.IDENTIFIER);
                            long                 lEndPos = name.getEndPosition();
                            List<TypeExpression> params  = null;
                            if (peek().getId() == Id.COMP_LT)
                                {
                                try (SafeLookAhead attempt = new SafeLookAhead())
                                    {
                                    params = parseTypeParameterTypeList(true);
                                    if (attempt.isClean())
                                        {
                                        attempt.keepResults();
                                        lEndPos = getLastMatch().getEndPosition();
                                        }
                                    else
                                        {
                                        params = null;
                                        }
                                    }
                                catch (CompilerException e) {}
                                }
                            expr = new NameExpression(expr, noDeRef, name, params, lEndPos);
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
                    // ArgumentList
                    expr = new InvocationExpression(expr, parseArgumentList(true, true, false),
                            getLastMatch().getEndPosition());
                    break;

                case L_SQUARE:
                    {
                    // ArrayDims
                    // ArrayIndex
                    expect(Id.L_SQUARE);
                    if (match(Id.R_SQUARE) != null)
                        {
                        // "SomeClass[]"
                        expr = new ArrayTypeExpression(expr.toTypeExpression(), 0,
                                getLastMatch().getEndPosition());
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
                        expr = new ArrayTypeExpression(expr.toTypeExpression(), cExplicitDims,
                                getLastMatch().getEndPosition());
                        }
                    else
                        {
                        // "someArray[3]"
                        List<Expression> indexes = parseExpressionList();
                        expect(Id.R_SQUARE);
                        expr = new ArrayAccessExpression(expr, indexes, getLastMatch().getEndPosition());
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
     *     "T0D0" TodoFinish-opt
     *     Literal
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

            case NEW:
                {
                Token            keyword = expect(Id.NEW);
                TypeExpression   type    = parseTypeExpression();
                // we always need arguments, but if the arguments are an empty array indicator,
                // e.g. "new Int[]", then we've already eaten the arguments when we parsed the type
                // expression
                List<Expression> args = type instanceof ArrayTypeExpression &&
                                        ((ArrayTypeExpression) type).getDimensions() == 0
                                      ? Collections.EMPTY_LIST
                                      : parseArgumentList(true, false, true);
                StatementBlock   body = peek().getId() == Id.L_CURLY
                                      ? parseTypeCompositionBody(keyword)
                                      : null ;
                return new NewExpression(keyword, type, args, body, getLastMatch().getEndPosition());
                }

            case THROW:
                return new ThrowExpression(expect(Id.THROW), parseTernaryExpression());

            case TODO:
                return parseTodoExpression();

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
                if (fNormal && peek().getId() == Id.LAMBDA)
                    {
                    return new LambdaExpression(Collections.singletonList(new NameExpression(name)),
                            expect(Id.LAMBDA), parseLambdaBody(), getLastMatch().getStartPosition());
                    }

                // the no-de-ref goes with the construct if there is a construct, not with the name
                Token nameNDR      = construct == null ? amp : null;
                Token constructNDR = construct != null ? amp : null;

                // parse qualified name (which is necessary because we may have to tack on a
                // "construct" to the end)
                long           lEndPos = getLastMatch().getEndPosition();
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
                            parseTypeParameterTypeList(false), lEndPos);

                    // construct is added to the end of the list if it was specified
                    if (construct != null)
                        {
                        expr = new NameExpression(expr, constructNDR, construct, null, lEndPos);
                        }

                    return expr;
                    }

                // test for an access-specified TypeExpression, i.e. ending with :public,
                // :protected, :private, or :struct
                Token access = null;
                if (peek().getId() == Id.COLON)
                    {
                    Token colon = expect(Id.COLON);
                    if (!colon.hasLeadingWhitespace() && !colon.hasTrailingWhitespace())
                        {
                        switch (peek().getId())
                            {
                            case PUBLIC:
                            case PROTECTED:
                            case PRIVATE:
                            case STRUCT:
                                // at this point, this MUST be a type expression
                                access = current();
                                lEndPos = access.getEndPosition();
                                break;
                            }
                        }

                    if (access == null)
                        {
                        putBack(colon);
                        }
                    }

                // test for a non-auto-narrowing modifier ("!")
                Token tokNoNarrow = !peek().hasLeadingWhitespace()
                        ? match(Id.NOT)
                        : null;

                // test to see if there is a type parameter list (implying the expression is a type,
                // or a name of a method specifying "redundant return types"
                List<TypeExpression> params = null;
                if (peek().getId() == Id.COMP_LT)
                    {
                    try (SafeLookAhead attempt = new SafeLookAhead())
                        {
                        params = parseTypeParameterTypeList(true);
                        if (attempt.isClean())
                            {
                            attempt.keepResults();
                            lEndPos = getLastMatch().getEndPosition();
                            }
                        else
                            {
                            params = null;
                            }
                        }
                    catch (CompilerException e) {}
                    }

                // test to see if this is a tuple literal of the form "Tuple:(", or some other
                // type literal of the form "type:{"
                if (peek().getId() == Id.COLON)
                    {
                    Token colon = expect(Id.COLON);
                    if (!colon.hasLeadingWhitespace() && !colon.hasTrailingWhitespace())
                        {
                        switch (peek().getId())
                            {
                            case L_PAREN:
                                if (left != null || !name.getValueText().equals("Tuple"))
                                    {
                                    break;
                                    }
                                // fall through
                            case L_CURLY:
                                return parseCustomLiteral(new NamedTypeExpression(null,
                                        toList(left, name), access, tokNoNarrow, params, lEndPos));

                            case LIT_STRING:
                                if (left == null && (name.getValueText().equals("v")
                                        || name.getValueText().equals("Version")))
                                    {
                                    return parseCustomLiteral(new NamedTypeExpression(
                                            null, Collections.singletonList(name), access,
                                            tokNoNarrow, params, lEndPos));
                                    }
                                break;
                            }
                        }

                    putBack(colon);
                    }

                // note to future self: the reason that we have NameExpression with <params>
                // (which seems almost self-evident to ALWAYS be a type and not a name) is because
                // we have the ability to do this: "String s = o.to<String>();" (redundant return)
                return access == null && tokNoNarrow == null
                        ? new NameExpression(left, null, name, params, lEndPos)
                        : new NamedTypeExpression(null, toList(left, name), access, tokNoNarrow, params, lEndPos);
                }

            case L_PAREN:
                {
                /// this could be a tuple literal, a parenthesized expression, or a lambda
                // expression's parameter list
                Token tokLParen = expect(Id.L_PAREN);
                if (match(Id.R_PAREN) != null)
                    {
                    // void lambda
                    return new LambdaExpression(Collections.EMPTY_LIST, expect(Id.LAMBDA),
                            parseLambdaBody(), tokLParen.getStartPosition());
                    }

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
                            return new LambdaExpression(exprs, expect(Id.LAMBDA),
                                    parseLambdaBody(), tokLParen.getStartPosition());
                            }

                        // it's a Tuple literal
                        return new TupleExpression(null, exprs, tokLParen.getStartPosition(), getLastMatch().getEndPosition());

                    case R_PAREN:
                        // this is either a parenthesized expression or a single parameter for a
                        // lambda (it's not a tuple literal)
                        expect(Id.R_PAREN);
                        if (peek().getId() == Id.LAMBDA)
                            {
                            return new LambdaExpression(Collections.singletonList(expr),
                                    expect(Id.LAMBDA), parseLambdaBody(), tokLParen.getStartPosition());
                            }
                        else
                            {
                            // just a parenthesized expression
                            return expr;
                            }

                    case IDENTIFIER:
                        // it has to be a lambda, because we just parsed an expression (which must
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
                throw new IllegalStateException("TODO statement expression"); // TODO

            case SWITCH:
                throw new IllegalStateException("TODO switch expression"); // TODO

            case L_SQUARE:
                return parseCustomLiteral(null);

            case LIT_CHAR:
            case LIT_STRING:
            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                return new LiteralExpression(current());

            case FUNCTION:
            case IMMUTABLE:
            case AT:
                return parseTypeExpression();
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
        ReturnStatement stmt = new ReturnStatement(fakeReturn, parseElvisExpression());
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
     *     "(" Expression ")"
     * </pre></code>
     *
     * @return a TodoExpression
     */
    TodoExpression parseTodoExpression()
        {
        Expression message  = null;
        Token      keyword  = expect(Id.TODO);
        if (keyword.getValue() == null)
            {
            if (match(Id.L_PAREN) != null)
                {
                message = parseExpression();
                expect(Id.R_PAREN);
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

        return new TodoExpression(keyword, message);
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
     *     "Tuple:(" ExpressionList-opt ")"         // REVIEW drop this odd-man-out support?
     *     "Tuple:{" ExpressionList-opt "}"
     *
     * ListLiteral
     *     "[" ExpressionList-opt "]"
     *     "Sequence:{" ExpressionList-opt "}"
     *     "List:{" ExpressionList-opt "}"
     *     "Array:{" ExpressionList-opt "}"
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
    Expression parseCustomLiteral(TypeExpression type)
        {
        String sType;
        if (type == null)
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
                            log(Severity.ERROR, BAD_HEX_LITERAL, literal.getStartPosition(),
                                    literal.getEndPosition(), Handy.quotedChar(ch));
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

                return new BinaryExpression(ab, lPosStart, lPosEnd, type.getStartPosition(),
                        getLastMatch().getEndPosition());
                }

            case "Array":
            case "Sequence":
            case "List":
                {
                long     lStartPos;
                Token.Id idEnd;
                if (type == null)
                    {
                    lStartPos = expect(Id.L_SQUARE).getStartPosition();
                    idEnd     = Id.R_SQUARE;
                    }
                else
                    {
                    expect(Id.L_CURLY);
                    lStartPos = type.getStartPosition();
                    idEnd     = Id.R_CURLY;
                    }

                List<Expression> exprs = null;
                while (match(idEnd) == null)
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
                return new ListExpression(type, exprs, lStartPos, getLastMatch().getEndPosition());
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
                return new MapExpression(type, keys, values, getLastMatch().getEndPosition());
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
                    else
                        {
                        expect(Id.COMMA);
                        }
                    exprs.add(parseExpression());
                    }
                return new TupleExpression(type, exprs, type.getStartPosition(),
                        getLastMatch().getEndPosition());
                }

            case "Version":
            case "v":
                {
                Token   tokLit = peek();
                Version ver    = parseVersion(true);
                return new VersionExpression(tokLit, ver, type.getStartPosition());
                }

            default:
                {
                Token      open  = expect(Id.L_CURLY);
                Expression expr  = parseExpression();
                Token      close = expect(Id.R_CURLY);
                // custom type parsing is not supported in the prototype compiler
                log(Severity.ERROR, BAD_CUSTOM, open.getStartPosition(), close.getEndPosition(), type, expr);
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
     * Parse a type expression of the form "Type + Type" or "Type - Type".
     *
     * <p/><code><pre>
     * UnionedTypeExpression
     *     IntersectingTypeExpression
     *     UnionedTypeExpression + IntersectingTypeExpression
     *     UnionedTypeExpression - IntersectingTypeExpression
     * </pre></code>
     *
     * @return a type expression
     */
    TypeExpression parseUnionedTypeExpression()
        {
        TypeExpression expr = parseIntersectingTypeExpression();
        Token tokOp;
        do
            {
            if ((tokOp = match(Id.ADD)) != null || (tokOp = match(Id.SUB)) != null)
                {
                expr = new BiTypeExpression(expr, tokOp, parseIntersectingTypeExpression());
                }
            }
        while (tokOp != null);
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
     *     NonBiTypeExpression ArrayDims
     *     NonBiTypeExpression ArrayIndexes
     *     NonBiTypeExpression "..."
     *     "conditional" NonBiTypeExpression
     *     "immutable" NonBiTypeExpression
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
                    long lEndPos = getLastMatch().getEndPosition();
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

                case ELLIPSIS:
                    type = new SequenceTypeExpression(type, expect(Id.ELLIPSIS));
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
            putBack(name);
            }

        return new FunctionTypeExpression(function, listReturn, listParam, getLastMatch().getEndPosition());
        }

    /**
     * Parse a type expression in the form:
     *
     *   "immutable name.name.name!<param, param>"
     *
     * <p/><code><pre>
     * NamedTypeExpression
     *     QualifiedName TypeAccessModifier-opt NoAutoNarrowModifier-opt TypeParameterTypeList-opt
     *
     * TypeAccessModifier
     *     NoWhitespace ":" NoWhitespace AccessModifier
     *
     * NoAutoNarrowModifier
     *     NoWhitespace "!"
     *
     * </pre></code>
     *
     * @return a NamedTypeExpression
     */
    NamedTypeExpression parseNamedTypeExpression()
        {
        Token immutable = match(Id.IMMUTABLE);

        // QualifiedName
        List<Token> names = parseQualifiedName();

        // TypeAccessModifier
        Token tokAccess = null;
        Token tokNext   = peek();
        if (tokNext.getId() == Id.COLON && !tokNext.hasLeadingWhitespace() && !tokNext.hasTrailingWhitespace())
            {
            Token tokColon = current();
            switch ((tokNext = peek()).getId())
                {
                case PUBLIC:
                case PROTECTED:
                case PRIVATE:
                case STRUCT:
                    // use expect() to make sure that getLastMatch() is set correctly
                    tokAccess = expect(tokNext.getId());
                    break;

                default:
                    putBack(tokColon);
                    break;
                }
            }

        // NoAutoNarrowModifier
        Token tokNarrow = !peek().hasLeadingWhitespace()
                ? match(Id.NOT)
                : null;

        // TypeParameterTypeList
        List<TypeExpression> params = parseTypeParameterTypeList(false);

        return new NamedTypeExpression(immutable, names, tokAccess, tokNarrow, params, getLastMatch().getEndPosition());
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
        List<Token>      modifiers   = null;
        List<Annotation> annotations = null;
        boolean          err         = false;
        Token            access      = null;
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
    Annotation parseAnnotation(boolean required)
        {
        long lStartPos = peek().getStartPosition();
        if (match(Id.AT, required) == null)
            {
            return null;
            }

        // while the annotation is technically a named type expression, it only allows a qualified
        // name (and none of the other things that are normally allowed in a named type expression)
        NamedTypeExpression type = new NamedTypeExpression(null, parseQualifiedName(),
                null, null, null, getLastMatch().getEndPosition());

        // a trailing argument list is only assumed to be part of the annotation if there is
        // no whitespace separating the annotation from the arguments
        List<Expression> args = null;
        Token token = peek();
        if (token != null && token.getId() == Id.L_PAREN && !token.hasLeadingWhitespace())
            {
            args = parseArgumentList(true, false, false);
            }

        long lEndPos = args == null ? type.getEndPosition() : getLastMatch().getEndPosition();
        return new Annotation(type, args, lStartPos, lEndPos);
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
            types = peek().getId() == Id.COMP_GT
                    ? Collections.EMPTY_LIST
                    : parseTypeExpressionList();
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

        List<Expression> args = null;
        args = new ArrayList<>();
        boolean first = true;
        while (match(idClose) == null)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                expect(Id.COMMA);
                }

            Token label = null;
            if (!fArray)
                {
                // special case where the parameter names are being specified with the arguments
                if (peek().getId() == Id.IDENTIFIER)
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
                    case COND:
                        {
                        Token tokUnbound = match(Id.COND);
                        expr = new NonBindingExpression(tokUnbound.getStartPosition(),
                                tokUnbound.getEndPosition(), null);
                        }
                        break;

                    case COMP_LT:
                        {
                        Token          tokOpen    = match(Id.COMP_LT);
                        TypeExpression type       = parseTypeExpression();
                        Token          tokClose   = match(Id.COMP_GT);
                        Token          tokUnbound = match(Id.COND);
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

        return args;
        }

    /**
     * Parse a declared list of return types.
     *
     * <p/><code><pre>
     * ReturnList
     *     "void"
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
        if (match(Id.VOID) != null)
            {
            listReturn = Collections.EMPTY_LIST;
            }
        else if (match(Id.L_PAREN) == null)
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
     *
     * # note: the StringLiteral must contain a VersionString
     * Version
     *     "v:" NoWhitespace StringLiteral
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
     * @param required  true if a version requirement must be present next in the stream of tokens
     *
     * @return a list of version overrides, or null if no version requirement is encountered
     */
    List<VersionOverride> parseVersionRequirement(boolean required)
        {
        // start with the initial version requirement (no preceding verb)
        VersionExpression exprVer = parseVersionLiteral(required);
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
                            getLastMatch().getEndPosition(), ver.toString());
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
     * # note: the StringLiteral must contain a VersionString
     * Version
     *     "v" ":" StringLiteral
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
     * @param required  true if a version requirement must be present next in the stream of tokens
     *
     * @return a VersionExpression
     */
    VersionExpression parseVersionLiteral(boolean required)
        {
        Token tokPrefix = match(Id.IDENTIFIER, required);
        if (tokPrefix == null)
            {
            return null;
            }

        String sName = (String) tokPrefix.getValue();
        if ((sName.equals("v") || sName.equals("Version")) && !tokPrefix.hasTrailingWhitespace()
                && !expect(Id.COLON).hasTrailingWhitespace())
            {
            return new VersionExpression(peek(), parseVersion(true), tokPrefix.getStartPosition());
            }
        else
            {
            log(Severity.ERROR, BAD_VERSION, tokPrefix.getStartPosition(), peek().getEndPosition());
            throw new CompilerException("version literal");
            }
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
        Token token = match(Id.LIT_STRING, required);
        if (token != null)
            {
            String[] parts = parseDelimitedString((String) token.getValue(), '.');
            for (int i = 0, c = parts.length; i < c; ++i)
                {
                // each of the parts has to be an integer, except for the last which can start with
                // a non-GA designator
                if (!Version.isValidVersionPart(parts[i], i == c-1))
                    {
                    log(Severity.ERROR, BAD_VERSION, token.getStartPosition(), token.getEndPosition());
                    return new Version("0");
                    }
                }

            return new Version((String) token.getValue());
            }

        return null;
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
        return m_tokenPutBack == null ? m_token : m_tokenPutBack;
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

        log(Severity.ERROR, UNEXPECTED_EOF, m_source.getPosition(), m_source.getPosition());
        throw new CompilerException("unexpected EOF");
        }

    protected void putBack(Token token)
        {
        assert m_tokenPutBack == null;
        m_tokenPutBack = token;
        }

    private class Mark
        {
        long    pos;
        Token   token;
        Token   putBack;
        Token   doc;
        boolean noRec;
        }

    protected Mark mark()
        {
        Mark mark = new Mark();
        mark.pos     = m_lexer.getPosition();
        mark.token   = m_token;
        mark.putBack = m_tokenPutBack;
        mark.doc     = m_doc;
        mark.noRec   = m_fAvoidRecovery;
        return mark;
        }

    protected void restore(Mark mark)
        {
        m_lexer.setPosition(mark.pos);
        m_token           = mark.token;
        m_tokenPutBack = mark.putBack;
        m_doc             = mark.doc;
        m_fAvoidRecovery  = mark.noRec;
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
            return m_tokenLastMatch = current();
            }

        Token token = m_token;
        if (token.getId() == Id.IDENTIFIER && id.ContextSensitive && token.getValue().equals(id.TEXT))
            {
            // advance to the next token
            next();

            // return the previously "current" token
            return m_tokenLastMatch = token.convertToKeyword();
            }

        token = token.peel(id, m_source);
        if (token != null)
            {
            m_tokenLastMatch = token;
            }
        return token;
        }

    /**
     * Return the most recently matched token.
     *
     * @return the token most recently returned from the match method
     */
    protected Token getLastMatch()
        {
        return m_tokenLastMatch;
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

        log(Severity.ERROR, EXPECTED_TOKEN, m_token.getStartPosition(), m_token.getEndPosition(),
                id, m_token.getId());

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


    // ----- constants ---------------------------------------------------------

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
     * Unsupported custom literal.
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
     * Illegal assert designation.
     */
    public static final String BAD_ASSERT        = "PARSER-10";
    /**
     * Conditional not allowed in a switch statement.
     */
    public static final String NO_CONDITIONAL    = "PARSER-11";
    /**
     * Case statement required first in a switch.
     */
    public static final String MISSING_CASE      = "PARSER-12";
    /**
     * Assignment not allowed.
     */
    public static final String NO_ASSIGNMENT     = "PARSER-13";
    /**
     * Multi-conditional for loop requires all statements be conditional, otherwise no conditionals
     * are allowed.
     */
    public static final String ALL_OR_NO_CONDS   = "PARSER-14";
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
    private Token m_tokenPutBack;

    /**
     * The most recently matched token.
     */
    private Token m_tokenLastMatch;

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