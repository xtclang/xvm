/**
 * A console that captures test output without writing to the runner's console.
 */
service TestConsole
        implements Console {
    private StringBuffer buffer = new StringBuffer();

    @Override
    void print(Object object = "", Boolean suppressNewline = False) {
        buffer.addAll(object.toString());
        if (!suppressNewline) {
            buffer.add('\n');
        }
    }

    @Override
    String readLine(String prompt = "", Boolean suppressEcho = False) {
        throw new Unsupported();
    }

    String output() = buffer.toString();

    void reset() {
        buffer = new StringBuffer();
    }
}
