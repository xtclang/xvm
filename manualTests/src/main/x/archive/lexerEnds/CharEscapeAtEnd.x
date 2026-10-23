/**
 * A source that ends inside an escape in a character: manualTests' runLexerEnds compiles this file
 * without its last line end, so that the source ends right after the last line below. The compiler
 * failed with NoSuchElementException; it must report LEXER-05 once and stop.
 */
module CharEscapeAtEnd {
    Char c = '\
