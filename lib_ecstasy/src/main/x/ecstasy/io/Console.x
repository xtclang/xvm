/**
 * An injectable character based device.
 */
interface Console
    {
    /**
     * Print the string produced by the {@link Object#toString) method for the specified object.
     *
     * @param the object to print
     */
    void print(Object o);

    /**
     * Print the string produced by the {@link Object#toString) method for the specified object
     * followed by the line separator string.
     *
     * @param (optional) the object to print
     */
    void println(Object o = "");

    /**
     * Read a single text line from the console.
     *
     * @param echo  (optional) flag indicating whether the input should be shown by the console
     *
     * @return the input string
     */
    String readLine(Boolean echo = True);
    }