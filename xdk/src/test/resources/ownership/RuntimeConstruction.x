/**
 * Cold entry, generic construction and super-call return types must use runtime descriptors.
 */
module RuntimeConstruction {
    void run() {
        Derived<Int> numbers = new Derived<Int>(17);
        Derived<String> words = new Derived<String>("value");
        assert numbers.value() == 17;
        assert words.value() == "value";
        assert numbers.label == "base:derived";
        assert words.label == "base:derived";
        Tuple<Int, String> numberPair = numbers.pair();
        Tuple<String, String> wordPair = words.pair();
        assert numberPair.size == 2;
        assert numberPair[0] == 17;
        assert numberPair[1] == "base";
        assert wordPair.size == 2;
        assert wordPair[0] == "value";
        assert wordPair[1] == "base";
        assert numbers.child().read() == 17;
        assert words.child().read() == "value";

        Tuple<Int, String> pair = supply();
        assert pair.size == 2;
        assert pair[0] == 23;
        assert pair[1] == "supplied";
        Type generic = Derived;
        assert generic == Derived;
    }

    (Int, String) supply() {
        return 23, "supplied";
    }

    class Base<Element>(Element content) {
        Element value() = content;
        @RO String label.get() = "base";
        (Element, String) pair() {
            return content, "base";
        }

        Child child() = new Child();

        class Child {
            Element read() = content;
        }
    }

    class Derived<Element>(Element content)
            extends Base<Element>(content) {
        @Override
        Element value() {
            Element result = super();
            return result;
        }

        @Override
        @RO String label.get() = super() + ":derived";

        @Override
        (Element, String) pair() {
            Tuple<Element, String> result = super();
            return result;
        }

        @Override
        class Child {
            @Override
            Element read() = super();
        }
    }
}
