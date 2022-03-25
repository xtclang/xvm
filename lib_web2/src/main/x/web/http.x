package http
    {
    /**
     * Validate the passed string as a file extension. This is based on the HTTP token definition,
     * as illustrated in [validToken], but with a reduced set of non-alpha-numeric characters, based
     * on the characters that ASCII DOS allowed back in the dark ages.
     *
     * @param a String that may be a file extension (but not including the dot)
     *
     * @return True iff the string is, in theory, a valid file extension
     */
    static Boolean validExtension(String s)
        {
        for (Char ch : s)
            {
            switch (ch)
                {
                case 'a'..'z':
                case 'A'..'Z':
                case '0'..'9':
                // this is the intersection of allowed special characters between the HTTP Token
                // specification and the ASCII DOS specification (which file systems often respect)
                case  '!', '#', '$', '%', '&', '-', '^', '_', '`', '~':
                    break;

                default:
                    return False;
                }
            }

        return s.size >= 1;
        }

    /**
     * Validate the passed string as an HTTP "token".
     *
     *     token = 1*tchar
     *     tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                 / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                 / DIGIT / ALPHA
     *                 ; any VCHAR, except delimiters
     *
     * @param a String that may be an HTTP token
     *
     * @return True iff the string is a valid HTTP token
     */
    static Boolean validToken(String s)
        {
        for (Char ch : s)
            {
            switch (ch)
                {
                case 'a'..'z':
                case 'A'..'Z':
                case '0'..'9':
                case  '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~':
                    break;

                default:
                    return False;
                }
            }

        return s.size >= 1;
        }

    /**
     * TODO
     *
     *     token          = 1*tchar
     *     tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
     *                      / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
     *                      / DIGIT / ALPHA
     *                      ; any VCHAR, except delimiters
     *     quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE
     *     qdtext         = HTAB / SP /%x21 / %x23-5B / %x5D-7E / obs-text
     *     obs-text       = %x80-FF
     */
    conditional String validTokenOrQuotedString(String s)
        {
        if (s.startsWith('\"'))
            {
            return http.validToken(s), s;
            }

        Int length = s.size;
        if (length < 2 || !s.endsWith('\"'))
            {
            return False;
            }

        TODO check char [1..length-1)
        }
    }