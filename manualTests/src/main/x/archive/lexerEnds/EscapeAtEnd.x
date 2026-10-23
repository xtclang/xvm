/**
 * A source that ends inside an escape in a string: manualTests' runLexerEnds compiles this file
 * without its last line end, so that the source ends right after the last line below. The compiler
 * failed with NoSuchElementException; it must report LEXER-08 once and stop.
 */
module EscapeAtEnd {
    String s = "abc\
