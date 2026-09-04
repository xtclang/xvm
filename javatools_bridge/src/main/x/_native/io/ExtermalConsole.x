/**
 * Simple external console.
 */
service ExternalConsole
        extends TerminalConsole {
    @Override
    String toString() {
        return "ExternalConsole";
    }
}
