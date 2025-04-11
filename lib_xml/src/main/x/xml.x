/**
 * A module for working with -- searching, extracting data from, creating, and manipulating -- XML
 * documents.
 */
module xml.xtclang.org {
    package convert import convert.xtclang.org;
    import convert.Format;
    import ecstasy.lang.ErrorList;

    static Boolean equalsCaseInsens(String s1, String s2) = ecstasy.collections.CaseInsensitive.areEqual(s1, s2);

    /**
     * Create an XML [Document] from the passed XML text.
     *
     * @param text    a `String` containing an XML document
     * @param errors  (optional) a collector for errors encountered during parsing
     *
     * @return `True` iff the `String` contained a valid XML document
     * @return (optional) the parsed XML [Document]
     */
    (Document?, ErrorList) parse(String text) {
         Parser parser = new Parser();
         return parser.parse(text) ?: Null, parser.errs;
    }

    /**
     * Create an XML [Document] from the passed XML text.
     *
     * @param name  the name of the top level XML element
     * TODO add optional DTD etc. params
     *
     * @return a new XML [Document] with a root element of the specified name
     */
    Document create(String name) = new impl.DocumentNode(name);

    // ----- XML validation ------------------------------------------------------------------------

    /**
     * Determine if a String is a valid XML
     * ["Name"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Name).
     *
     * @param name  the XML name to test
     *
     * @return `True` iff the specified name is a valid XML "Name"
     */
    static Boolean isValidName(String name) {
        Int len = name.size;
        if (len == 0) {
            return False;
        }
        if (!isNameStart(name[0])) {
            return False;
        }
        for (Int i : 1..<len) {
            if (!isNameChar(name[i])) {
                return False;
            }
        }
        return True;
    }

    /**
     * Determine if the provided content is valid XML
     * [CharData](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CharData).
     *
     * @param content  the textual content to encode in a CDATA section
     *
     * @return `True` iff the specified content can be encoded in a CDATA section
     */
    static Boolean isValidCharData(String content) {
        for (Int i = 0, Int len = content.size; i < len; ++i) {
            Char ch = content[i];
            if (!isChar(ch)) {
                return False;
            }
        }
        return True;
    }

    /**
     * Determine if the provided content can be placed into a valid XML
     * ["CDATA section"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-CDSect).
     *
     * @param content  the textual content to encode in a CDATA section
     *
     * @return `True` iff the specified content can be encoded in a CDATA section
     */
    static Boolean isValidCData(String content) {
        for (Int i = 0, Int len = content.size; i < len; ++i) {
            Char ch = content[i];
            if (!isChar(ch)) {
                return False;
            }
            // CDATA cannot contain the text "]]>"
            if (ch == '>' && i >= 2 && content[i-2] == ']' && content[i-1] == ']') {
                return False;
            }
        }
        return True;
    }

    /**
     * Determine if the provided content is a valid entity reference, aka
     * ["EntityRef"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-EntityRef).
     *
     * @param ref  the EntityRef text
     *
     * @return `True` iff the provided EntityRef text is a legal entity reference in XML
     */
    static Boolean isValidEntityRef(String ref) {
        // EntityRef ::= '&' Name ';'
        Int len = ref.size;
        return len >= 3 && ref[0] == '&' && ref[len-1] == ';' && isValidName(ref[0..<len-1]);
    }

    /**
     * Determine if the provided  target and instruction constitute a valid XML
     * ["PI"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PITarget).
     *
     * @param target       the "PITarget"
     * @param instruction  the contents of the processing instruction
     *
     * @return `True` iff the specified target and instruction are valid
     */
    static Boolean isValidComment(String comment) {
        for (Int i = 0, Int len = comment.size; i < len; ++i) {
            Char ch = comment[i];
            if (!isChar(ch)) {
                return False;
            }
            if (ch == '-' && i > 0 && comment[i-1] == '-') {
                return False;
            }
        }
        return True;
    }

    /**
     * Determine if the provided target is valid for an XML Processing Instruction, aka a
     * ["PI"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PITarget).
     *
     * @param target  the "PITarget"
     *
     * @return `True` iff the specified instruction target is valid
     */
    static Boolean isValidTarget(String target) {
        return isValidName(target) && !equalsCaseInsens(target, "xml");
    }

    /**
     * Determine if the provided instruction is valid for an XML Processing Instruction, aka a
     * ["PI"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PITarget).
     *
     * @param instruction  the contents of the processing instruction
     *
     * @return `True` iff the specified instruction text is valid
     */
    static Boolean isValidInstruction(String instruction) {
        for (Int i = 0, Int len = instruction.size; i < len; ++i) {
            Char ch = instruction[i];
            if (!isChar(ch)) {
                return False;
            }
            if (ch == '>' && i > 0 && instruction[i-1] == '?') {
                return False;
            }
        }
        return True;
    }

    /**
     * Determine if the provided
     * [EncodingDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-EncodingDecl) is valid for an
     * XML document.
     *
     *     EncodingDecl ::= S 'encoding' Eq ('"' EncName '"' | "'" EncName "'" )
     *     EncName      ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
     *
     * @param encoding  the XML EncodingDecl
     *
     * @return `True` iff the specified XML EncodingDecl is valid
     */
    static Boolean isValidEncoding(String encoding) {
        Int len = encoding.size;
        if (len > 0 && encoding[0].asciiLetter()) {
            for (Int i = 1; i < len; ++i) {
                switch (encoding[i]) {
                case 'A'..'Z', 'a'..'z', '0'..'9', '.', '_', '-':
                    break;
                default: return False;
                }
            }
            return True;
        }
        return False;
    }

    // ----- XML validation helpers ----------------------------------------------------------------

    /**
     * Determine if a character is a valid XML
     * ["Char"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Char).
     *
     * From section 2.2 Characters:
     *
     *     Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
     *
     * @param ch  the character to test
     *
     * @return `True` iff the specified character is a valid XML "Char"
     */
    static Boolean isChar(Char ch) {
        UInt32 codepoint = ch.codepoint;
        return codepoint <= 0xD7FF && (   codepoint >= 0x20 // <-- most common case (covers ASCII)
                                       || codepoint == 0x0A     // `/n`
                                       || codepoint == 0x09     // `/t`
                                       || codepoint == 0x0D)    // `/r`
            || 0xE000 <= codepoint <= 0xFFFD
            || 0x10000 <= codepoint <= 0x10FFFF;
    }

    /**
     * Determine if a character is a valid XML
     * ["S"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-S) construct, aka white space.
     *
     * From section 2.3 Common Syntactic Constructs:
     *
     *     White Space
     *     S ::= (#x20 | #x9 | #xD | #xA)+
     *
     * @param ch  the character to test
     *
     * @return `True` iff the specified character is a valid XML white space character
     */
    static Boolean isSpace(Char ch) {
        UInt32 codepoint = ch.codepoint;
        // the following is the equivalent of:
        //     return (ch == ' ') | (ch == '\t') | (ch == '\r') | (ch == '\n');
        //                                                 2               1               0
        //                                                 0FEDCBA9876543210FEDCBA9876543210
        return codepoint <= 0x20 && Int:1 << codepoint & 0b100000000000000000010011000000000 != 0;
    }

    /**
     * Determine if a character is a valid XML
     * ["NameStartChar"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Name).
     *
     * @param ch  the character to test
     *
     * @return `True` iff the character is a valid XML "NameStartChar"
     */
    static Boolean isNameStart(Char ch) {
        Int cp = ch.codepoint;
        if (cp < 0x80) {
            // the following is the equivalent of:
            //     return 'A' <= ch <= 'Z' || 'a' <= ch <= 'z' || ch == ':' || ch == '_';
            Int cm = 1 << 0x3F - (cp & 0x3F);
            return 0 !=
                    //                                                                                         :
                    //                               0               1               2               3
                    //                               0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    (~cp & 0x40 << 57 >> 63 & cm & 0b0000000000000000000000000000000000000000000000000000000000100000)

                    //                                 ABCDEFGHIJKLMNOPQRSTUVWXYZ    _ abcdefghijklmnopqrstuvwxyz                                                   :
                    //                                4               5               6               7
                    //                                0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    + (cp & 0x40 << 57 >> 63 & cm & 0b0111111111111111111111111110000101111111111111111111111111100000);
        }

        return switch (cp) {
            case 0x00C0..0x00D6:
            case 0x00D8..0x00F6:
            case 0x00F8..0x02FF:
            case 0x0370..0x037D:
            case 0x037F..0x1FFF:
            case 0x200C..0x200D:
            case 0x2070..0x218F:
            case 0x2C00..0x2FEF:
            case 0x3001..0xD7FF:
            case 0xF900..0xFDCF:
            case 0xFDF0..0xFFFD:
            case 0x10000..0xEFFFF: True;
            default:               False;
        };
    }

    /**
     * Determine if a character is a valid XML
     * ["NameChar"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Name).
     *
     * @param ch  the character to test
     *
     * @return `True` iff the character is a valid XML "NameChar"
     */
    static Boolean isNameChar(Char ch) {
        Int cp = ch.codepoint;
        if (cp < 0x80) {
            // the following is the equivalent of:
            //     return 'A' <= ch <= 'Z' || 'a' <= ch <= 'z' || '0' <= ch <= '9'
            //             || ch == ':' || ch == '_' || ch == '-' || ch == '.';
            Int cm = 1 << 0x3F - (cp & 0x3F);
            return 0 !=
                    //                                                                            -. 0123456789:
                    //                               0               1               2               3
                    //                               0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    (~cp & 0x40 << 57 >> 63 & cm & 0b0000000000000000000000000000000000000000000001101111111111100000)

                    //                                 ABCDEFGHIJKLMNOPQRSTUVWXYZ    _ abcdefghijklmnopqrstuvwxyz                                                   :
                    //                                4               5               6               7
                    //                                0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    + (cp & 0x40 << 57 >> 63 & cm & 0b0111111111111111111111111110000101111111111111111111111111100000);
        }

        return switch (cp) {
            case 0x00B7:
            case 0x00C0..0x00D6:
            case 0x00D8..0x00F6:
            case 0x00F8..0x037D:
            case 0x037F..0x1FFF:
            case 0x200C..0x200D:
            case 0x203F..0x2040:
            case 0x2070..0x218F:
            case 0x2C00..0x2FEF:
            case 0x3001..0xD7FF:
            case 0xF900..0xFDCF:
            case 0xFDF0..0xFFFD:
            case 0x10000..0xEFFFF: True;
            default:               False;
        };
    }

    /**
     * Test if the character is an open/close quote character in XML.
     *
     * @param ch  the character to test
     *
     * @return `True` iff the character is a valid XML quote character
     */
    static Boolean isQuote(Char ch) {
        return ch == '\'' || ch == '\"';
    }

    // ----- XML output ----------------------------------------------------------------------------

    /**
     * Write an XML element.
     *
     * @param writer       the [Writer] to emit output to
     * @param name         the element name
     * @param value        the optional element value
     * @param attributes   the optional element attributes
     * @param writeNested  the optional list of child elements, each represented by a function that
     *                     will write a child element
     *
     * @return the writer
     */
    static Writer writeElement(Writer                      writer,
                               String                      name,
                               String?                     value       = Null,
                               Map<String, String>         attributes  = [],
                               List<function void(Writer)> writeNested = []) {
        // opening element
        writer.add('<');
        name.appendTo(writer);

        // add any attributes
        Attrs: for ((String attrName, String attrValue) : attributes) {
            if (!Attrs.first) {
                writer.add(',');
            }
            writer.add(' ');
            writeAttribute(writer, attrName, attrValue);
        }

        // close the opening element
        if (value == Null && writeNested.empty) {
            // use the short form `<x/>` and don't use a separate closing tag e.g. `<x></x>`
            return writer.add('/')
                         .add('>');
        }
        writer.add('>');

        // for the element value, escape just `<` and `&` chars
        if (value != Null) {
            writeData(writer, value);
        }

        // write any child elements
        for (function void(Writer) writeChild : writeNested) {
            writeChild(writer);
        }

        // add the closing element
        return writer.add('<')
                     .add('/')
                     .addAll(name.chars)
                     .add('>');
    }

    /**
     * Write an XML attribute, which is a name/value pair.
     *
     * @param writer  the [Writer] to emit output to
     * @param name    the attribute name
     * @param value   the attribute value
     *
     * @return the writer
     */
    static Writer writeAttribute(Writer writer, String name, String value) {
        name.appendTo(writer);
        writer.add('=');
        // select single or double quotes: for long strings, just use double quotes and escape any
        // double quotes in the value; for short strings, use double quotes unless the value
        // contains double quotes but does not contain single quotes
        Char quote = 0 < value.size <= 64 && value.indexOf('\"') && !value.indexOf('\'') ? '\'' : '\"';
        writer.add(quote);
        for (Char ch : value) {
            switch (ch) {
            case '\'':
                if (quote == ch) {
                    "&apos;".appendTo(writer);
                } else {
                    writer.add(ch);
                }
                break;
            case '\"':
                if (quote == ch) {
                    "&quot;".appendTo(writer);
                } else {
                    writer.add(ch);
                }
                break;
            case '<':
                "&lt;".appendTo(writer);
                break;
            case '&':
                "&amp;".appendTo(writer);
                break;
            default:
                writer.add(ch);
                break;
            }
        }
        writer.add(quote);
        return writer;
    }

    /**
     * Write an XML element's CharData.
     *
     * @param writer  the [Writer] to emit output to
     * @param text    the CharData text content
     *
     * @return the writer
     */
    static Writer writeData(Writer writer, String text) {
        Int of  = 0;
        Int len = text.size;

        // skip leading and trailing whitespace (using the broad Unicode definition of whitespace)
        while (of < len && text[of].isWhitespace()) {
            ++of;
        }
        while (len > of && text[len-1].isWhitespace()) {
            --len;
        }

        for ( ; of < len; ++of) {
            switch (Char ch = text[of]) {
            case '<':
                "&lt;".appendTo(writer);
                break;
            case '&':
                "&amp;".appendTo(writer);
                break;
            case '>':
                if (of >= 2 && text[of-1] == ']' && text[of-2] == ']') {
                    // weird XML spec detail: "The right angle bracket (>) may be represented
                    // using the string "&gt;", and MUST, for compatibility, be escaped using
                    // either "&gt;" or a character reference when it appears in the string
                    // "]]>" in content", and "In the content of elements, character data is any
                    // string of characters which does not contain the start-delimiter of any
                    // markup and does not include the CDATA-section-close delimiter, "]]>""
                    "&gt;".appendTo(writer);
                } else {
                    writer.add(ch);
                }
                break;
            default:
                writer.add(ch);
                break;
            }
        }
        return writer;
    }

    /**
     * Write an XML CDATA section.
     *
     * @param writer   the [Writer] to emit output to
     * @param content  the text content of the CDATA section
     *
     * @return the writer
     */
    static Writer writeCData(Writer writer, String content) {
        "<![CDATA[".appendTo(writer);
        content.appendTo(writer);
        "]]>".appendTo(writer);
        return writer;
    }

    /**
     * Write an XML comment.
     *
     * @param writer   the [Writer] to emit output to
     * @param comment  the text of the comment
     *
     * @return the writer
     */
    static Writer writeComment(Writer writer, String comment) {
        "<!--".appendTo(writer);
        if (comment.empty) {
            writer.add(' ');
        } else {
            comment.appendTo(writer);
            if (comment.endsWith('-')) {
                writer.add(' ');
            }
        }
        "-->".appendTo(writer);
        return writer;
    }

    /**
     * Write an XML processing instruction.
     *
     * @param writer       the [Writer] to emit output to
     * @param target       the target of the processing instruction
     * @param instruction  the text of the processing instruction
     *
     * @return the writer
     */
    static Writer writeInstruction(Writer writer, String target, String instruction) {
        writer.add('<')
              .add('?');
        target.appendTo(writer);
        if (!instruction.empty) {
            writer.add(' ');
            instruction.appendTo(writer);
        }
        return writer.add('?')
                     .add('>');
    }
}