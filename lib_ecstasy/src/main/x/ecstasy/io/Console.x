/**
 * Represents a text-based console, with both line-oriented text input, and text output.
 */
interface Console {
    /**
     * Outputs the textual form of the specified object to the console. The textual form of the
     * object is expected to be obtained via a call to the [Object.toString()] method.
     *
     * @param object           (optional) the object to output to the console
     * @param suppressNewline  (optional) pass True to prevent the automatic addition of a newline
     */
    void print(Object object = "", Boolean suppressNewline = False);

    /**
     * Read a line of user input from the console.
     *
     * @param suppressEcho  (optional) pass True to prevent the automatic display of typed input to
     *                      the console as it is typed
     *
     * @return the input string
     */
    String readLine(Boolean suppressEcho = False);
}