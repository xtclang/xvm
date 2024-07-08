module TestSimple {
    @Inject Console console;

    void run() {
    }

    mixin MixIn<Element> into Base1<Element> {
        // this used to compile though it should have not - @Override is missing
        String add(Element e) = $"M: {e}";
    }

    class Super1<Element> {
        String add(Element e) = $"S: {e}";
    }
    class Base1<Element> extends Super1<Element>
        incorporates MixIn<Element> {
        @Override String add(Element e) = $"B: {super(e)}";
    }
}
