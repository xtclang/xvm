/**
 * Runtime coverage for shared AST validation and emission helpers: chained comparisons,
 * tuple loop targets, condition scopes, and switch-range bounds.
 */
module TestAstExpressions {
    void run() {
        new Probe().verify();
    }

    class Probe {
        Int calls = 0;
        Int first = 0;
        Int second = 0;

        Int value(Int n) {
            ++calls;
            return n;
        }

        Object boxed(Object value) = value;

        void verify() {
            for (Int left : [0, 3]) {
                for (Int right : [0, 3]) {
                    Boolean expected = left == 3 && right == 3;
                    Boolean actual = left == 3 == right;
                    assert actual == expected;
                    if (left == 3 == right) {
                        assert expected;
                    } else {
                        assert !expected;
                    }
                    if (!(left == 3 == right)) {
                        assert !expected;
                    } else {
                        assert expected;
                    }
                }
            }

            for (Int? left : [Null, 3]) {
                for (Int? right : [Null, 3]) {
                    Boolean expected = left == Null && right == Null;
                    Boolean actual = left == Null == right;
                    assert actual == expected;
                    if (left == Null == right) {
                        assert expected;
                    } else {
                        assert !expected;
                    }
                    if (!(left == Null == right)) {
                        assert !expected;
                    } else {
                        assert expected;
                    }
                }
            }

            Boolean result = 2 < 1 < value(3);
            assert !result && calls == 1;
            calls = 0;
            result = value(1) < value(2) <= value(2);
            assert result && calls == 3;
            calls = 0;
            result = value(1) > value(2) >= value(3);
            assert !result && calls == 3;
            calls = 0;
            result = value(1) != value(2) != value(3);
            assert result && calls == 3;

            Int counter = 0;
            result = counter++ < counter++ < counter++;
            assert result && counter == 3;

            Probe target = new Probe();
            Int sum = 0;
            for ((target.first, target.second) : [(1, 2), (3, 4)]) {
                sum += target.first + target.second;
            }
            assert sum == 10 && target.first == 3 && target.second == 4;

            sum = 0;
            for (Int i = 0; i < 5; ++i) {
                if (i == 2) {
                    continue;
                }
                sum += i;
            }
            assert sum == 8;
            Int i = 0;
            while (i < 3) {
                ++i;
                continue;
            }
            do {
                --i;
            } while (i > 0);
            assert i == 0;

            Object a = boxed("abc");
            Object b = boxed(Int:7);
            if (a.is(String) && b.is(Int)) {
                assert a.size == 3 && b == 7;
            } else {
                assert False;
            }
            if (!a.is(String) || !b.is(Int)) {
                assert False;
            }

            assert classify(1) == 1;
            assert classify(3) == 1;
            assert classify(4) == 0;
            assert classify(5) == 2;
            assert classify(7) == 2;
            assert classify(8) == 0;
            assert classify('a') == 1;
            assert classify('c') == 1;
            assert classify('d') == 0;
        }

        Int classify(Int n) {
            switch (n) {
            case 1..3: return 1;
            case 5..7: return 2;
            default: return 0;
            }
        }

        Int classify(Char ch) {
            switch (ch) {
            case 'a'..'c': return 1;
            default: return 0;
            }
        }
    }
}
