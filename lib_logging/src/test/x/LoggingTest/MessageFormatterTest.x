import logging.MessageFormatter;

/**
 * Tests for [MessageFormatter] — the SLF4J-shaped `{}` substitution + throwable promotion
 * helper. Cases are lifted from the SLF4J reference suite to keep behavioural parity
 * obvious.
 */
class MessageFormatterTest {

    @Test
    void shouldReturnPatternUnchangedWhenNoArgs() {
        (String formatted, Exception? cause) = MessageFormatter.format("hello world", []);
        assert formatted == "hello world";
        assert cause == Null;
    }

    @Test
    void shouldSubstituteSinglePlaceholder() {
        (String formatted, Exception? cause) = MessageFormatter.format("hello {}", ["world"]);
        assert formatted == "hello world";
        assert cause == Null;
    }

    @Test
    void shouldSubstituteMultiplePlaceholders() {
        (String formatted, _) = MessageFormatter.format("a={} b={} c={}", ["1", "2", "3"]);
        assert formatted == "a=1 b=2 c=3";
    }

    @Test
    void shouldLeaveExcessPlaceholdersLiteral() {
        (String formatted, _) = MessageFormatter.format("a={} b={}", ["only-one"]);
        assert formatted == "a=only-one b={}";
    }

    @Test
    void shouldDropExcessArguments() {
        (String formatted, _) = MessageFormatter.format("a={}", ["used", "extra"]);
        assert formatted == "a=used";
    }

    @Test
    void shouldEscapePlaceholderWithSingleBackslash() {
        // "\{}" — backslash is consumed, "{}" is emitted literally
        (String formatted, _) = MessageFormatter.format("escaped: \\{}", ["X"]);
        assert formatted == "escaped: {}";
    }

    @Test
    void shouldKeepLiteralBackslashWithDoubleEscape() {
        // "\\{}" — both backslashes consumed (one literal '\'), placeholder substitutes
        (String formatted, _) = MessageFormatter.format("\\\\{}", ["X"]);
        assert formatted == "\\X";
    }

    @Test
    void shouldHandleAdjacentPlaceholders() {
        (String formatted, _) = MessageFormatter.format("{}{}{}", ["a", "b", "c"]);
        assert formatted == "abc";
    }

    @Test
    void shouldPromoteTrailingException() {
        Exception boom = new Exception("boom");
        (String formatted, Exception? cause) =
                MessageFormatter.format("op failed: {}", ["disk", boom]);
        assert formatted == "op failed: disk";
        assert cause == boom;
    }

    @Test
    void shouldPromoteTrailingExceptionWithoutPlaceholder() {
        Exception boom = new Exception("boom");
        (String formatted, Exception? cause) =
                MessageFormatter.format("no placeholder", [boom]);
        assert formatted == "no placeholder";
        assert cause == boom;
    }

    @Test
    void shouldHandleStrayBraces() {
        (String formatted, _) = MessageFormatter.format("a { b } c {} d", ["X"]);
        assert formatted == "a { b } c X d";
    }

    @Test
    void shouldHandleEmptyPattern() {
        (String formatted, _) = MessageFormatter.format("", ["unused"]);
        assert formatted == "";
    }
}
