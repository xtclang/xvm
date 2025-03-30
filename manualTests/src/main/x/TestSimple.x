module TestSimple {
    @Inject Console console;

    void run() {
        Char[] chars = ['A', 'B', 'C'].toArray(mutability=Fixed);

        Int offset = 0;
        if (!(chars[offset++] := next())) { // this used to throw at runtime
            offset = 1;
        }
        console.print(chars);
    }

    conditional Char next() = (True, 'a');
}