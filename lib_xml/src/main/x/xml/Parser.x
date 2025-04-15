import ecstasy.io.Reader;
import ecstasy.io.TextPosition;
import ecstasy.lang.ErrorList;
import ecstasy.lang.ErrorCode;
import ecstasy.lang.Severity;
import ecstasy.lang.src.Error;

import impl.*;

/**
 * An XML `Parser`.
 *
 * Things to consider during REVIEW:
 * * should the use of @Parsed annotation be optional?
 */
class Parser(Boolean ignoreProlog       = False,
             Boolean ignoreComments     = False,
             Boolean ignoreInstructions = False,
            ) {

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [Reader] available while parsing.
     */
    protected Reader reader.get() = reader_ ?: assert;

    /**
     * The storage that holds the [Reader] while parsing.
     */
    private Reader? reader_;

    /**
     * The ErrorList.
     */
    ErrorList errs = new ErrorList();

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Parse an XML [Document] from the passed `String`.
     *
     * @param text  a `String` containing the text of the XML document
     *
     * @return `True` iff parsing succeeded
     * @return (conditional) the parsed XML [Document]
     */
    conditional Parsed+Document parse(String text) = parse(text.toReader());

    /**
     * Parse an XML [Document] from the passed [Reader].
     *
     * @param in  a [Reader] providing the text of the XML document
     *
     * @return `True` iff parsing succeeded
     * @return (conditional) the parsed XML [Document]
     */
    conditional Parsed+Document parse(Reader in) {
        if (!errs.empty) {
            errs = new ErrorList();
        }

        reader_ = in;
        try {
            if (Document doc := parseDocument(), !errs.hasSeriousErrors) {
                return True, doc;
            }
            return False;
        } finally {
            reader_ = Null;
        }
    }

    // ----- parsing -------------------------------------------------------------------------------

    /**
     * Parse an XML [Document].
     *
     *     document    ::= prolog element Misc*
     *     prolog      ::= XMLDecl? Misc* (doctypedecl Misc*)?
     *     doctypedecl ::= '<!DOCTYPE' S Name (S ExternalID)? S? ('[' intSubset ']' S?)? '>'
     *
     * @return `True` iff a [Document] is present and parseable (even if parsing errors occur)
     * @return (conditional) the [Parsed] [DocumentNode]
     */
    protected conditional Parsed+DocumentNode parseDocument() {
        Int     startDoc  = offset;
        Parsed? firstNode = Null;
        Parsed? lastNode  = Null;
        Boolean bad       = False;

        if (Parsed node := parseXmlDecl()) {
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        }

        if (!quit, Parsed node := parseMisc()) {
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        }

        if (!quit, Parsed node := parseDocTypeDecl()) {
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        }

        if (!quit, Parsed node := parseMisc()) {
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        }

        if (Parsed node := parseElement()) { // TODO GG @Parsed ElementNode instead of Parsed+ElementNode
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        } else {
            log(offset, offset, Error, NoRootElement);
            bad = True; // root element is required
        }

        if (!quit, Parsed node := parseMisc()) {
            (firstNode, lastNode) = link(firstNode, lastNode, node);
        }

        if (!quit && !eof) {
            log(offset, offset+1, Error, Unexpected, [reader.take().quoted()]);
        }

        return firstNode == Null || bad
                ? False
                : (True, new @Parsed(startDoc, offset-startDoc) DocumentNode(firstNode));
    }

    /**
     * Parse an XML "Misc":
     *
     *    Misc     ::= Comment | PI | S
     *
     * @return `True` iff any "Misc" [Node] is present and parseable (even if parsing errors occur)
     * @return (conditional) a linked list of "Misc" [Node] objects
     */
    protected conditional Parsed parseMisc() {
        Parsed? firstNode = Null;
        Parsed? lastNode  = Null;
        do {
            matchSpace();
            if (Parsed comment := parseComment()) {
                if (!ignoreComments) {
                    (firstNode, lastNode) = link(firstNode, lastNode, comment);
                }
            } else if (Parsed instruction := parseInstruction()) {
                if (!ignoreInstructions) {
                    (firstNode, lastNode) = link(firstNode, lastNode, instruction);
                }
            } else {
                break;
            }
        } while (!quit);
        return firstNode == Null ? False : (True, firstNode);
    }

    /**
     * Parse an XML "Comment":
     *
     *     Comment  ::= '<!--' ((Char - '-') | ('-' (Char - '-')))* '-->'
     *
     * @return `True` iff a comment is present and parseable (even if parsing errors occur)
     * @return (conditional) a [CommentNode]
     */
    protected conditional Parsed+CommentNode parseComment() {
        Int start = offset;
        if (match("<!--")) {
            while (Char ch := next()) {
                if (ch == '-') {
                    Int count = 1;
                    while (match('-')) {
                        ++count;
                    }
                    if (count >= 2) {
                        Boolean endComment = match('>');
                        if (count > 2 || !endComment) {
                            Int restoreOffset = offset;
                            offset = offset - count - endComment.toInt64();
                            TextPosition hyphens = position;
                            offset = restoreOffset;
                            log(hyphens, position, Error, CommentHyphens);
                        }
                        if (endComment) {
                            return True, new @Parsed(start, offset-start)
                                    CommentNode(Null, ignoreComments ? "" : reader[start+3 ..< offset-3]);
                        }
                    }
                }
            }
            log(start, position, Error, CommentNoEnd);
        }
        return False;
    }

    /**
     * Parse an XML processing instruction:
     *
     *     PI       ::= '<?' PITarget (S (Char* - (Char* '?>' Char*)))? '?>'
     *     PITarget ::= Name - (('X' | 'x') ('M' | 'm') ('L' | 'l'))
     *
     * @return `True` iff a comment is present and parseable (even if parsing errors occur)
     * @return (conditional) a [CommentNode]
     */
    protected conditional Parsed+InstructionNode parseInstruction() {
        Int start = offset;
        if (match("<?")) {
            String  target = "?";
            String? text   = Null;
            Boolean closed = False;

            if (target := matchName()) {
                if (equalsCaseInsens("xml", target)) {
                    log(start+2, offset, Error, PiNoXmlTarget);
                }

                if (matchSpace()) {
                    Int startText = offset;
                    while (Char ch := next()) {
                        if (ch == '?' && match('>')) {
                            text   = reader[startText..<offset-2];
                            closed = True;
                            break;
                        }
                    }
                } else if (match("?>")) {
                    closed = True;
                }
            } else {
                log(offset, offset, Error, PiTargetExpected);
                if (match("?>")) {
                    closed = True;
                }
            }

            if (!closed) {
                log(offset, offset, Error, PiCloseExpected, [peek()?.quoted() : eofText]);
                // find closing "?>"
                while (Char ch := next()) {
                    if (ch == '?' && match('>')) {
                        break;
                    }
                }
            }

            return True, new @Parsed(start, offset-start) InstructionNode(Null, target, text);
        }
        return False;
    }

    /**
     * Parse an XMLDecl:
     *
     *     XMLDecl      ::=  '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
     *     VersionInfo  ::=  S 'version' Eq ("'" VersionNum "'" | '"' VersionNum '"')
     *     Eq           ::=  S? '=' S?
     *     VersionNum   ::=  '1.' [0-9]+
     *     EncodingDecl ::=  S 'encoding' Eq ('"' EncName '"' | "'" EncName "'" )
     *     EncName      ::=  [A-Za-z] ([A-Za-z0-9._] | '-')*
     *     SDDecl       ::=  S 'standalone' Eq (("'" ('yes' | 'no') "'") | ('"' ('yes' | 'no') '"'))
     *
     * @return `True` iff a XmlDecl is present and parseable (even if parsing errors occur)
     * @return (conditional) a [XmlDeclNode]
     */
    protected conditional Parsed+XmlDeclNode parseXmlDecl() {
        Int startDecl = offset;
        if (match("<?") && matchName("xml")) {
            Version xmlVersion = v:1.0;
            matchSpace();
            Int startAttr = offset;
            if (String value := parseRawAttribute("version")) {
                if (value != "1.0") {
                    try {
                        xmlVersion = new Version(value);
                        log(startAttr, offset, Warning, XmlDeclUnsuppVer, [value.quoted()]);
                    } catch (Exception e) {
                        log(startAttr, offset, Error, XmlDeclBadVer, [value.quoted()]);
                    }
                }
            } else {
                log(offset, offset, Error, XmlDeclNoVer);
            }

            String? encoding = Null;
            matchSpace();
            startAttr = offset;
            if (String value := parseRawAttribute("encoding")) {
                if (isValidEncoding(value)) {
                    encoding = value;
                } else {
                    log(startAttr, offset, Error, XmlDeclBadEnc, [value.quoted()]);
                }
            }

            Boolean? standalone = Null;
            matchSpace();
            startAttr = offset;
            if (String value := parseRawAttribute("standalone")) {
                if (value == "yes") {
                    standalone = True;
                } else if (value == "no") {
                    standalone = False;
                } else {
                    log(startAttr, offset, Error, XmlDeclBadSA, [value.quoted()]);
                }
            }

            matchSpace();
            if (!required("?>")) {
                skipPast("?>");
            }
            return True, new @Parsed(startDecl, offset) XmlDeclNode(Null, xmlVersion, encoding, standalone);
        }
        offset = startDecl;
        return False;
    }

    /**
     * Parse a "doctypedecl":
     *
     *    doctypedecl ::= '<!DOCTYPE' S Name (S ExternalID)? S? ('[' intSubset ']' S?)? '>'
     *    DeclSep     ::= PEReference | S
     *    intSubset   ::= (markupdecl | DeclSep)*
     *    markupdecl  ::= elementdecl | AttlistDecl | EntityDecl | NotationDecl | PI | Comment
     *
     * @return `True` iff a `doctypedecl` is present and parseable (even if parsing errors occur)
     * @return (conditional) the [Parsed] [DocTypeNode] value
     */
    protected conditional Parsed+/*DocType*/Node parseDocTypeDecl() {
        assert:TODO !match("<!DOCTYPE");
        // TODO
        return False;
    }

    /**
     * Parse an XML attribute of the specified name.
     *
     *     Attribute ::= Name Eq AttValue
     *     Eq        ::= S? '=' S?
     *     AttValue  ::=   '"' ([^<&"] | Reference)* '"'
     *                   | "'" ([^<&'] | Reference)* "'"
     *
     * @return `True` iff an attribute with the specified name is present and parseable (even if
     *         parsing errors occur)
     * @return (conditional) the attribute value
     */
    conditional String parseRawAttribute(String name) {
        if (match(name)) {
            matchSpace();
            required('=');
            matchSpace();
            String value = "";
            if (Char quote := peek(), quote == '\"' || quote == '\'') {
                Int start = offset;
                if (skipUntil(ch -> ch == quote)) {
                    value = reader[start..offset];
                    if (value.indexOf('&')) {
                        value = value.replace("&lt;"  , "<" )
                                     .replace("&gt;"  , ">" )
                                     .replace("&apos;", "\'")
                                     .replace("&quot;", "\"")
                                     .replace("&amp;" , "&" );
                    }
                }
                required(quote);
            } else {
                // force an error
                required('\"');
                skipUntil(ch -> ch == '>');
            }
            return True, value;
        }
        return False;
    }

    /**
     * Parse an XML [Element]:
     *
     *     element      ::=   EmptyElemTag
     *                      | STag content ETag
     *     EmptyElemTag ::= '<' Name (S Attribute)* S? '/>'
     *     STag         ::= '<' Name (S Attribute)* S? '>'
     *     content      ::= CharData? ((element | Reference | CDSect | PI | Comment) CharData?)*
     *     CharData     ::= [^<&]* - ([^<&]* ']]>' [^<&]*)
     *     Reference    ::= EntityRef | CharRef
     *     EntityRef    ::= '&' Name ';'
     *     CharRef      ::=   '&#' [0-9]+ ';'
     *                      | '&#x' [0-9a-fA-F]+ ';'
     *     CDSect       ::= CDStart CData CDEnd
     *     CDStart      ::= '<![CDATA['
     *     CData        ::= (Char* - (Char* ']]>' Char*))
     *     CDEnd        ::= ']]>'
     *     Comment      ::= '<!--' ((Char - '-') | ('-' (Char - '-')))* '-->'
     *     PI           ::= '<?' PITarget (S (Char* - (Char* '?>' Char*)))? '?>'
     *     PITarget     ::= Name - (('X' | 'x') ('M' | 'm') ('L' | 'l'))
     *     ETag         ::= '</' Name S? '>'
     *
     * @return `True` iff an [Element] is present and parseable (even if parsing errors occur)
     * @return (conditional) the [Parsed] [ElementNode]
     */
    protected conditional Parsed+ElementNode parseElement() {
        Int stagOffset = offset;
        if (!quit && match('<'), String elementName := matchName()) {
            Parsed? firstNode = Null;
            Parsed? lastNode  = Null;

            // parse attributes
            while (matchSpace(), Parsed node := parseAttribute()) {
                (firstNode, lastNode) = link(firstNode, lastNode, node);
            }
            // check if the STag is actually an EmptyElemTag
            if (match('/')) {
                required('>');
                return True, new @Parsed(stagOffset, offset-stagOffset) ElementNode(elementName, firstNode);
            }
            // otherwise the STag is just an STag
            required('>');

            // parse `content`, i.e. any combination of `CharData?`, `element`, `Reference`,
            // `CDSect`, `PI`, `Comment`, and `ETag`, until the `ETag` has been parsed
            do {
                Int partOffset = offset;
                if (Char firstChar := next()) {
                    if (firstChar == '<') { // element, CDSect, PI, Comment, ETag
                        if (Char secondChar := next()) {
                            if (secondChar == '!') { // CDSect "<![" or Comment "<!-"
                                if (match('[')) {
                                    offset = partOffset;
                                    if (val node := parseCData()) {
                                        (firstNode, lastNode) = link(firstNode, lastNode, node);
                                        continue;
                                    }
                                    log(partOffset, offset, Error, CDataExpected);
                                } else {
                                    offset = partOffset;
                                    if (val node := parseComment()) {
                                        (firstNode, lastNode) = link(firstNode, lastNode, node);
                                        continue;
                                    }
                                    log(partOffset, offset, Error, CommentExpected);
                                }
                            } else if (secondChar == '/') { // ETag "</"
                                required(elementName);
                                matchSpace();
                                if (!required('>')) {
                                    while (next()? != '>') {}
                                }
                                break;
                            } else if (secondChar == '?') { // PI "<?"
                                offset = partOffset;
                                if (val node := parseInstruction()) {
                                    (firstNode, lastNode) = link(firstNode, lastNode, node);
                                    continue;
                                }
                                log(partOffset, offset, Error, PiExpected);
                            } else { // element "<"
                                offset = partOffset;
                                if (val node := parseElement()) {
                                    (firstNode, lastNode) = link(firstNode, lastNode, node);
                                    continue;
                                }
                                log(partOffset, offset, Error, ElementExpected);
                            }
                        }
                        // if no parsing progress was made, then assume an error was already logged
                        // and we need to skip over some "<...>" chunk of text
                        if (offset == partOffset) {
                            assert errs.hasSeriousErrors;
                            while (next()? != '>') {}
                        }
                    } else { // CharData and Reference
                        StringBuffer? buf = Null;
                        Char ch = firstChar;
                        do {
                            if (ch == '&') {
                                Int ampOffset = offset - 1;
                                (Boolean resolved, String|(Parsed+EntityRef) text) = parseReference();
                                if (text.is(String)) {
                                    if (resolved && buf == Null) {
                                        buf = new StringBuffer();
                                        Int current = offset;
                                        offset = partOffset;
                                        reader.pipeTo(buf, ampOffset-partOffset);
                                        offset = current;
                                    }
                                    buf?.addAll(text);
                                } else {
                                    if (ampOffset > partOffset) {
                                        // we have already accrued text that needs to be
                                        // converted into `@Parsed @ContentNode Data`
                                        Node node = new @Parsed(partOffset, ampOffset-partOffset)
                                                @ContentNode
                                                Data(buf?.toString() : reader[partOffset..<ampOffset]);
                                        (firstNode, lastNode) = link(firstNode, lastNode, node);
                                    }
                                    (firstNode, lastNode) = link(firstNode, lastNode, text);
                                    partOffset = offset;
                                    buf        = Null;
                                }
                            } else if (ch == '<') {
                                reader.rewind(1);
                                break;
                            } else {
                                buf?.add(ch);
                            }
                        } while (ch := next());
                        if (offset > partOffset) {
                            Node node = new @Parsed(partOffset, offset-partOffset) @ContentNode
                                    Data(buf?.toString() : reader[partOffset..<offset]);
                            (firstNode, lastNode) = link(firstNode, lastNode, node);
                        }
                        continue;
                    }
                }
                if (eof) {
                    // EOF before we found ETag
                    log(offset, offset, Error, Expected, [$"</{elementName}>", eofText]);
                    break;
                }
            } while (!quit);
            return True, new @Parsed(stagOffset, offset-stagOffset) ElementNode(elementName, firstNode);
        } else {
            offset = stagOffset;
            return False;
        }
    }

    /**
     * Parse an XML [Attribute]:
     *
     *     Attribute ::= Name Eq AttValue
     *     Eq        ::= S? '=' S?
     *     AttValue  ::=   '"' ([^<&"] | Reference)* '"'
     *                   | "'" ([^<&'] | Reference)* "'"
     *
     * @return `True` iff an attribute with the specified name is present and parseable (even if
     *         parsing errors occur)
     * @return (conditional) the attribute value
     */
    protected conditional Parsed+AttributeNode parseAttribute() {
        Int attrOffset = offset;
        if (String name := matchName()) {
            Parsed? firstNode = Null;
            Parsed? lastNode  = Null;

            matchSpace();
            required('=');
            matchSpace();
            String value = "";
            if (Char quote := required(isQuote)) {
                Int valOffset = offset;
                StringBuffer? buf = Null;
                while (Char ch := next()) {
                    if (ch == quote) {
                        break;
                    } else if (ch == '&') {
                        Int ampOffset = offset-1;
                        (Boolean resolved, String|(Parsed+EntityRef) text) = parseReference();
                        if (text.is(String)) {
                            if (resolved && buf == Null) {
                                buf = new StringBuffer();
                                Int current = offset;
                                offset = attrOffset;
                                reader.pipeTo(buf, ampOffset-valOffset);
                                offset = current;
                            }
                            buf?.addAll(text);
                        } else {
                            if (ampOffset > valOffset) {
                                // we have already accrued text that needs to be converted into
                                // `@Parsed @ContentNode Data`
                                Node node = new @Parsed(valOffset, ampOffset-valOffset) @ContentNode
                                        Data(buf?.toString() : reader[valOffset..<ampOffset]);
                                (firstNode, lastNode) = link(firstNode, lastNode, node);
                            }
                            (firstNode, lastNode) = link(firstNode, lastNode, text);
                            valOffset = offset;
                            buf       = Null;
                        }
                    } else {
                        buf?.add(ch);
                    }
                }
                if (firstNode == Null) {
                    value = buf?.toString() : reader[valOffset..<offset-1];
                } else {
                    Int endOffset = offset-1; // do not take the quote char
                    if (endOffset > valOffset) {
                        // the content is made up of a sequence of Content Parts, so if there's
                        // anything left at the end, we have to add one more part to the list
                        Node node = new @Parsed(valOffset, endOffset-valOffset) @ContentNode
                                Data(buf?.toString() : reader[valOffset..<endOffset]);
                        (firstNode, lastNode) = link(firstNode, lastNode, node);
                    }
                }
            } else {
                // no quote? then there's no obvious way to recover; just find the end of the "stag"
                while (Char ch := next()) {
                    if (ch == '/') {
                        if (peek()? == '>') {
                            reader.rewind(1);
                            break;
                        }
                    } else if (ch == '>') {
                        reader.rewind(1);
                        break;
                    }
                }
            }
            return True, firstNode == Null
                    ? new @Parsed(attrOffset, offset-attrOffset) AttributeNode(Null, name, value)
                    : new @Parsed(attrOffset, offset-attrOffset) AttributeNode(name, firstNode);
        }
        return False;
    }

    /**
     * Parse and resolve a "CData" section.
     *
     *     CDSect       ::= CDStart CData CDEnd
     *     CDStart      ::= '<![CDATA['
     *     CData        ::= (Char* - (Char* ']]>' Char*))
     *     CDEnd        ::= ']]>'
     *
     * @return `True` iff a CData section is present and parseable (even if parsing errors occur)
     * @return (conditional) the [Parsed] [CData] [ContentNode]
     */
    protected conditional Parsed+ContentNode+CData parseCData() {
        Int start = offset;
        if (match("<![CDATA[")) {
            while (Char ch := next()) {
                if (ch == ']' && match("]>")) {
                    return True, new @Parsed(start, offset-start)
                            @ContentNode
                            CData(reader[start+9 ..< offset-3]);
                }
            }
            log(start, position, Error, CDataNoEnd);
        }
        return False;
    }

    /**
     * Parse and resolve a "Reference". The leading `&` has already been "eaten".
     *
     *     Reference ::= EntityRef | CharRef
     *     EntityRef ::= '&' Name ';'
     *     CharRef   ::=   '&#' [0-9]+ ';'
     *                   | '&#x' [0-9a-fA-F]+ ';'
     *
     * @return `True` iff the reference could be both parsed and resolved
     * @return the `String` value or the [Parsed] [EntityRef] to use in lieu of the reference
     */
    (Boolean resolved, String|(Parsed+EntityRef) ref) parseReference() {
        Int start = offset-1;
        // check for a CharRef
        if (match('#')) {
            UInt32  codepoint = 0;
            Boolean hex       = match('x');
            Boolean any       = False;
            Boolean bad       = False;
            while (Char ch := next(), val n := hex ? ch.asciiHexit() : ch.asciiDigit()) {
                any       = True;
                codepoint = hex
                        ? codepoint << 4 | n
                        : codepoint * 10 + n;
                if (codepoint > 0x10FFFF) {
                    bad = True;
                }
            }
            required(';');
            Char ch = codepoint.toChar();
            if (!any || bad || !isChar(ch)) {
                String text = reader[start..<offset];
                log(start, offset, Error, CharRefRange, [text.quoted()]);
                return False, text;
            }
            return True, ch.toString();
        }

        // otherwise parse an EntityRef
        if (String name := matchName()) {
            required(';');
            if (String|(Parsed+EntityRef) resolved := resolveReference(name)) {
                return True, resolved;
            }
            log(start, offset, Error, UnknownRef, [name.quoted()]);
            return False, reader[start..<offset]; // just use the illegal text "as-is"
        }

        log(start, offset, Error, Unexpected, ["\"&\""]);
        return False, "&";
    }

    /**
     * Resolve the specified reference name.
     *
     * @param name  the XML `Name` from an XML `EntityRef`
     *
     * @return `True` iff the name is known
     * @return (conditional) the text associated with the name
     */
    // TODO CP: conditional (Parsed+EntityRef)|String resolveReference(String name) {
    conditional String|(Parsed+EntityRef) resolveReference(String name) {
        switch (name) {
        case "lt"  : return True, "<";
        case "gt"  : return True, ">";
        case "apos": return True, "\'";
        case "quot": return True, "\"";
        case "amp" : return True, "&";
        }

        // TODO resolve DTD (DOCTYPE) names
        return False;
    }

    /**
     * Build a linked list by adding a Node at a time.
     *
     * @param firstNode  the first [Node] in a linked list, or `Null` if no nodes are in the list
     * @param lastNode   the last [Node] in a linked list, or `Null` if no nodes are in the list
     * @param newNode    the [Node] to add to the end of the linked list
     *
     * @return the first [Node] in the linked list
     * @return the last [Node] in the linked list
     */
    protected <SpecificNode extends Parsed+Node>
            (SpecificNode firstNode,
             SpecificNode lastNode,
            ) link(SpecificNode? firstNode,
                   SpecificNode? lastNode,
                   SpecificNode  newNode,
                  ) {
        if (firstNode == Null) {
            assert lastNode == Null;
            return newNode, newNode;
        }

        assert lastNode != Null;
        assert val lastNodeRW := &lastNode.revealAs((protected Node));
        lastNodeRW.next_ = newNode;
        return firstNode, newNode;
    }

    // ----- lexing --------------------------------------------------------------------------------

    /**
     * Provides [Reader.eof].
     */
    protected Boolean eof.get() = reader.eof;

    /**
     * Provides gettable and settable [Reader.offset]
     */
    protected Int offset {
        @Override
        Int get() = reader.offset;

        @Override
        void set(Int newValue) {
            reader.offset = newValue;
        }
    }

    /**
     * Provides [Reader.position]
     */
    protected TextPosition position.get() = reader.position;

    /**
     * Provides [Reader.peek]
     */
    protected conditional Char peek() = reader.peek();

    /**
     * Provides [Reader.next]
     */
    protected conditional Char next() = reader.next();

    /**
     * Provides [Reader.match(Char)]
     */
    protected conditional Char match(Char ch) = reader.match(ch);

    /**
     * Provides [Reader.match(function Boolean(Char))]
     */
    protected conditional Char match(function Boolean(Char) matches) = reader.match(matches);

    /**
     * Provides [Reader] `match`-like functionality, but for an entire `String` and not just a
     * single `Char`.
     *
     * @param text  the `String` to match
     *
     * @return `True` iff the next sequence of characters in the [Reader] are identical to those in
     *         the provided `text`
     * @return (conditional) the matched String
     */
    protected conditional String match(String text) {
        Int oldOffset = offset;
        for (Char ch : text) {
            if (!match(ch)) {
                offset = oldOffset;
                return False;
            }
        }
        return True, text;
    }

    /**
     * @return `True` iff one or more XML "White Space" characters was matched
     */
    protected Boolean matchSpace() {
        if (match(isSpace)) {
            while (match(isSpace)) {}
            return True;
        }
        return False;
    }

    /**
     * From 2.3 Common Syntactic Constructs:
     *     Name ::= NameStartChar (NameChar)*
     *
     * @return `True` iff an XML "Name" was next in the [Reader]
     * @return (conditional) the string holding the "Name"
     */
    protected conditional String matchName() {
        if (Char ch := match(isNameStart)) {
            Int start = offset-1;
            while (match(isNameChar)) {}
            return True, reader[start..<offset];
        }
        return False;
    }

    /**
     * From 2.3 Common Syntactic Constructs:
     *     Name ::= NameStartChar (NameChar)*
     *
     * @param name  the name to match
     *
     * @return `True` iff an XML "Name" was next in the [Reader], and it matches the specified name
     * @return (conditional) the string holding the "Name" (same as passed in)
     */
    protected conditional String matchName(String name) {
        Int prev = offset;
        if (match(name)) {
            if (Char ch := peek(), isNameChar(ch)) {
                offset = prev;
                return False;
            }
            return True, name;
        }
        return False;
    }

    /**
     * Match for the specified character, and log an error if it was not present.
     *
     * @param ch  the character to match
     *
     * @return `True` iff the next character matches the specified character
     */
    protected Boolean required(Char ch) {
        if (match(ch)) {
            return True;
        }

        log(offset, offset+1, Error, Expected, [ch.quoted(), peek()?.quoted() : eofText]);
        return False;
    }

    /**
     * Verify that the next character matches the provided function, and log an error otherwise.
     *
     * @param ch  the character to match
     *
     * @return `True` iff the next character matches the specified character
     */
    protected conditional Char required(function Boolean(Char) matches) {
        if (Char ch := match(matches)) {
            return True, ch;
        }

        log(offset, offset+1, Error, Unexpected, [peek()?.quoted() : eofText]);
        return False;
    }

    /**
     * Match for the specified string, and log an error if it was not present.
     *
     * @param s  the string to match
     *
     * @return `True` iff the next characters matches the contents of the string
     */
    protected Boolean required(String s) {
        if (match(s)) {
            return True;
        }

        // assemble what we found instead of the match string
        Int     start    = offset;
        Boolean isName   = True;
        Int     matchLen = s.size;
        String? found    = Null;
        if (!eof) {
            Each: while (Char ch := next()) {
                if (isName) {
                    if (!isNameChar(ch)) {
                        isName = False;
                        if (offset-start > matchLen) {
                            reader.rewind(1);
                            break;
                        }
                    }
                } else {
                    if (Each.count >= matchLen) {
                        break;
                    }
                }
            }
            found = reader[start..<offset];
            offset = start;
        }
        log(offset, offset+1, Error, Expected, [s.quoted(), found?.quoted() : eofText]);
        return False;
    }

    /**
     * Fast forward until the provided function evaluates to `True`, and then back up one character.
     *
     * @param tooFar  evaluates each character and returns `True` when the fast-forward has gone too
     *                far
     *
     * @return `True` iff the function returned `True`, otherwise `False` when the EOF is reached
     * @return (conditional) the character for which the function returned `True`
     */
    conditional Char skipUntil(function Boolean(Char) tooFar) {
        while (Char ch := next()) {
            if (tooFar(ch)) {
                reader.rewind(1);
                return True, ch;
            }
        }
        return False;
    }

    /**
     * Fast forward until the specified string has been found.
     *
     * @param s  the string to fast forward past
     *
     * @return `True` iff the string was found, or `False` if EOF was reached without finding the
     *         string
     */
    Boolean skipPast(String s) {
        assert !s.empty;
        Char   lead   = s[0];
        String tail   = s.substring(1);
        while (Char ch := next()) {
            if (ch == lead && match(tail)) {
                return True;
            }
        }
        return False;
    }

    // ----- error handling ------------------------------------------------------------------------

    /**
     * Indicates when the `Parser` should give up on parsing.
     */
    Boolean quit.get() = eofReported || errs.abortDesired;

    /**
     * Set this to `True` after logging an error caused by EOF.
     */
    protected Boolean eofReported = False;

    /**
     * Return the string used to indicate EOF in the error log, and also assume that an error has
     * been logged for EOF having been reached without finishing parsing.
     */
    protected String eofText.get() {
        eofReported = True;
        return "EOF";
    }

    /**
     * Log an error.
     *
     * @param start     the TextPosition or offset of the first character (inclusive) related to the
     *                  error
     * @param end       the TextPosition or offset of the last character (exclusive) related to the
     *                  error
     * @param severity  the severity of the error
     * @param msg       the error message identity
     * @param params    the values to use to populate the parameters of the error message
     *
     * @return True indicates that the process that reported the error should attempt to abort at
     *         this point if it is able to
     */
    protected Boolean log(TextPosition|Int before,
                          TextPosition|Int after,
                          Severity         severity,
                          ErrorMsg         errmsg,
                          Object[]         params = [],
                         ) {
        Int? restore = Null;
        if (before.is(Int)) {
            restore = offset;
            offset  = before;
            before  = position;
        }
        if (after.is(Int)) {
            restore ?:= offset;
            offset    = after;
            after     = position;
        }
        Boolean result = errs.log(new Error(reader, before, after, severity, errmsg, Null, params));
        offset = restore?;
        return result;
    }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it can eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message)
            implements ErrorCode {
        Expected        ("XML-01", "Expected {0}, found {1}."),
        Unexpected      ("XML-02", "Unexpected character: {0}."),
        UnknownRef      ("XML-03", "Unknown reference: {0}."),
        CharRefRange    ("XML-04", "Character reference is illegal or out of range: {0}."),
        XmlDeclNoVer    ("XML-11", "Missing XML \"version\" attribute."),
        XmlDeclBadVer   ("XML-12", "Invalid XML \"version\" attribute: {0}."),
        XmlDeclUnsuppVer("XML-13", "Invalid XML \"version\" attribute: {0}."),
        XmlDeclBadEnc   ("XML-14", "Invalid XML \"encoding\" attribute: {0}."),
        XmlDeclBadSA    ("XML-15", "Invalid XML \"standalone\" attribute: {0}."),
        CommentHyphens  ("XML-21", "When two '-' hyphens occur within an XML comment, the next character must be '>'."),
        CommentNoEnd    ("XML-22", "No XML comment terminator (\"-->\") was found."),
        CommentExpected ("XML-23", "XML comment expected"),
        PiTargetExpected("XML-31", "An XML Processing Instruction must start with a name; {0} was encountered instead."),
        PiCloseExpected ("XML-32", "An XML Processing Instruction terminator (\"?>\") was expected."),
        PiNoXmlTarget   ("XML-33", "An XML Processing Instruction must not have the target \"xml\"."),
        PiExpected      ("XML-34", "XML Processing Instruction expected"),
        NoRootElement   ("XML-41", "Missing XML root element."),
        ElementExpected ("XML-42", "XML element expected."),
        CDataExpected   ("XML-51", "XML CData expected."),
        CDataNoEnd      ("XML-52", "No XML CData terminator (\"]]>\") was found."),
        ;

        /**
         * Message  token ids, but not including context-sensitive keywords.
         */
        static Map<String, ErrorMsg> byCode = {
            HashMap<String, ErrorMsg> map = new HashMap();
            for (ErrorMsg errmsg : ErrorMsg.values) {
                map[errmsg.code] = errmsg;
            }
            return map.makeImmutable();
        };

        /**
         * Lookup unformatted error message by error code.
         */
        static String lookup(String code) {
            assert ErrorMsg err := byCode.get(code);
            return err.message;
        }
    }
}
