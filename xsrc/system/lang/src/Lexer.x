import io.TextPosition;

/**
 * A lexical analyzer (tokenizer) for the Ecstasy language.
 */
class Lexer
        implements Iterator<Token>
        implements Markable
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy lexical analyzer ("tokenizer") that processes source code from a Reader.
     *
     * @param source   the Ecstasy source code
     * @param errlist  the ErrorList to log errors to
     */
    construct(Source source, ErrorList errlist)
        {
        this.source  = source;
        this.errlist = errlist;
        this.reader  = source.createReader();
        }
    finally
        {
        eatWhitespace();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [Source] being lexed.
     */
    public/private Source source;

    /**
     * The underlying [Reader]. Note that the UTF-escape transformation occurs between this Reader
     * and the lexer.
     */
    protected/private Reader reader;

    /**
     * Keeps track of whether whitespace was encountered.
     */
    protected/private Boolean whitespace;

    /**
     * The ErrorList to log errors to.
     */
    public/private ErrorList errlist;


    // ----- Iterator methods ----------------------------------------------------------------------

    @Override
    conditional Token next()
        {
        if (eof)
            {
            return False;
            }

        Boolean      spaceBefore = whitespace;
        TextPosition posBefore   = reader.position;
        (Id id, Object value)    = eatToken();
        TextPosition posAfter    = reader.position;
        Boolean      spaceAfter  = eatWhitespace();
        return True, new Token(id, posBefore, posAfter, value, spaceBefore, spaceAfter);
        }


    // ----- Markable methods ----------------------------------------------------------------------

    /**
     * A restorable position within the Lexer (Literally, Lex-Mark.)
     */
    protected class Mark(Reader reader, TextPosition position, Boolean whitespace);

    @Override
    Object mark()
        {
        return new Mark(reader, reader.position, whitespace);
        }

    @Override
    void restore(Object mark, Boolean unmark = False)
        {
        assert mark.is(Mark);
        assert mark.reader == reader;
        reader.position = mark.position;
        this.whitespace = mark.whitespace;
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Eat the characters defined as whitespace, which include line terminators and the file
     * terminator. Whitespace does not include comments.
     */
    protected Boolean eatWhitespace()
        {
        Boolean whitespace = False;
        Reader  reader     = this.reader;
        while (Char ch := reader.next())
            {
            if (ch.isWhitespace())
                {
                whitespace = True;
                }
            else
                {
                // put back the non-whitespace character
                reader.rewind();
                break;
                }
            }

        this.whitespace = whitespace;
        return whitespace;
        }

    /**
     * TODO doc
     */
    (Id id, Object value) eatToken()
        {
        TODO
        }


    // ----- reader behavior -----------------------------------------------------------------------

    Boolean eof.get()
        {
        return reader.eof;
        }

    protected conditional Char nextChar()
        {
        Char ch;
        if (ch := reader.next())
            {
            // 1) last character in the file may be SUB
            // 2) CR:LF is converted to LF
            // 3) '\\' + 'u' + 'xxxx'
            // 4) '\\' + 'U' + 'xxxxxxxx'
            switch (ch)
                {
                case '\z':
                    if (reader.eof)
                        {
                        return False;
                        }
                    else
                        {
                        // back up to get the location of the SUB character
                        reader.rewind();
                        TextPosition before = reader.position;
                        assert reader.next();
                        TextPosition after = reader.position;
                        log(Error, UnexpectedEof, [], before, after);
                        }
                    break;

                case '\r':
                    if (!reader.eof)
                        {
                        assert ch := reader.next();
                        if (ch != '\n')
                            {
                            reader.rewind();
                            ch = '\n';
                            }
                        }
                    break;

                case '\\':
                    // TODO uXXXX
                    // TODO UXXXXXXXX
                    break;
                }

            return True, ch;
            }
        else
            {
            return False;
            }
        }


    // ----- error handling ------------------------------------------------------------------------

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
        return errlist.log(new Error(severity, errmsg.code, ErrorMsg.lookup, params, source, before, after));
        }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it will eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message)
        {
        UnexpectedEof     ("LEXER-01", "Unexpected End-Of-File (SUB character)."),
        ExpectedEndcomment("LEXER-02", "Expected a comment-ending \"star slash\" but never found one."),
        IllegalChar       ("LEXER-03", "Invalid character: \"{0}\"."),
        IllegalNumber     ("LEXER-04", "Illegal number: \"{0}\"."),
        CharNoTerm        ("LEXER-05", "An illegal character literal, missing closing quote: \"{0}\"."),
        CharBadEsc        ("LEXER-06", "An illegally escaped character literal: \"{0}\"."),
        CharNoChar        ("LEXER-07", "An illegal character literal missing the character: \"{0}\"."),
        StringNoTerm      ("LEXER-08", "An illegally terminated string literal: \"{0}\"."),
        StringBadEsc      ("LEXER-09", "An illegally escaped string literal: \"{0}\"."),
        IllegalHex        ("LEXER-10", "Illegal hex value: \"{0}\"."),
        ExpectedChar      ("LEXER-11", "Expected \"{0}\"; found \"{1}\"."),
        ExpectedDigits    ("LEXER-12", "\"{0}\" digits were required; only \"{1}\" digits were found."),
        BadDate           ("LEXER-13", "Invalid ISO-8601 date \"{0}\"; date must be in the format \"YYYY-MM-DD\" with valid values for each."),
        BadTime           ("LEXER-14", "Invalid ISO-8601 time \"{0}\"; time must be in the format \"hh:mm:ss.sss\" or \"hhmmss.sss\" (with seconds and fractions of seconds optional) with valid values for each."),
        BadDatetime       ("LEXER-15", "Invalid ISO-8601 datetime \"{0}\"; datetime must be in the format date+\"T\"+time+timezone (with timezone optional), with valid values for each."),
        BadTimezone       ("LEXER-16", "Invalid ISO-8601 timezone \"{0}\"; timezone must be \"Z\" (for UTC), or in the format \"+hh:mm\" or \"+hhmm\" (using either \"+\" or \"-\", and with minutes optional) with valid values for each."),
        BadDuration       ("LEXER-17", "Invalid ISO-8601 duration \"{0}\"; duration must be in the format \"PnYnMnDTnHnMnS\" (with the year, month, day, and time value optional, and the hours, minutes, and seconds values optional within the time portion), with valid values for each."),
        UnexpectedChar    ("LEXER-18", "Unexpected character: \"{0}\".");

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


    // ----- types ---------------------------------------------------------------------------------

    /**
     * An Ecstasy token has a lexical identity (what the element type is), a location in the text stream
     * (with the ending position being the position _after_ the token), and an optional value.
     */
    static const Token(Id id, TextPosition start, TextPosition end, Object value = Null,
                       Boolean spaceBefore=False, Boolean spaceAfter=False);

    /**
     * Token identity categories.
     */
    enum Category {Normal, ContextSensitive, Special, Artificial}

    /**
     * Ecstasy source code is composed of these lexical elements.
     */
    enum Id(String? text, Category category=Normal)
        {
        Colon        (":"              ),
        Semicolon    (";"              ),
        Comma        (","              ),
        Dot          ("."              ),
        DotDot       (".."             ),
        Ellipsis     ("..."            ),
        CurrentDir   ("./"             ),
        ParentDir    ("../"            ),
        LeftParen    ("("              ),
        RightParen   (")"              ),
        LeftCurly    ("{"              ),
        RightCurly   ("}"              ),
        LeftSquare   ("["              ),
        RightSquare  ("]"              ),
        Add          ("+"              ),
        Sub          ("-"              ),
        Mul          ("*"              ),
        Div          ("/"              ),
        DivRem       ("/%"             ),
        Modulo       ("%"              ),
        ShiftLeft    ("<<"             ),
        ShiftRight   (">>"             ),
        ShiftAll     (">>>"            ),
        BitAnd       ("&"              ),
        BitOr        ("|"              ),
        BitXor       ("^"              ),
        BitNot       ("~"              ),
        BoolAnd      ("&&"             ),
        BoolOr       ("||"             ),
        BoolXor      ("^^"             ),
        BoolNot      ("!"              ),
        Binary       ("#"              ),
        At           ("@"              ),
        Condition    ("?"              ),
        Elvis        ("?:"             ),
        Assign       ("="              ),
        AddAsn       ("+="             ),
        SubAsn       ("-="             ),
        MulAsn       ("*="             ),
        DivAsn       ("/="             ),
        ModuloAsn    ("%="             ),
        ShiftLeftAsn ("<<="            ),
        ShiftRightAsn(">>="            ),
        ShiftAllAsn  (">>>="           ),
        BitAndAsn    ("&="             ),
        BitOrAsn     ("|="             ),
        BitXorAsn    ("^="             ),
        BoolAndAsn   ("&&="            ),
        BoolOrAsn    ("||="            ),
        CondAsn      (":="             ),
        NotNullAsn   ("?="             ),
        ElvisAsn     ("?:="            ),
        CompareEQ    ("=="             ),
        CompareNE    ("!="             ),
        CompareLT    ("<"              ),
        CompareLTEQ  ("<="             ),
        CompareGT    (">"              ),
        CompareGTEQ  (">="             ),
        CompareOrder ("<=>"            ),
        Increment    ("++"             ),
        Decrement    ("--"             ),
        Lambda       ("->"             ),
        Any          ("_"              ),
        Allow        ("allow"          , Category.ContextSensitive),  // TODO GG why is "Category." required?
        As           ("as"             ),
        Assert       ("assert"         ),
        AssertRnd    ("assert:rnd"     ),
        AssertArg    ("assert:arg"     ),
        AssertBounds ("assert:bounds"  ),
        AssertTodo   ("assert:TODO"    ),
        AssertOnce   ("assert:once"    ),
        AssertTest   ("assert:test"    ),
        AssertDebug  ("assert:debug"   ),
        Avoid        ("avoid"          , Category.ContextSensitive),
        Break        ("break"          ),
        Case         ("case"           ),
        Catch        ("catch"          ),
        Class        ("class"          ),
        Conditional  ("conditional"    ),
        Const        ("const"          ),
        Construct    ("construct"      ),
        Continue     ("continue"       ),
        Default      ("default"        ),
        Delegates    ("delegates"      , Category.ContextSensitive),
        Do           ("do"             ),
        Else         ("else"           ),
        Enum         ("enum"           ),
        Extends      ("extends"        , Category.ContextSensitive),
        Finally      ("finally"        ),
        For          ("for"            ),
        Function     ("function"       ),
        If           ("if"             ),
        Immutable    ("immutable"      ),
        Implements   ("implements"     , Category.ContextSensitive),
        Import       ("import"         ),
        ImportEmbed  ("import:embedded"),
        ImportRequire("import:required"),
        ImportDesire ("import:desired" ),
        ImportOption ("import:optional"),
        Incorporates ("incorporates"   , Category.ContextSensitive),
        Interface    ("interface"      ),
        Into         ("into"           , Category.ContextSensitive),
        Is           ("is"             ),
        Mixin        ("mixin"          ),
        Module       ("module"         ),
        New          ("new"            ),
        Outer        ("outer"          , Category.Special),
        Package      ("package"        ),
        Prefer       ("prefer"         , Category.ContextSensitive),
        Private      ("private"        ),
        Protected    ("protected"      ),
        Public       ("public"         ),
        Return       ("return"         ),
        Service      ("service"        ),
        Static       ("static"         ),
        Struct       ("struct"         ),
        Super        ("super"          , Category.Special),
        Switch       ("switch"         ),
        This         ("this"           , Category.Special),
        ThisClass    ("this:class"     , Category.Special),
        ThisModule   ("this:module"    , Category.Special),
        ThisPri      ("this:private"   , Category.Special),
        ThisPro      ("this:protected" , Category.Special),
        ThisPub      ("this:public"    , Category.Special),
        ThisServ     ("this:service"   , Category.Special),
        ThisStruct   ("this:struct"    , Category.Special),
        ThisTarget   ("this:target"    , Category.Special),
        Throw        ("throw"          ),
        Todo         ("TODO"           ),
        Try          ("try"            ),
        Typedef      ("typedef"        ),
        Using        ("using"          ),
        Val          ("val"            , Category.ContextSensitive),
        Var          ("var"            , Category.ContextSensitive),
        Void         ("void"           ),
        While        ("while"          ),
        Identifier   (Null             ),
        LitChar      (Null             ),
        LitString    (Null             ),
        LitBinstr    (Null             ),
        LitInt       (Null             ),
        LitDec       (Null             ),
        LitFloat     (Null             ),
        LitDate      (Null             ),
        LitTime      (Null             ),
        LitDatetime  (Null             ),
        LitTimezone  (Null             ),
        LitDuration  (Null             ),
        LitVersion   (Null             ),
        LitPath      (Null             ),               // generated by Parser, not Lexer
        EolComment   (Null             ),
        EncComment   (Null             ),
        StrFile      ("$"              , Category.Artificial),
        Template     ("{...}"          , Category.Artificial),
        DotDotEx     ("..<"            , Category.Artificial),
        EnumVal      ("enum-value"     , Category.Artificial);

        /**
         * Keyword token ids, but not including context-sensitive keywords.
         */
        static Map<String, Id> keywords =
            {
            HashMap<String, Id> map = new HashMap();
            for ((String text, Id id) : allKeywords)
                {
                if (id.category != ContextSensitive)
                    {
                    map[text] = id;
                    }
                }
            return map.makeImmutable();
            };

        /**
         * All keyword token ids, including context-sensitive keywords.
         */
        static Map<String, Id> allKeywords =
            {
            HashMap<String, Id> map = new HashMap();
            for (Id id : Id.values)
                {
                String? text = id.text;
                if (id.category != Artificial && text != Null
                        && (text[0].category.letter || text[0] == '_'))
                    {
                    map[text] = id;
                    }
                }
            return map.makeImmutable();
            };


        /**
         * String representations of tokens that have both "normal" and "suffixed" representations.
         */
        static Map<String, Id> prefixes =
            {
            HashMap<String, Id> map = new HashMap();
            for ((String text, Id id) : allKeywords)
                {
                if (Int colon := text.indexOf(':'))
                    {
                    String prefix = text[0..colon);
                    if (!map.contains(prefix) && !allKeywords.contains(prefix))
                        {
                        map[text] = id;
                        }
                    }
                }
            return map.makeImmutable();
            };
        }
    }
