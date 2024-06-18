module CompilerBug {
    @Inject Console console;

    void run() {
    }

    class Base<Element> // this used to crash the compiler with an infinite loop
            incorporates MixIn<Element> {

        @Override
        void add(Element e) {
            console.print($"Base2 {e=}");
            super(e);
        }
    }

    mixin MixIn<Element>
            into Base<Element> {
        void add(Element e) {
            console.print($"MixIn2 {e=}");
        }
    }
}
