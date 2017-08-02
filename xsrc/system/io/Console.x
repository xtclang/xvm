/**
 * An injectable character based device.
 */
interface Console
    {
    /**
     * Print the string produced by the {@link Object#toString) method for the specified object.
     */
    Void print(Object o);

    /**
     * Print the string produced by the {@link Object#toString) method for the specified object
     * followed by the line separator string.
     */
    Void println(Object o = "");

    /**
    * Read a single text line from the console.
    */
    String readLine();

    /**
    * Turn the line input echo on or off.
    *
    * @return the previous echo value
    */
    Boolean echo(Boolean flag);

    // well-known (injectable) implementations
    static class TerminalConsole
            implements Console
        {
        // TODO: how to remove it?
        Void print(Object o);
        Void println(Object o);
        String readLine();
        Boolean echo(Boolean f);
        }
    }
