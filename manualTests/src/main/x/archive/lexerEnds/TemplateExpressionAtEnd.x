/**
 * A source that ends inside a template expression: manualTests' runLexerEnds compiles this file
 * without its last line end, so that the source ends right after the last line below. The compiler
 * failed with NoSuchElementException; it must report LEXER-08 once and stop.
 */
module TemplateExpressionAtEnd {
    String s = $"abc{x
