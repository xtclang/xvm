/**
 * Simple terminal-based console.
 */
service TerminalConsole
        implements Console
    {
    @Override
    void print(Object o);

    @Override
    void println(Object o = "");

    @Override
    String readLine(Boolean echo = True);

    @Override
    String toString()
        {
        return "TerminalConsole";
        }
    }