/**
 * Tests for the Ecstasy StringBuffer class.
 */
module StringBufferTest {
    @Inject static Console console;
    static void out(Object? o = Null) {
        console.print(o);
    }

    void run(String[] params = []) {
        if (params.empty) {
            append1();
            append2();
            addDup();
            trunc();
            clear();
            setElement();
        }

        randomize(params.empty ? 0 : Int.parse(params[0]) ?: 0);
    }

    static void append1() {
        val buf = new StringBuffer();
        buf.add('x');
        buf.addAll("yz");
        verify(buf, "xyz");
    }

    static void append2() {
        StringBuffer buf     = new StringBuffer();
        String       control = "";
        for (Int i : 1..300) {
            control = bufAddAll(buf, i.toString().dup(i), control);
        }
        verify(buf, control);

        // some extra checks testing indexOf/lastIndexOf
        for (Char ch : '0'..'9') {
            bufIndexOf(buf, ch, control);
            bufLastIndexOf(buf, ch, control);
        }
    }

    static void addDup() {
        for (Int i : 1..65) {
            StringBuffer buf     = new StringBuffer();
            String       control = "";
            for (Char ch : 'a'..'z') {
                control = bufAddDup(buf, 'a', i, control);
            }
            verify(buf, control);
        }
    }

    static void trunc() {
        verify(new StringBuffer().add('a').truncate(1), "a");
        verify(new StringBuffer().add('a').truncate(0), "");
        verify(new StringBuffer().addAll("abc").truncate(3), "abc");
        verify(new StringBuffer().addAll("a").truncate(1), "a");
    }

    static void clear() {
        verify(new StringBuffer().add('a').clear(), "");
        verify(new StringBuffer().addAll("abc").clear(), "");
    }

    static void setElement() {
        StringBuffer buf     = new StringBuffer();
        String       control = "";
        control = bufAddAll(buf, "abcdefghijklmnopqrstuvwxyz", control);
        control = bufSetElement(buf, 13, 'q', control);
        control = bufSetElement(buf, 0, '*', control);
        control = bufSetElement(buf, 25, '!', control);
        verify(buf, control);
    }


    // ----- random testing -----

    static void randomize(Int seed) {
        import ecstasy.numbers.PseudoRandom;
        Random rnd = new PseudoRandom(seed.toUInt());
        if (seed == 0) {
            seed = rnd.int(MaxValue);
            rnd  = new PseudoRandom(seed.toUInt());
            out($"Random seed: {seed.toNibbleArray()}");
        }

        String AllChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        function Char()         rndChar   = () -> AllChars[rnd.int(AllChars.size)];
        function Char[]()       rndChars  = () -> new Char[rnd.int(100)](_ -> rndChar());
        function StringBuffer() rndBuf    = () -> new StringBuffer().addAll(rndChars());
        function String()       rndString = () -> new String(rndChars());
        function Int()          rndIndex  = () -> rnd.int(1+rnd.int(200));

        @Inject Timer timer;
        timer.start();
        while (timer.elapsed < Duration:10m) {
            Step[] steps = new Step[];
            Int    count = rnd.int(1+rnd.int(40));
            for (Int i : 1..count) {
                Step step;
                step = switch (rnd.int(97)) {
                    default:
                    case  0..39: new Add(rndChar());
                    case 40..44: new AddAll(rndChars());
                    case 45..49: new AddAll(rndBuf());
                    case 50..54: new AddAll(rndString().as(Iterable<Char>));
                    case 55..59: new Append(rndChar());
                    case 60..64: new Append(rndChars());
                    case 65..69: new Append(rndBuf());
                    case 70..74: new Append(rndString().as(Object));
                    case 75..76: new Append(rnd.int(10000));
                    case 77..78: new Append(new Date(2000 + rnd.int(30), 1+rnd.int(12), 1+rnd.int(28)));
                    case 79..80: new Append(new TimeOfDay(rnd.int(24), rnd.int(60), rnd.int(60)));
                    case 81..85: new GetElement(rndIndex());
                    case 86..89: new SetElement(rndIndex(), rndChar());
                    case 90..94: new Slice(rndIndex()..rndIndex());
                    case 95:     new Clear();
                    case 96:     new Truncate(rndIndex());
                };
                steps.add(step);
            }
            execute(steps);
        }
    }

    static void execute(Step[] steps) {
        try {
            StringBuffer buf     = new StringBuffer();
            String       control = "";
            Steps: for (Step step : steps) {
                try {
                    (buf, control) = step.process(buf, control);
                } catch (Exception e) {
                    out($"Exception in test at step #{Steps.count}: {step}");
                    out($"Control={control.quoted()}");
                    out($"Buffer ={buf.toString().quoted()}");
                    throw e;
                }
            }
            verify(buf, control);
        } catch (Exception e) {
            out($"Exception: {e}");
            showTest(steps);
            throw e;
        }
    }

    static void showTest(Step[] steps) {
        out("Test listing:");
        Steps: for (Step step : steps) {
            out($"[{Steps.count}] {step}");
        }
    }

    static Step[] parseTest(String test) {
        // TODO
        TODO
    }

    @Abstract class Step implements Destringable {
        @Abstract (StringBuffer buf, String control) process(StringBuffer buf, String control);
        @Abstract @Override String toString();
    }

    class Add(Char ch) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            return buf, bufAdd(buf, ch, control);
        }
        @Override String toString() = $".add({ch.quoted()})";
    }

    class AddAll(Iterable<Char> chars) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            return buf, bufAddAll(buf, chars, control);
        }
        @Override String toString() = $".addAll(new {&chars.type}({new String(chars.toArray()).quoted()}))";
    }

    class Append(Object o) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            return buf, bufAppend(buf, o, control);
        }
        @Override String toString() = $".append(new {&o.type}({o.is(Iterable<Char>) ? new String(o.as(Iterable<Char>).toArray()).quoted() : o.toString().quoted()}))";
    }

    class GetElement(Int index) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            Char? ch = Null;
            try {
                ch = buf[index];
            } catch (Exception e) {
                assert index < 0 || index >= control.size;
            }
            if (ch != Null) {
                assert 0 <= index < control.size && ch == control[index];
            }
            return buf, control;
        }
        @Override String toString() = $"[{index}]";
    }

    class SetElement(Int index, Char ch) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            Boolean set;
            try {
                buf[index] = ch;
                set = True;
            } catch (Exception e) {
                assert index < 0 || index > control.size
                        as $"Unexpected StringBuffer setElement failure: {e}";
                set = False;
            }
            return buf, set ? control[0..<index] + ch + control.substring(index+1) : control;
        }
        @Override String toString() = $"[{index}]={ch.quoted()}";
    }

    class Slice(Range<Int> range) extends Step {
        @Override construct(String s) = TODO();
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            String? slice = Null;
            try {
                slice = buf[range];
            } catch (Exception e) {
                assert range.effectiveLowerBound < 0 || range.effectiveUpperBound >= control.size
                        as $"Unexpected StringBuffer slice[{range}] failure: {e}";
            }
            if (slice != Null) {
                if (range.empty) {
                    assert slice == "";
                } else {
                    assert 0 <= range.effectiveLowerBound
                            && range.effectiveUpperBound < control.size
                            && slice == control[range];
                }
            }
            return buf, control;
        }
        @Override String toString() = $"[{range}]";
    }

    class Clear extends Step {
        construct() {}
        @Override construct(String s) {}
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            return buf.clear(), "";
        }
        @Override String toString() = ".clear()";
    }

    class Truncate(Int size) extends Step {
        @Override construct(String s) {}
        @Override (StringBuffer buf, String control) process(StringBuffer buf, String control) {
            Boolean truncated = False;
            try {
                buf.truncate(size);
                truncated = True;
            } catch (Exception e) {
                assert size < 0 || size > control.size
                        as $"Unexpected StringBuffer truncate({size}) failure: {e}";
            }
            return buf, truncated ? control[0..<size] : control;
        }
        @Override String toString() = $".truncate({size})";
    }


    // ----- internal helpers -----

    static String bufAppend(StringBuffer buf, Object o, String control) {
        buf.append(o);
        return control.add(o);
    }
    static String bufAddDup(StringBuffer buf, Char ch, Int count, String control) {
        buf.addDup(ch, count);
        return control.add(ch.toString().dup(count));
    }
    static conditional Int bufIndexOf(StringBuffer buf, Char ch, String control) {
        if (Int offset := buf.indexOf(ch)) {
            assert Int check := control.indexOf(ch), check == offset;
            return True, offset;
        } else {
            assert !control.indexOf(ch);
            return False;
        }
    }
    static conditional Int bufLastIndexOf(StringBuffer buf, Char ch, String control) {
        if (Int offset := buf.lastIndexOf(ch)) {
            assert Int check := control.lastIndexOf(ch), check == offset;
            return True, offset;
        } else {
            assert !control.indexOf(ch);
            return False;
        }
    }
    static String bufTruncate(StringBuffer buf, Int size, String control) {
        try {
            buf.truncate(size);
            return control[0..<size];
        } catch (Exception e) {
            assert size < 0 || size > control.size;
            return control;
        }
    }
    static String bufClear(StringBuffer buf, String control) {
        buf.clear();
        return "";
    }
    static String bufAdd(StringBuffer buf, Char ch, String control) {
        buf.add(ch);
        return control + ch;
    }
    static String bufAddAll(StringBuffer buf, Iterable<Char> iterable, String control) {
        buf.addAll(iterable);
        for (Char ch : iterable) {
            control += ch;
        }
        return control;
    }
    static String bufSetElement(StringBuffer buf, Int index, Char value, String control) {
        buf[index] = value;
        return control[0..<index] + value + control.substring(index+1);
    }

    static Boolean verify(StringBuffer buf, String control) {
        Iterator<Char> iter = buf.iterator();
        Each: while (Char ch := iter.next()) {
            assert control[Each.count] == ch;
            assert buf[Each.count] == ch;
        }

        assert buf.size == control.size && buf.toString() == control;
        // toString() modifies the StringBuffer state; verify the test
        assert buf.size == control.size && buf.toString() == control;
        return True;
    }
}