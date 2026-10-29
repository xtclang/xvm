/** Compiler-resolved highlighting, using the editor's own theme colors. */
module SemanticColors {
    enum Mode {Quiet, Loud}

    interface Reader { Int read(Int Count); }

    // Lowercase type and uppercase parameter are intentional: spelling is not identity.
    class counter implements Reader {
        Int count = 0;

        construct() {}

        @Override
        Int read(Int Count) {
            var next = Count + count;
            count += 1;
            return next;
        }
    }

    static counter create() = new counter();

    Int run() {
        counter item = create();
        Mode mode = Mode.Quiet;
        List<Int> values = [item.read(1)];
        return values[0];
    }
}
