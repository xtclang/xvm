/**
 * Simple terminal-based console.
 */
service TerminalConsole
        implements Console {
    @Override
    void print(Object object= "", Boolean suppressNewline = False);

    @Override
    String readLine(Boolean suppressEcho = False);

    @Override
    String toString() {
        return "TerminalConsole";
    }
}