/** Generated method and accessor bodies belong to each application execution. */
module RuntimeDelegation {
    void run() {
        Cell<Int> cell = new Cell<Int>(3);
        Value<Int> numbers = new Proxy<Int>(cell);
        assert numbers.value == 3;
        writeInt(numbers, 7);
        assert cell.value == 7;
        assert numbers.echo(11) == 11;
        numbers.update(13);
        assert numbers.value == 13;
        (Int left, Int right) = numbers.pair(17);
        assert left == 13;
        assert right == 17;
        function Int(Int) echo = numbers.echo;
        assert echo(19) == 19;
        assert numbers.identity("generic method") == "generic method";

        Value<String> words = new Proxy<String>(new Cell<String>("first"));
        assert words.value == "first";
        writeString(words, "second");
        assert words.echo("third") == "third";
        assert words.value == "second";
        assert words.identity(53) == 53;
        assert numbers.value == 13;

        Value<Int> inherited = new Derived<Int>(new Cell<Int>(23));
        writeInt(inherited, 29);
        assert inherited.echo(inherited.value) == 29;

        AtomicProxy atomic = new AtomicProxy(new Cell<Int>(31));
        atomic.update(37);
        assert atomic.echo(41) == 41;
        (Int a, Int b) = atomic.pair(43);
        assert a == 37;
        assert b == 43;

        Named first = new Named(5);
        Named same = new Named(5);
        Named later = new Named(6);
        assert first == same;
        assert first < later;
        assert Named.hashCode(first) == Named.hashCode(same);
        assert $"{first}" == "named";
        StringBuffer buffer = new StringBuffer();
        first.appendTo(buffer);
        assert buffer.toString() == "named";
        assert first.estimateStringLength() == 0;
    }

    void writeInt(Value<Int> target, Int input) { target.value = input; }
    void writeString(Value<String> target, String input) { target.value = input; }

    interface Value<Element> {
        Element value;
        Element echo(Element input);
        <Item> Item identity(Item input);
        void update(Element input);
        (Element, Element) pair(Element input);
    }

    class Cell<Element> implements Value<Element> {
        construct(Element initial) { value = initial; }
        @Override Element value;
        @Override Element echo(Element input) = input;
        @Override <Item> Item identity(Item input) = input;
        @Override void update(Element input) { value = input; }
        @Override (Element, Element) pair(Element input) = (value, input);
    }

    class Proxy<Element>(Value<Element> target) delegates Value<Element>(target) {}

    class Derived<Element>(Value<Element> target) extends Proxy<Element>(target) {}

    class AtomicProxy delegates Value<Int>(target) {
        construct(Value<Int> initial) { target = initial; }
        @Atomic private Value<Int> target;
    }

    const Named(Int value) {
        @Override String toString() = "named";
    }
}
