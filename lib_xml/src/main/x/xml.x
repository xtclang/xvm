/**
 * TODO
 */
module xml.xtclang.org {
    package convert import convert.xtclang.org;
    import convert.Format;

    /**
     * Create an XML [Document] from the passed XML text.
     *
     * TODO add error logging
     *
     * @param text  a `String` containing an XML document
     *
     * @return `True` iff the `String` contained a valid XML document
     * @return (optional) the parsed XML [Document]
     */
    conditional Document parse(String text) {
        TODO
    }

    /**
     * Create an XML [Document] from the passed XML text.
     *
     * TODO add optional DTD etc. params
     *
     * @param name  the name of the top level XML element
     *
     * @return a new XML [Document] with a root element of the specified name
     */
    Document create(String name) {
        TODO
    }

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
        return isValidName(target) && !ecstasy.collections.CaseInsensitive.areEqual(target, "xml");
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

    // ----- XML validation helpers ----------------------------------------------------------------

    /**
     * Determine if a character is a valid XML
     * ["Char"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Char).
     *
     * @param ch  the character to test
     *
     * @return `True` iff the specified character is a valid XML "Char"
     */
    static Boolean isChar(Char ch) = switch (ch.codepoint) {
        case 0x09:              True;
        case 0x0A:              True;
        case 0x0D:              True;
        case 0x20..0xD7FF:      True;
        case 0xE000..0xFFFD:    True;
        case 0x10000..0x10FFFF: True;
        default:                False;
    };

    /**
     * Determine if a character is a valid XML
     * ["S"](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-S) construct, aka white space.
     *
     * @param ch  the character to test
     *
     * @return `True` iff the specified character is a valid XML white space character
     */
    Boolean isSpace(Char ch) {
        return (ch == ' ') | (ch == '\t') | (ch == '\r') | (ch == '\n');
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
        if (ch.ascii) {
            // the following is the equivalent of:
            //     return 'A' <= ch <= 'Z' || 'a' <= ch <= 'z' || ch == ':' || ch == '_';
            Int cp = ch.codepoint;
            Int cm = 1 << 0x3F - (cp & 0x3F);
            return 0 !=
                    //                                                                                        :
                    //                              0               1               2               3
                    //                              0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    ~cp & 0x40 << 57 >> 63 & cm & 0b0000000000000000000000000000000000000000000000000000000000100000

                    //                                ABCDEFGHIJKLMNOPQRSTUVWXYZ    _ abcdefghijklmnopqrstuvwxyz                                                   :
                    //                               4               5               6               7
                    //                               0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    + cp & 0x40 << 57 >> 63 & cm & 0b0111111111111111111111111110000101111111111111111111111111100000;
        }

        return switch (ch.codepoint) {
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

            default: False;
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
        if (ch.ascii) {
            // the following is the equivalent of:
            //     return 'A' <= ch <= 'Z' || 'a' <= ch <= 'z' || '0' <= ch <= '9'
            //             || ch == ':' || ch == '_' || ch == '-' || ch == '.';
            Int cp = ch.codepoint;
            Int cm = 1 << 0x3F - (cp & 0x3F);
            return 0 !=
                    //                                                                           -. 0123456789:
                    //                              0               1               2               3
                    //                              0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    ~cp & 0x40 << 57 >> 63 & cm & 0b0000000000000000000000000000000000000000000001101111111111100000

                    //                                ABCDEFGHIJKLMNOPQRSTUVWXYZ    _ abcdefghijklmnopqrstuvwxyz                                                   :
                    //                               4               5               6               7
                    //                               0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
                    + cp & 0x40 << 57 >> 63 & cm & 0b0111111111111111111111111110000101111111111111111111111111100000;
        }

        return switch (ch.codepoint) {
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

            default: False;
        };
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
            for (Char ch : value) {
                if (ch == '<') {
                    "&lt;".appendTo(writer);
                } else if (ch == '&') {
                    "&amp;".appendTo(writer);
                } else {
                    writer.add(ch);
                }
            }
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