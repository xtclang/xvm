/**
 * Simple console.
 */
service TerminalConsole
        implements Console
    {
    @Override
    void print(Object o);

    @Override
    void println(Object o = "");

    @Override
    String readLine();

    @Override
    Boolean echo(Boolean flag);
    }