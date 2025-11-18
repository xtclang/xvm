module test {
    @Inject Console console;

    void run() {

        dump((protected Array_<String>));
    }

    class Array_<Element>
        implements ArrayDelegate_<Element>
        implements List_<Element> {

        @Override
        Array_ delete(Int index) = throw new ReadOnly();

        private static interface ArrayDelegate_<Element> {
            ArrayDelegate_ delete(Int index);
        }
    }

    interface List_<Element> {
        List_ delete(Int index) = throw new ReadOnly();
    }

    void dump(Type t) {
        console.print(t.dump());
    }

}