/**
 * Simple terminal-based console.
 */
service TerminalConsole
        implements Console {
    @Override
    void print(Object object = "", Boolean suppressNewline = False) {TODO("native");}

    @Override
    String readLine(String prompt = "", Boolean suppressEcho = False) {TODO("native");}

    @Override
    String toString() {
        return "TerminalConsole";
    }
}