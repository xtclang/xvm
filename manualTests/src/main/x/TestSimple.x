module TestSimple {
    @Inject Console console;

    void run() {
        Int[] ints = [3, 1, 2];
        try {
            ints.sorted(inPlace=True); // this used to produce an out-of-place result; should throw
            console.print(ints);
        } catch (ReadOnly ignore) {
            console.print("Array is immutable");
        }

        console.print(ints.sorted());
    }
}
