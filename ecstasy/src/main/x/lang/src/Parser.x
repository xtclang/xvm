import ecstasy.Markable;

import io.TextPosition;

import Lexer.Token;
import Lexer.Id;

import ast.AccessTypeExpression;
import ast.AnnotatedTypeExpression;
import ast.AnnotationExpression;
import ast.ArrayTypeExpression;
import ast.ChildTypeExpression;
import ast.DifferenceTypeExpression;
import ast.Expression;
import ast.FunctionTypeExpression;
import ast.ImmutableTypeExpression;
import ast.ImportStatement;
import ast.IntersectionTypeExpression;
import ast.LabeledExpression;
import ast.LiteralExpression;
import ast.NamedTypeExpression;
import ast.NonBindingExpression;
import ast.NullableTypeExpression;
import ast.Parameter;
import ast.PrefixTypeExpression;
import ast.RelationalTypeExpression;
import ast.SuffixTypeExpression;
import ast.TupleTypeExpression;
import ast.TypeExpression;
import ast.UnaryExpression;
import ast.UnionTypeExpression;


class Parser
        implements Markable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy parser.
     *
     * @param source            the source code to parse
     * @param errs              the error listener (if none is provided, one will be created
     *                          automatically)
     * @param allowModuleNames  pass True if the Parser is being used to parse type names that may
     *                          include explicit module names
     */
    construct(String source, ErrorList? errs = Null, Boolean allowModuleNames = False)
        {
        construct Parser(new Source(source), errs, allowModuleNames);
        }

    /**
     * Construct an Ecstasy parser.
     *
     * @param source            the source code to parse
     * @param errs              the error listener (if none is provided, one will be created
     *                          automatically)
     * @param allowModuleNames  pass True if the Parser is being used to parse type names that may
     *                          include explicit module names
     */
    construct(Source source, ErrorList? errs = Null, Boolean allowModuleNames = False)
        {
        this.source           = source;
        this.errs             = errs ?: new ErrorList(10);
        this.lexer            = new Lexer(source, this.errs, synthesizeEof = True);
        this.allowModuleNames = allowModuleNames;
        }
    finally
        {
        // prime the parser
        advance();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The source code.
     */
    public/private Source source;

    /**
     * The ErrorList into which the Parser collects errors.
     */
    public/private ErrorList errs;

    /**
     * The Lexer.
     */
    protected/private Lexer lexer;

    /**
     * True indicates that the parser exists to parse type names, and those type names can include
     * explicit module names.
     */
    public/private Boolean allowModuleNames;


    // ----- API -----------------------------------------------------------------------------------

    /**
     * Parse an expression.
     *
     * TODO Warning: Not currently implemented!
     *
     * @return an `Expression`
     */
    Expression parseExpression()
        {
        // TODO - temporarily, the parser implementation does not parse expressions other than
        //        constant literals; normally, an parsing would begin with an ElseExpression
        // return parseElseExpression();

        // note: this is all wrong (just temporary)
        switch (peek().id)
            {
            case LeftParen:
                expect(LeftParen);
                Expression expr = parseExpression();
                expect(RightParen);
                return expr;

            case Sub:
                return new UnaryExpression(expect(Sub), parseExpression());

            case LitChar..LitPath:
                return new LiteralExpression(next());

            default:
                // this is obviously temporary, and not correct in the general case
                return parseTypeExpression();
            }
        }

    /**
     * Parse a type expression.
     *
     *     TypeExpression
     *         UnionedTypeExpression
     *
     * @return a `TypeExpression`
     */
    TypeExpression parseTypeExpression()
        {
        return parseUnionedTypeExpression();
        }


    // ----- statement parsing ---------------------------------------------------------------------

    /**
     * Parse an import statement.
     *
     *     ImportStatement
     *         "import" QualifiedName ImportAlias-opt ";"
     *
     *     ImportAlias
     *         "as" Name
     *
     * @return an ImportStatement
     */
    ImportStatement parseImportStatement()
        {
        Token   keyword = expect(Import);
        Token[] names   = parseQualifiedName();
        Token?  alias   = Null;
        if (match(As) != Null)
            {
            alias = expect(Identifier);
            }
        expect(Semicolon);
        return new ImportStatement(keyword, names, alias);
        }


    // ----- expression parsing --------------------------------------------------------------------

    /**
     * Parse an argument list.
     *
     *     ArgumentList
     *         "(" Arguments-opt ")"
     *
     *     Arguments
     *         Argument
     *         Arguments "," Argument
     *
     *     Argument
     *         NamedArgument-opt ArgumentExpression
     *
     *     # note: the "?" argument allows functions to specify arguments that they are NOT binding
     *     ArgumentExpression
     *         "?"
     *         "<" TypeExpression ">" "?"
     *         Expression
     *
     *     NamedArgument
     *         Name "="
     *
     * @param required        pass True iff the parenthesis are required
     * @param allowCurrying   pass True iff the "?" argument and its variations are allowed
     * @param allowArraySize  pass True iff the argument(s) can be inside '[' and ']'
     *
     * @return an array of arguments, or Null if no parenthesis were encountered
     */
    protected Expression[]? parseArgumentList(Boolean required       = False,
                                    Boolean allowCurrying  = False,
                                    Boolean allowArraySize = False)
        {
        Id       close;
        Boolean  array = False;
        switch (peek().id)
            {
            case LeftParen:
                expect(LeftParen);
                close = RightParen;
                break;

            case LeftSquare:
                if (!allowArraySize)
                    {
                    return Null;
                    }

                expect(LeftSquare);
                close = RightSquare;
                array = True;
                break;

            default:
                if (required)
                    {
                    // this generates an error for the missing arguments list
                    expect(LeftParen);
                    }
                return Null;
            }

        Expression[] args = new Expression[];
        Loop: while (match(close) == Null)
            {
            if (!Loop.first)
                {
                expect(Comma);
                }

            Token? label = Null;
            if (!array)
                {
                // special case where the parameter names are being specified with the arguments
                if (Token name ?= match(Identifier))
                    {
                    if (match(Asn) == Null)
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
            if (allowCurrying && !array)
                {
                switch (peek().id)
                    {
                    case Any:
                        Token any = expect(Any);
                        expr = new NonBindingExpression(Null, any.start, any.end);
                        break;

                    case CompareLT:
                        Token          open = expect(CompareLT);
                        TypeExpression type = parseTypeExpression();
                                              expect(CompareGT);
                        Token          any  = expect(Any);
                        expr = new NonBindingExpression(type, open.start, any.end);
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

            args.add(new LabeledExpression(label?, expr) : expr);
            }

        return args;
        }

    /**
     * Parse a declared list of return types.
     *
     *     ReturnList
     *         "void"
     *         SingleReturnList
     *         "(" MultiReturnList ")"
     *
     *     SingleReturnList
     *         TypeExpression
     *
     *     MultiReturnList
     *         MultiReturn
     *         MultiReturnList "," MultiReturn
     *
     *     MultiReturn
     *         TypeExpression Name-opt
     *
     * @return an array of `Parameter`
     */
    protected Parameter[] parseReturnList()
        {
        if (match(Void) != Null)
            {
            return [];
            }

        if (match(LeftParen) == Null)
            {
            return [new Parameter(parseTypeExpression())];
            }

        Parameter[] returns = new Parameter[];
        do
            {
            returns.add(new Parameter(parseTypeExpression(), match(Identifier) ?: match(Any)));
            }
        while (match(Comma) != Null);
        expect(RightParen);
        return returns;
        }


    // ----- type expression parsing ---------------------------------------------------------------

    /**
     * Parse a type expression of the form "Type + Type" or "Type - Type".
     *
     *     UnionedTypeExpression
     *         IntersectingTypeExpression
     *         UnionedTypeExpression + IntersectingTypeExpression
     *         UnionedTypeExpression - IntersectingTypeExpression
     *
     * @return a type expression
     */
    protected TypeExpression parseUnionedTypeExpression()
        {
        TypeExpression expr = parseIntersectingTypeExpression();
        while (True)
            {
            switch (peek().id)
                {
                case Add:
                    expr = new UnionTypeExpression(expr, expect(Add), parseIntersectingTypeExpression());
                    break;

                case Sub:
                    expr = new DifferenceTypeExpression(expr, expect(Sub), parseIntersectingTypeExpression());
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse a type expression of the form "Type | Type".
     *
     *     IntersectingTypeExpression
     *         NonBiTypeExpression
     *         IntersectingTypeExpression | NonBiTypeExpression
     *
     * @return a type expression
     */
    protected TypeExpression parseIntersectingTypeExpression()
        {
        TypeExpression expr = parseNonBiTypeExpression();
        while (True)
            {
            switch (peek().id)
                {
                case BitOr:
                    expr = new IntersectionTypeExpression(expr, expect(BitOr), parseNonBiTypeExpression());
                    break;

                default:
                    return expr;
                }
            }
        }

    /**
     * Parse any type expression that does NOT look like "Type + Type" or "Type | Type".
     *
     *     NonBiTypeExpression
     *         "(" TypeExpression ")"
     *         AnnotatedTypeExpression
     *         NamedTypeExpression
     *         FunctionTypeExpression
     *         NonBiTypeExpression "?"
     *         NonBiTypeExpression ArrayDims
     *         NonBiTypeExpression ArrayIndexes
     *         NonBiTypeExpression "..."
     *         "immutable" NonBiTypeExpression
     *
     *     ArrayDims
     *         "[" DimIndicators-opt "]"
     *
     *     DimIndicators
     *         DimIndicator
     *         DimIndicators "," DimIndicator
     *
     *     DimIndicator
     *         "?"
     *
     *     ArrayIndexes
     *         "[" ExpressionList "]"
     *
     *     ExpressionList
     *         Expression
     *         ExpressionList "," Expression
     *
     * @return a type expression
     */
    protected TypeExpression parseNonBiTypeExpression()
        {
        TypeExpression type;
        switch (peek().id)
            {
            case LeftParen:
                expect(LeftParen);
                type = parseTypeExpression();
                expect(RightParen);
                break;

            case At:
                type = parseAnnotatedTypeExpression();
                break;

            case Function:
                type = parseFunctionTypeExpression();
                break;

            case Immutable:
                type = new ImmutableTypeExpression(expect(Immutable), parseNonBiTypeExpression());
                break;

            default:
                type = parseNamedTypeExpression();
                break;
            }

        while (True)
            {
            switch (peek().id)
                {
                case LeftSquare:
                    // this could be either:
                    //  -> NonBiTypeExpression ArrayDims
                    //  -> NonBiTypeExpression ArrayIndexes
                    // in the case of the ArrayIndexes, we do NOT consume that portion of the
                    // expression; we use it to give us a dimension count, as if it were ArrayDims
                    val mark = mark();

                    expect(LeftSquare);
                    Int dims    = 0;
                    Int indexes = 0;
                    while (match(RightSquare) == Null)
                        {
                        if (dims + indexes > 0)
                            {
                            expect(Comma);
                            }

                        Token dim = peek(); // just for error reporting
                        if (match(Condition) == Null)
                            {
                            parseExpression();
                            if (indexes == 0 && dims > 0)
                                {
                                // just log the first one that deviates
                                log(Error, AllOrNoDims, [], dim.start, dim.end);
                                }
                            ++indexes;
                            }
                        else // we ate the "?"
                            {
                            if (dims == 0 && indexes > 0)
                                {
                                // just log the first one that deviates
                                log(Error, AllOrNoDims, [], dim.start, dim.end);
                                }
                            ++dims;
                            }
                        }
                    type = new ArrayTypeExpression(type, dims + indexes, prev().end);

                    // if there were only indexes, then we need to leave them in place because the
                    // type expression does not consume them
                    if (dims == 0 && indexes > 0)
                        {
                        restore(mark);
                        return type;
                        }
                    break;

                case Condition:
                    if (!peek().spaceBefore)
                        {
                        type = new NullableTypeExpression(type, expect(Condition));
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
     *     AnnotatedTypeExpression
     *         Annotation TypeExpression
     *
     * @return the annotated type expression
     */
    protected AnnotatedTypeExpression parseAnnotatedTypeExpression()
        {
        AnnotationExpression annotation = parseAnnotation(True) ?: assert;
        TypeExpression type = parseTypeExpression();

        return new AnnotatedTypeExpression(annotation, type);
        }

    /**
     * Parse an annotation.
     *
     *     Annotation
     *         "@" NamedTypeExpression ArgumentList-opt
     *
     * @param required  True iff the annotation is required
     *
     * @return an annotation, or Null if no annotation was encountered
     */
    protected AnnotationExpression? parseAnnotation(Boolean required)
        {
        TextPosition start = peek().start;
        if (match(At, required) == Null)
            {
            return Null;
            }

        // while the annotation is technically a named type expression, it only allows a qualified
        // name (and none of the other things that are normally allowed in a named type expression)
        Token[]?     moduleNames = Null;
        Token[]      names       = parseQualifiedName();
        TextPosition end         = prev().end;
        if (allowModuleNames && peek(t -> t.id == Colon && !t.spaceBefore && !t.spaceAfter))
            {
            (moduleNames, names) = parseModuleQualifiedName(names);
            if (moduleNames != Null)
                {
                end = prev().end;
                }
            }

        TypeExpression type = new NamedTypeExpression(moduleNames, names, Null, Null, Null, end);

        // a trailing argument list is only assumed to be part of the annotation if there is
        // no whitespace separating the annotation from the arguments
        Expression[]? args = Null;
        if (peek(t -> t.id == LeftParen && !t.spaceBefore))
            {
            args = parseArgumentList(True, False, False);
            }

        TextPosition endAnno = args == Null ? type.end : prev().end;
        return new AnnotationExpression(type, args, start, endAnno);
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
    protected FunctionTypeExpression parseFunctionTypeExpression()
        {
        Token func = expect(Function);

        // return values
        Parameter[] returns = parseReturnList();

        // see if the parameters precede the name
        TypeExpression[]? params = parseParameterTypeList();

        if (params == Null)
            {
            // name optionally comes before or after the parameters
            Token name = expect(Identifier);
            params = parseParameterTypeList(True);

            // pretend the name is the next token (as if we didn't eat it already)
            putBack(name);
            }

        return new FunctionTypeExpression(func, returns, params?, prev().end) : assert;
        }

    /**
     * Parse a named type expression.
     *
     *     NamedTypeExpression
     *         NamedTypeExpressionPart
     *         NamedTypeExpression '.' Annotations-opt NamedTypeExpressionPart
     *
     *     NamedTypeExpressionPart
     *         QualifiedName TypeAccessModifier-opt NoAutoNarrowModifier-opt TypeParameterTypeList-opt
     *
     *     TypeAccessModifier
     *         NoWhitespace ":" NoWhitespace AccessModifier
     *
     *     NoAutoNarrowModifier
     *         NoWhitespace "!"
     *
     * @return a TypeExpression for the named type
     */
    protected TypeExpression parseNamedTypeExpression()
        {
        Token[]?        moduleNames = Null;
        TypeExpression? parent      = Null;
        TypeExpression  type;
        do
            {
            AnnotationExpression[] annotations = [];
            if (parent != Null && peek(At))
                {
                annotations = new AnnotationExpression[];
                do
                    {
                    // TODO this is not yet implemented in the Java version
                    annotations.add(parseAnnotation(True)?) : assert;
                    }
                while (peek(At));
                }

            // QualifiedName
            Token[] names = parseQualifiedName();
            if (allowModuleNames && parent == Null
                    && peek(t -> t.id == Colon && !t.spaceBefore && !t.spaceAfter))
                {
                (moduleNames, names) = parseModuleQualifiedName(names);
                }

            // TypeAccessModifier
            Token? access = Null;
            if (peek(t -> t.id == Colon && !t.spaceBefore && !t.spaceAfter))
                {
                Token colon = next();
                switch (Id next = peek().id)
                    {
                    case Public:
                    case Protected:
                    case Private:
                    case Struct:
                        access = expect(next);
                        if (parent != Null)
                            {
                            log(Error, NoChildAccess, [access.id.text],  access.start, access.end);
                            }
                        break;

                    default:
                        putBack(colon);
                        break;
                    }
                }

            // NoAutoNarrowModifier
            Token? noNarrow = match(t -> t.id == BoolNot && !t.spaceBefore);
            if (noNarrow != Null && parent != Null)
                {
                log(Error, NoChildNonNarrow, [], noNarrow.start, noNarrow.end);
                }

            // TypeParameterTypeList
            TypeExpression[]? params = parseTypeParameterTypeList();

            type = parent == Null
                    ? new NamedTypeExpression(moduleNames, names, access, noNarrow, params, prev().end)
                    : new ChildTypeExpression(parent, annotations, names, params, prev().end);
            moduleNames = Null;
            parent      = type;
            }
        while (match(Dot) != Null);

        return type;
        }

    /**
     * Parse a list of type expressions.
     *
     *     TypeExpressionList
     *         TypeExpression
     *         TypeExpressionList "," TypeExpression
     *
     * @return an array of TypeExpression
     */
    protected TypeExpression[] parseTypeExpressionList(Boolean noSequence = False)
        {
        TypeExpression[] types = new TypeExpression[];
        Loop: while (True)
            {
            if (!Loop.first && match(Comma) == Null)
                {
                return types;
                }

            if (noSequence || peek().id != CompareLT)
                {
                types.add(parseTypeExpression());
                }
            else
                {
                Token            start = peek();
                TypeExpression[] seq   = parseTypeParameterTypeList(required=True, noSequence=True) ?: assert;
                Token            end   = prev();
                types.add(new TupleTypeExpression(seq, start.start, end.end));
                }
            }
        }

    /**
     * Parse a list of parameter types (without parameter names).
     *
     *     ParameterTypeList
     *         "(" TypeExpressionList-opt ")"
     *
     * @param required  pass True if the parameter list is required (although it may still be empty)
     *
     * @return an array of TypeExpression
     */
    protected TypeExpression[]? parseParameterTypeList(Boolean required = False)
        {
        if (match(LeftParen, required) == Null)
            {
            return Null;
            }

        TypeExpression[] types = peek().id == RightParen
                ? []
                : parseTypeExpressionList(noSequence = True);
        expect(RightParen);
        return types;
        }

    /**
     * For a parameterized type, parse the list of the types of its type parameters. For example,
     * for `Map<String, Int>`, this would parse the `<String, Int>` portion and produce a list of
     * two types: `String, Int`.
     *
     *     TypeParameterTypeList
     *         "<" TypeParameterTypes ">"
     *
     *     TypeParameterTypes
     *         TypeParameterType
     *         TypeParameterTypes "," TypeParameterType
     *
     *     TypeParameterType
     *         TypeExpression
     *
     * @param required    True iff the angle brackets are required
     * @param noSequence  True if a type sequence is not allowed
     *
     * @return an array of zero or more types, or `Null` if there were no angle brackets
     */
    protected TypeExpression[]? parseTypeParameterTypeList(Boolean required = False, Boolean noSequence = False)
        {
        if (match(CompareLT, required) == Null)
            {
            return Null;
            }

        if (match(CompareGT) != Null)
            {
            return [];
            }

        TypeExpression[] types = parseTypeExpressionList(noSequence);
        expect(CompareGT);
        return types;
        }


    // ----- miscellaneous parsing -----------------------------------------------------------------

    /**
     * Parse a dot-delimited list of names.
     *
     *     QualifiedName
     *        Name
     *        QualifiedName "." Name
     *
     * @param required  pass True if the name is required; False if it is optional
     *
     * @return an array of zero or more identifier tokens
     */
    protected Token[] parseQualifiedName(Boolean required = True)
        {
        if (!required && !peek(Identifier))
            {
            return [];
            }

        Token[] names = new Token[];
        do
            {
            names.add(expect(Identifier));
            }
        while (match(Dot) != Null);
        return names;
        }

    /**
     * Parse a possible dot-delimited list of names that follows a dot-delimited list of names that
     * is followed by a colon.
     *
     *     name.name.name:name.name.name
     *                   ^
     *                   next token is the colon
     *
     * @return an array of zero or more identifier tokens
     */
    protected (Token[]? modulesNames, Token[] names) parseModuleQualifiedName(Token[] names)
        {
        assert allowModuleNames && peek(t -> t.id == Colon && !t.spaceBefore && !t.spaceAfter);
        assert names.size > 0;

        // eliminate literal types
        switch (names[names.size-1].valueText)
            {
            case "Array":
            case "List":
            case "Range":
            case "Interval":
            case "Map":
            case "Tuple":
            case "Path":
            case "File":
            case "Directory":
            case "FileStore":
            // the following are already accounted for the by the Lexer, but they are repeated here
            // to avoid generating confusing errors
            case "Date":
            case "Time":
            case "DateTime":
            case "TimeZone":
            case "Duration":
            case "Version":
            case "v":
                return Null, names;
            }

        Token colon = expect(Colon);
        switch (peek().id)
            {
            case Public:
            case Protected:
            case Private:
            case Struct:
                putBack(colon);
                return Null, names;
            }

        Token[] moduleNames = names;
        Token[] localNames  = new Token[];
        if (peek(Identifier))
            {
            do
                {
                localNames.add(expect(Identifier));
                }
            while (match(Dot) != Null);
            }

        return moduleNames, localNames;
        }


    // ----- token handling ------------------------------------------------------------------------

    /**
     * The next token (unless there is a back token, in which case it takes precedence).
     */
    protected/private Token? nextToken;

    /**
     * The token previously returned.
     */
    protected/private Token? prevToken;

    /**
     * A token that has been "put back".
     */
    protected/private Token? backToken;

    /**
     * The most recent documentation comment encountered.
     */
    protected/private Token? prevDoc;

    /**
     * An indicator that recovering from a parser error has been determined to be undesirable.
     */
    protected/private Boolean suppressRecovery;

    /**
     * True iff the token stream is exhausted.
     */
    public Boolean eof.get()
        {
        return (nextToken?.id == EndOfFile : True) && (backToken?.id == EndOfFile : True);
        }

    /**
     * Advance the parser to the next token. (The first time that this is called, it primes the
     * parser by advancing to the first token.)
     */
    protected Token? advance()
        {
        if (Token result ?= backToken)
            {
            backToken = Null;
            return result;
            }

        Token? result = nextToken;
        while (Token newNext := lexer.next())
            {
            switch (newNext.id)
                {
                case EolComment:
                case EncComment:
                    // silently discard comments
                    break;

                case DocComment:
                    prevDoc = newNext;
                    break;

                default:
                    nextToken = newNext;
                    return result;
                }
            }

        nextToken = Null;
        return result;
        }

    /**
     * Obtain the next token in the parse stream, and advance the parse stream.
     *
     * @return the next token in the parse stream
     */
    protected Token next()
        {
        return advance() ?: throw new ParseFailed("EOF");
        }

    /**
     * Obtain the most recently _matched_ token from the parse stream.
     *
     * @return the most recent token returned from `match(...)`, or `expect(...)`
     */
    protected Token prev()
        {
        return prevToken ?: assert;
        }

    /**
     * Place the specified token in the front of the parse stream. For each call to a method that
     * takes a token from the stream, such as [advance()], [next()], `match(...)`, or `expect(...)`,
     * it is possible to invoke this operation up to one time; an attempt to put back more than one
     * token in a row will result in an exception.
     *
     * @token  the token to use as the next token in the parse stream
     */
    protected void putBack(Token token)
        {
        assert backToken == Null;
        backToken = token;
        }

    /**
     * Without actually taking anything out of the token stream, obtain the next token.
     *
     * @return the next token in the token stream without actually taking it from the token stream
     *
     * @throws ParseFailed  iff there are no more tokens in the token stream
     */
    protected Token peek()
        {
        return backToken ?: nextToken ?: lexer.eofToken ?: assert;
        }

    /**
     * Without actually taking anything out of the token stream, determine if the next token matches
     * the specified token id, and return it if it does.
     *
     * @param id  the Id to match
     *
     * @return True iff the next token in the token stream matches the specified Id
     * @return (conditional) the matching token
     */
    protected conditional Token peek(Id id)
        {
        if (Token token ?= backToken ?: nextToken, token.id == id)
            {
            return True, token;
            }

        return False;
        }

    /**
     * Without actually taking anything out of the token stream, determine if the next token matches
     * some constraint, and return it if it does.
     *
     * @param matches  a function that indicates that a token meets the constraint
     *
     * @return True iff the next token in the token stream matches the constraint
     * @return (conditional) the matching token
     */
    protected conditional Token peek(function Boolean(Token) matches)
        {
        if (Token token ?= backToken ?: nextToken, matches(token))
            {
            return True, token;
            }

        return False;
        }

    /**
     * Determine if the next token matches the specified token id, and return it if it does.
     *
     * @param id        the Id to match
     * @param required  (optional) specify True if the next token **must** match the specified id
     *
     * @return the matching token, or `Null`
     *
     * @throws ParseFailed  iff `required` is specified, and either the next token does not match or
     *                      there are no more tokens in the token stream
     */
    protected Token? match(Id id, Boolean required=False)
        {
        Token? token = backToken ?: nextToken;
        if (token?.id == id)
            {
            advance();
            prevToken = token;
            return token;
            }

        if (required)
            {
            throw new ParseFailed($"{id} required, {token?.id.name : "EOF"} found");
            }

        return Null;
        }

    /**
     * Determine if the next token matches some constraint, and return it if it does.
     *
     * @param matches   a function that indicates that a token meets the constraint
     * @param required  (optional) specify True if the next token **must** match the specified
     *                  constraint
     *
     * @return the matching token, or `Null`
     *
     * @throws ParseFailed  iff `required` is specified, and either the next token does not match or
     *                      there are no more tokens in the token stream
     */
    protected Token? match(function Boolean(Token) matches, Boolean required=False)
        {
        Token? token = backToken ?: nextToken;
        if (matches(token?))
            {
            advance();
            prevToken = token;
            return token;
            }

        if (required)
            {
            throw new ParseFailed($"matching token required, {token?.id.name : "EOF"} found");
            }

        return Null;
        }

    /**
     * Verify that the next token matches the specified token id, and return it.
     *
     * @param id  the Id to match
     *
     * @return the matching token
     *
     * @throws ParseFailed  iff either the next token does not match or there are no more tokens in
     *                      the token stream
     */
    protected Token expect(Id id)
        {
        return match(id, True) ?: assert;
        }

    /**
     * Verify that the next token matches the specified constraint, and return it.
     *
     * @param matches   a function that evaluates a token constraint
     *
     * @return the matching token
     *
     * @throws ParseFailed  iff either the next token does not match or there are no more tokens in
     *                      the token stream
     */
    protected Token expect(function Boolean(Token) matches)
        {
        return match(matches, True) ?: assert;
        }


    // ----- Markable methods ----------------------------------------------------------------------

    /**
     * A restorable position within the Lexer (Literally, Lex-Mark.)
     */
    protected static const Mark(immutable Object lexerMark,
                                Token?           nextToken,
                                Token?           prevToken,
                                Token?           backToken,
                                Token?           prevDoc,
                                Boolean          suppressRecovery);

    @Override
    immutable Object mark()
        {
        return new Mark(lexer.mark(), nextToken, prevToken, backToken, prevDoc, suppressRecovery);
        }

    @Override
    void restore(immutable Object mark, Boolean unmark = False)
        {
        assert mark.is(Mark);

        lexer.restore(mark.lexerMark);
        nextToken        = mark.nextToken;
        prevToken        = mark.prevToken;
        backToken        = mark.backToken;
        prevDoc          = mark.prevDoc;
        suppressRecovery = mark.suppressRecovery;
        }

    @Override
    void unmark(Object mark)
        {
        assert mark.is(Mark);

        lexer.unmark(mark.lexerMark);
        }


    // ----- error handling ------------------------------------------------------------------------

    /**
     * An exception that indicates that the parser failed due to a problem in the source code being
     * parsed.
     */
    static const ParseFailed(String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * Log an error.
     *
     * @param severity  the severity of the error
     * @param errmsg    the error message identity
     * @param params    the values to use to populate the parameters of the error message
     * @param before    the TextPosition of the first character (inclusive) related to the error
     * @param after     the TextPosition of the last character (exclusive) related to the error
     *
     * @return True indicates that the process that reported the error should attempt to abort at
     *         this point if it is able to
     */
    protected Boolean log(Severity severity, ErrorMsg errmsg, Object[] params, TextPosition before, TextPosition after)
        {
        return errs.log(new Error(severity, errmsg.code, ErrorMsg.lookup, params, source, before, after));
        }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it will eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message)
        {
        FatalError      ("PARSER-01", "Unknown fatal parser error: \"{0}\"."),
        UnexpectedEof   ("PARSER-02", "Unexpected End-Of-File (token exhaustion)."),
        ExpectedToken   ("PARSER-03", "Expected token {0}; found {1}."),
        BadVersion      ("PARSER-04", "Bad version value."),
        BadHexLiteral   ("PARSER-05", "Bad hex character: {0}."),
        BadCustom       ("PARSER-06", "Unsupported custom literal type: \"{0}\"."),
        NoTopLevel      ("PARSER-07", "Cannot have module or package in a method."),
        NotMultiAsn     ("PARSER-08", "Multiple assignment list required."),
        NoEmptyStmt     ("PARSER-09", "Empty statement is illegal."),
        NoChildNonNarrow("PARSER-10", "Child type cannot be specified as non-narrowing."),
        NoChildAccess   ("PARSER-11", "Child type cannot have an access specifier (\"{0}\")."),
        MissingCase     ("PARSER-12", "Case statement required first in a switch."),
        AllOrNoDims     ("PARSER-15", "Either all dimensions have values, or none may."),
        ExpectedEof     ("PARSER-16", "Unexpected token: {0}. (Expected an EOF.)"),
        NoTypeFound     ("PARSER-17", "Expected to find a type declaration."),
        ModuleNotRoot   ("PARSER-18", "No statements allowed outside of module declaration."),
        RepeatModifier  ("PARSER-19", "Repeated modifier: \"{0}\"."),
        ModifierConflict("PARSER-20", "Conflicting modifiers: \"{0}\", \"{1}\"."),
        RepeatDefault   ("PARSER-21", "Default switch branch is repeated."),
        NotAssignable   ("PARSER-22", "Expression does not represent an L-Value."),
        TemplateExtra   ("PARSER-23", "Unexpected token following expression in template: \"{0}\"."),
        InvalidPath     ("PARSER-24", "Invalid path: \"{0}\".");

        /**
         * Message  token ids, but not including context-sensitive keywords.
         */
        static Map<String, ErrorMsg> byCode =
            {
            HashMap<String, ErrorMsg> map = new HashMap();
            for (ErrorMsg errmsg : ErrorMsg.values)
                {
                map[errmsg.code] = errmsg;
                }
            return map.makeImmutable();
            };

        /**
         * Lookup unformatted error message by error code.
         */
        static String lookup(String code)
            {
            assert ErrorMsg err := byCode.get(code);
            return err.message;
            }
        }
    }