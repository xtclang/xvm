/**
 * A source that ends inside a template string: manualTests' runLexerEnds compiles this file
 * without its last line end, so that the source ends right after the last line below. The compiler
 * reported the unterminated string for ever and never stopped; it must report LEXER-08 once and
 * stop.
 */
module UnterminatedTemplate {
    String s = $"abc
