module TestSimple {
    @Inject Console console;

    void run() {
        function void(String) writePointer = &addPointerReference("name", _);
    }

    Iterator<Int> test(Int next) { // the name collision used to assert the compiler
        return new Iterator<Int>() {
            private Int nextValue = next;
            private Boolean hasNext = True;

            @Override
            conditional Element next() {
                if (hasNext) {
                    Int value = nextValue;
                    hasNext = False;
                    return True, value;
                } else {
                    return False;
                }
            }
        };
    }
}