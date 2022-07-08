import text.Log;

/**
 * A Log implementation that delegates to a Console.
 *
 * @param console  the Console to log to
 */
class ConsoleLog(Console console)
        implements Log
    {
    construct(Console console)
        {
        this.console = console;
        }
    finally
        {
        if (&console.isService || &console.isImmutable)
            {
            makeImmutable();
            }
        }

    private Console console;

    @Override
    ConsoleLog add(String v)
        {
        console.println(v);
        return this;
        }
    }