module readers {
    @Inject static Console console;
    @Inject Clock clock;
    @Inject Timer timer;

    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.CharArrayReader;
    import ecstasy.io.UTF8Reader;
    import ecstasy.io.TextPosition;
    import ecstasy.numbers.PseudoRandom;

    PseudoRandom rnd = new PseudoRandom(12345678901234567);

    void run(String[] args = []) {
        String[] inputs = [
            "",
            "this is a single line",
            "multiple\nlines\r\nwith\rvarious\r\nline\rterminators",
        ].toArray(Mutable);

        for (Int iter : 1..1000) {
            Int len = 1+rnd.int(1+rnd.int(250));
            StringBuffer buf = new StringBuffer(len);
            for (Int i : 0..<len) {
                switch (UInt32 n = rnd.int(58).toUInt32()) {
                case 0..25:
                    buf.append('A' + n);
                    break;
                case 26..35:
                    buf.append(0xC0 + n - 26);
                    break;
                case 36..44:
                    buf.append(0x98F + n - 36);
                    break;
                case 45..51:
                    buf.append(0x10192 + n - 45);
                    break;
                case 52:
                    buf.append(' ');
                    break;
                case 53:
                    buf.append('\r');
                    break;
                case 54..55:
                    buf.append("\r\n");
                    break;
                default:
                    buf.append('\n');
                    break;
                }
            }
            inputs += buf.toString();
        }

        function void(Reader)[] tests = [
            test1(_),
            test2(_),
            test3(_),
            test4(_),
            test5(_),
        ];

        for (String input : inputs) {
            console.print($"Testing: {input.quoted()}");
            for (val fn : tests) {
                Reader[] readers = [
                        new Control(input),
                        new CharArrayReader(input),
                        new UTF8Reader(new ByteArrayInputStream(input.utf8())),
                ];
                fn(new Tester(readers));
            }
        }
    }

    // TODO GG
    Char? TODO_GG(Char? ch) = ch;

    void test1(Reader r) {
        StringBuffer buf = new StringBuffer();
        while (True) {
            Boolean eof = r.eof;
            Char? ch1 = Null;
            Char? ch2 = Null;
            ch1 := r.peek();
            ch2 := r.next();
// TODO GG  assert eof == (ch1 == Null) == (ch2 == Null);
            assert eof && ch1 == Null && ch2 == Null || !eof && ch1 != Null && ch2 != Null;
            ch1 = TODO_GG(ch1); ch2 = TODO_GG(ch2); // TODO GG get rid of this
            assert ch1 == ch2;
            if (eof) {
                break;
            }

            r.rewind(1);
            assert Char ch3 := r.next();
            assert ch3 == ch2;

            if (r.offset >= 3) {
                r.rewind(3);
                assert r.next();
                assert r.next();
                assert Char ch4 := r.next();
                assert ch4 == ch2;
            }

            buf.add(ch1?);
        }
        String s = buf.toString();
        console.print($"{s=}");

        r.offset = 0;
        Char[] verify = new Char[];
        assert r.nextChars(verify, 0, s.size) == s.size;
        assert s.chars == verify;
        assert r.nextChars(verify, 0, s.size + 1) == s.size;
        assert s.chars == verify;
    }

    void test2(Reader r) {
        while (r.nextLine()) {}
    }

    void test3(Reader r) {
// TODO GG
//        val _ = r.eof;
//        val _ = r.remaining;
//        val _ = r.size;
//        val _ = r.remaining;
//        val _ = r.next();
//        val _ = r.remaining;

        val e  = r.eof;
        val r2 = r.remaining;
        val s  = r.size;
        val r3 = r.remaining;
        val n  = r.next();
        val r4 = r.remaining;
    }

    void test4(Reader r) {
        r.advance(15);
        r.next();
        r.rewind(3);
        r.next();
        r.rewind(3);
        r.next();
    }

    void test5(Reader r) {
        r.seekLine(3, 2);
        r.next();
        r.seekLine(1, 1);
        r.next();
        r.seekLine(11, 4);
        r.next();
        r.seekLine(0, 1);
        r.next();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * [Reader] impl: simple, expensive, slow, hopefully correct.
     */
    class Control
            implements Reader {
        construct(String text) {
            // build arrays of (i) offset to line number, (ii) offsets indexed by line number, and
            // (iii) line lengths indexed by line number.
            Int[] lineNumbers   = new Int[];
            Int[] lineOffsets   = new Int[];
            Int[] lineLengths   = new Int[];
            Int   lineNumber    = 0;
            Int   prevLineStart = 0;
            lineOffsets[0] = 0;
            Int c = text.size;
            for (Int i = 0; i < c; ++i) {
                lineNumbers[i] = lineNumber;
                Char ch = text[i];
                // check for new line
                if (ch.isLineTerminator() && (ch != '\r' || i+1 >= c || text[i+1] != '\n')) {
                    lineOffsets  += i + 1;
                    lineLengths  += i - prevLineStart;
                    prevLineStart = i + 1;
                    ++lineNumber;
                }
            }
            lineNumbers[c] = lineNumber;        // still need a line number for the EOF position
            lineLengths   += c - prevLineStart;
            lineLengths   += 0;                 // just in case the EOF position is a new line

            // build array of line number to offset and line length
            this.text        = text;
            this.lineNumbers = lineNumbers;
            this.lineOffsets = lineOffsets;
            this.lineLengths = lineLengths;
        }

        /**
         * The `String` that the [Reader] reads from.
         */
        String text;

        /**
         * Line number, indexed by [Reader] offset.
         */
        Int[] lineNumbers;

        /**
         * [Reader] offset, indexed by line number.
         */
        Int[] lineOffsets;

        /**
         * Line length, indexed by line number.
         */
        Int[] lineLengths;

        @Override
        Int offset.set(Int n) {
            assert:bounds 0 <= n <= lineNumbers.size;
            super(n);
        }

        @Override
        Int lineNumber {
            @Override
            Int get() = lineNumbers[offset];

            @Override
            void set(Int n) {
                assert:bounds 0 <= n < lineOffsets.size;
                offset = lineOffsets[n];
            }
        }

        @Override
        @RO Int lineStartOffset.get() = lineOffsets[lineNumber];

        @Override
        Int lineOffset {
            @Override
            Int get() {
                return offset - lineStartOffset;
            }

            @Override
            void set(Int n) {
                assert:bounds 0 <= n < lineLengths[lineNumber];
                offset += n - get();
            }
        }

        @Override
        TextPosition position {
            @Override
            TextPosition get() = snapshot(this);

            @Override
            void set(TextPosition pos) {
                offset = pos.offset;
            }
        }

        @Override
        @RO Int size.get() = text.size;

        @Override
        @RO Int remaining.get() = size - offset;

        @Override
        Char take() {
            assert:bounds !eof;
            return text[offset++];
        }

        @Override
        conditional Char peek() {
            if (eof) {
                return False;
            }
            return True, text[offset];
        }

        @Override
        immutable Char[] toCharArray() = text.chars;

        @Override
        String toString() = text;

        @Override
        conditional Reader seekLine(Int seekLine, Int seekOffset = 0) {
            assert:arg seekLine >= 0 && seekOffset >= 0;
            Boolean result = True;
            if (seekLine >= lineOffsets.size) {
                seekLine   = lineOffsets.size-1;
                seekOffset = lineLengths[seekLine];
                result = False;
            }
            if (seekOffset > lineLengths[seekLine]) {
                seekOffset = lineLengths[seekLine];
                result = False;
            }
            offset = lineOffsets[seekLine] + seekOffset;
            return result, this;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * [Reader] impl: combines multiple readers and compares results. First reader is used as the
     * control case.
     */
    class Tester(Reader[] readers)
            implements Reader {
        @Override
        Int offset {
            @Override
            Int get() {
                Int control = readers[0].offset;
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert readers[i].offset == control;
                }
                return control;
            }

            @Override
            void set(Int n) {
                Exception? controlException = Null;
                try {
                    readers[0].offset = n;
                } catch (Exception e) {
                    controlException = e;
                }
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    Exception? checkException = Null;
                    try {
                        readers[i].offset = n;
                    } catch (Exception e) {
                        checkException = e;
                    }
                    assert checkException == Null && controlException == Null || checkException != Null && controlException != Null;
                }
                check();
                throw controlException?;
            }
        }

        @Override
        Int lineNumber {
            @Override
            Int get() {
                Int control = readers[0].lineNumber;
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert readers[i].lineNumber == control;
                }
                return control;
            }

            @Override
            void set(Int n) {
                Exception? controlException = Null;
                try {
                    readers[0].lineNumber = n;
                } catch (Exception e) {
                    controlException = e;
                }
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    Exception? checkException = Null;
                    try {
                        readers[i].lineNumber = n;
                    } catch (Exception e) {
                        checkException = e;
                    }
                    assert checkException == Null && controlException == Null || checkException != Null && controlException != Null;
                    check();
                }
                throw controlException?;
            }
        }

        @Override
        @RO Int lineStartOffset.get() {
            Int control = readers[0].lineStartOffset;
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].lineStartOffset == control;
            }
            return control;
        }

        @Override
        Int lineOffset {
            @Override
            Int get() {
                Int control = readers[0].lineOffset;
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert readers[i].lineOffset == control;
                }
                return control;
            }

            @Override
            void set(Int n) {
                Exception? controlException = Null;
                try {
                    readers[0].lineOffset = n;
                } catch (Exception e) {
                    controlException = e;
                }
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    Exception? checkException = Null;
                    try {
                        readers[i].lineOffset = n;
                    } catch (Exception e) {
                        checkException = e;
                    }
                    assert checkException == Null && controlException == Null || checkException != Null && controlException != Null;
                    check();
                }
                throw controlException?;
            }
        }

        @Override
        TextPosition position {
            @Override
            TextPosition get() {
                // TODO
                TODO
            }

            @Override
            void set(TextPosition pos) {
                // TODO
                TODO
            }
        }

        @Override
        @RO Int size.get() {
            Int control = readers[0].size;
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].size == control;
            }
            return control;
        }

        @Override
        @RO Int remaining.get() {
            Int control = readers[0].remaining;
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].remaining == control;
            }
            return control;
        }

        @Override
        @RO Boolean eof.get() {
            Boolean control = readers[0].eof;
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].eof == control;
            }
            return control;
        }

        @Override
        conditional Char next() {
            if (Char control := readers[0].next()) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert Char check := readers[i].next();
                    assert check == control;
                }
                check();
                return True, control;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].next();
                }
                check();
                return False;
            }
        }

        @Override
        Char take() {
            Char control;
            try {
                control = readers[0].take();
            } catch (Exception e) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    Char? check = Null;
                    try {
                        check = readers[i].take();
                    } catch (Exception eCheck) {
                    }
                    assert check == Null as $"reader {i} returned {check.quoted()} from take() instead of throwing";
                }
                check();
                throw e;
            }

            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].take() == control;
            }
            check();
            return control;
        }

        @Override
        conditional Char peek() {
            if (Char control := readers[0].peek()) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert Char check := readers[i].peek();
                    assert check == control;
                }
                check();
                return True, control;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].peek();
                }
                check();
                return False;
            }
        }

        @Override
        conditional Char match(Char ch) {
            if (Char control := readers[0].match(ch)) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert Char check := readers[i].match(ch);
                    assert check == control;
                }
                check();
                return True, control;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].match(ch);
                }
                check();
                return False;
            }
        }

        @Override
        conditional Char match(function Boolean(Char) matches) {
            if (Char ch := readers[0].match(matches)) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert Char check := readers[i].match(matches);
                    assert check == ch;
                }
                check();
                return True, ch;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].match(matches);
                }
                check();
                return False;
            }
        }

        @Override
        Reader skip(Int count = 1) {
            for (Reader r : readers) {
                r.skip(count);
            }
            check();
            return this;
        }

        @Override
        conditional Reader advance(Int count = 1) {
            Boolean control = readers[0].advance(count);
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert control == readers[i].advance(count);
            }
            check();
            return control, this;
        }

        @Override
        conditional Reader rewind(Int count = 1) {
            Boolean control = readers[0].rewind(count);
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert control == readers[i].rewind(count);
            }
            check();
            return control, this;
        }

        @Override
        Reader reset() {
            for (Reader r : readers) {
                r.reset();
            }
            check();
            return this;
        }

        @Override
        @Op("[..]") String slice(Range<TextPosition> indexes) {
            String control;
            try {
                control = readers[0].slice(indexes);
            } catch (Exception e) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    String? check = Null;
                    try {
                        check = readers[i].slice(indexes);
                    } catch (Exception eCheck) {
                    }
                    assert check == Null as $"reader {i} returned {check.quoted()} from slice() instead of throwing";
                }
                check();
                throw e;
            }

            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].slice(indexes) == control;
            }
            check();
            return control;
        }

        @Override
        @Op("[..]") String slice(Range<Int> indexes) {
            String control;
            try {
                control = readers[0].slice(indexes);
            } catch (Exception e) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    String? check = Null;
                    try {
                        check = readers[i].slice(indexes);
                    } catch (Exception eCheck) {
                    }
                    assert check == Null as $"reader {i} returned {check.quoted()} from slice() instead of throwing";
                }
                check();
                throw e;
            }

            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].slice(indexes) == control;
            }
            check();
            return control;
        }

        @Override
        Boolean hasAtLeast(Int count) {
            Boolean control = readers[0].hasAtLeast(count);
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].hasAtLeast(count) == control;
            }
            check();
            return control;
        }

        @Override
        Int nextChars(Char[] chars, Int offset, Int count) {
            Char[][] results = new Char[][readers.size](i -> i == 0 ? chars : chars.duplicate());
            Int control; // TODO GG change type to String and get wrong error: Method "nextChars" on the "Reader" type is not accessible. ("readers[0].nextChars(results[0], offset, count)")
            try {
                control = readers[0].nextChars(results[0], offset, count);
            } catch (Exception e) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    Int? check = Null; // TODO GG change type to String here, too
                    try {
                        check = readers[i].nextChars(results[i], offset, count);
                    } catch (Exception eCheck) {
                    }
                    assert check == Null as $"reader {i} returned {check} from nextChars() instead of throwing";
                }
                check();
                throw e;
            }

            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].nextChars(results[i], offset, count) == control;
                assert chars == results[i];
            }
            check();
            return control;
        }

        @Override
        immutable Char[] nextChars(Int count) {
            Char[] control = readers[0].nextChars(count);
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].nextChars(count) == control;
            }
            check();
            return control;
        }

        @Override
        String nextString(Int count) {
            String control = readers[0].nextString(count);
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].nextString(count) == control;
            }
            check();
            return control;
        }

        @Override
        conditional String nextLine() {
            if (String control := readers[0].nextLine()) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert String check := readers[i].nextLine();
                    assert check == control;
                }
                check();
                return True, control;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].nextLine();
                }
                check();
                return False;
            }
        }

        @Override
        immutable Char[] toCharArray() {
            Char[] chars = readers[0].toCharArray();
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].toCharArray() == chars;
            }
            check();
            return chars;
        }

        @Override
        String toString() {
            String s = readers[0].toString();
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                assert readers[i].toString() == s;
            }
            check();
            return s;
        }

        @Override
        conditional Reader seekLine(Int line, Int lineOffset = 0) {
            if (readers[0].seekLine(line, lineOffset)) {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert readers[i].seekLine(line, lineOffset);
                }
                check();
                return True, this;
            } else {
                for (Int i = 1, Int c = readers.size; i < c; ++i) {
                    assert !readers[i].seekLine(line, lineOffset);
                }
                check();
                return False;
            }
        }

        @Override
        Writer pipeTo(Writer buf) {
            // TODO
            TODO
        }

        @Override
        Writer pipeTo(Writer buf, Int count) {
            // TODO
            TODO
        }

        @Override
        void close(Exception? cause = Null) {
            for (Reader r : readers) {
                r.close(cause);
            }
        }

        void check() {
            Boolean eof             = readers[0].eof;
            Int     offset          = readers[0].offset;
            Int     lineNumber      = readers[0].lineNumber;
            Int     lineStartOffset = readers[0].lineStartOffset;
            Int     lineOffset      = readers[0].lineOffset;
            TextPosition pos        = readers[0].position;
            for (Int i = 1, Int c = readers.size; i < c; ++i) {
                TextPosition otherPos = readers[i].position;
                assert otherPos == pos;
                assert readers[i].eof             == eof             as $"{eof=} {i=} {readers[i].eof=}";
                assert readers[i].offset          == offset          as $"{offset=} {i=} {readers[i].offset=}";
                assert readers[i].lineNumber      == lineNumber      as $"{lineNumber=} {i=} {readers[i].lineNumber=}";
                assert readers[i].lineStartOffset == lineStartOffset as $"{lineStartOffset=} {i=} {readers[i].lineStartOffset=}";
                assert readers[i].lineOffset      == lineOffset      as $"{lineOffset=} {i=} {readers[i].lineOffset=}";
                readers[i].position = otherPos;
                assert readers[i].eof             == eof             as $"{eof=} {i=} {readers[i].eof=}";
                assert readers[i].offset          == offset          as $"{offset=} {i=} {readers[i].offset=}";
                assert readers[i].lineNumber      == lineNumber      as $"{lineNumber=} {i=} {readers[i].lineNumber=}";
                assert readers[i].lineStartOffset == lineStartOffset as $"{lineStartOffset=} {i=} {readers[i].lineStartOffset=}";
                assert readers[i].lineOffset      == lineOffset      as $"{lineOffset=} {i=} {readers[i].lineOffset=}";
            }
        }
    }
}
