/**
 * Generic variance and inherited member lookup must agree across cold and repeated applications.
 */
module MetadataQueries {
    void run() {
        Source<String> strings = new Box<String>("value");
        Source<Object> objects = strings;
        assert objects.get() == "value";
        assert strings.get() == "value";

        Collector<Object> collector = new Collector<Object>();
        Sink<String> stringsOnly = collector;
        stringsOnly.put("first");
        stringsOnly.put("second");
        assert collector.values.size == 2;
        assert collector.values[0] == "first";
        assert collector.values[1] == "second";

        Source<Int> numbers = new Box<Int>(42);
        assert numbers.get() == 42;
        assert strings.get() == "value";

        // Rendering a shared core value also constructs a Ref in the executing frame's owner.
        assert new Exception("metadata").toString().startsWith("Exception: metadata");
    }

    interface Source<Element> {
        Element get();
    }

    interface Sink<Element> {
        void put(Element value);
    }

    class Box<Element>(Element value)
            implements Source<Element> {
        @Override
        Element get() = value;
    }

    class Collector<Element>
            implements Sink<Element> {
        Element[] values = new Element[]();

        @Override
        void put(Element value) {
            values.add(value);
        }
    }
}
