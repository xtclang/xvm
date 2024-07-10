module TestSimple {
    @Inject Console console;

    void run() {
        Base base1 = new Base(); // implicit Base1<Object>
        console.print(base1.add(base1));    // this used to blow up
        console.print(base1.add(Int:1471));

        // Base base2 = new Base<Int>(); // this should not compile as there's no @Override
                                         // for MixIn.add(Int)
    }

    mixin MixIn<Element> into Base<Element> {
        String add(Int i) = assert as "Must not be called";
    }

    class Super<Element> {
        String add(Element e) = $"S: {e}";
    }

    class Base<Element> extends Super<Element>
        incorporates MixIn<Element> {
        @Override String add(Element e) =
            $"B: {super(e)}"; // this used to "super" to MixIn.add(), which it should not
    }
}