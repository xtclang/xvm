import io.EndOfFile;
import io.IOException;
import io.Reader;
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
     * @param reader  the Ecstasy source code
     */
    construct(Reader reader)
        {
        this.reader = reader;
        }
    finally
        {
        eatWhitespace();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying Reader.
     */
    protected/private Reader reader;


    // ----- internal ------------------------------------------------------------------------------

    protected Boolean eatWhitespace()
        {
        // TODO
        return False;
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
