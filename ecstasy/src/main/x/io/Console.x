/**
 * An injectable character based device.
 */
interface Console
    {
    /**
     * Print the string produced by the {@link Object#toString) method for the specified object.
     */
    void print(Object o);

    /**
     * Print the string produced by the {@link Object#toString) method for the specified object
     * followed by the line separator string.
     */
    void println(Object o = "");

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
    }
