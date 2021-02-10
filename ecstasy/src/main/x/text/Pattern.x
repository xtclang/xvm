/**
 * TODO JK: explain and doc API please
 */
interface Pattern
        extends immutable Const
    {
    @RO String pattern;

    Matcher match(String input);

    static Pattern compile(String pattern)
        {
        @Inject RegExp regExp;

        return regExp.compile(pattern);
        }

    /**
     * Returns a literal pattern String for the specified String.
     *
     * This method produces an expression String that can compiled to
     * a Pattern that would match the string `s` as if it were a literal
     * pattern. Metacharacters or escape sequences in the input String
     * will be given no special meaning.
     *
     * @param s  the String to be turned into a a literal pattern
     *
     * @return a literal string pattern
     */
    static String createLiteralPattern(String s)
        {
        if (Int slashEIndex := s.indexOf("\\E"))
            {
            Int          current = 0;
            StringBuffer sb      = new StringBuffer();
            sb.append("\\Q");
            do
                {
                sb.append(s[current..slashEIndex)).append("\\E\\\\E\\Q");
                current = slashEIndex + 2;
                }
            while (slashEIndex := s.indexOf("\\E", current));

            return sb.append(s[current..s.size - 1])
                    .append("\\E")
                    .toString();
            }
        else
            {
            return "\\Q" + s + "\\E";
            }
        }
    }