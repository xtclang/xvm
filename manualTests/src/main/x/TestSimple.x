module TestSimple {
    @Inject Console console;

    void run() {

        Collection<String> strings = ["a", "b", "c"];
        Derived<String> d = new Derived(strings);

        assert d.size == 3;
        assert d.contains("a"); // used to assert at run-time
        }

    class Base<Element>(Collection<Element> original)
        implements Collection<Element>
        delegates Collection<Element>(original) {

        void test()   {
            new Base<Element>(original);
        }
    }

    class Derived<Element>(Collection<Element> original)
        extends Base<Element>(original) {

        @Override
        Boolean contains(Element el) {
            return super(el);
        }
    }
}