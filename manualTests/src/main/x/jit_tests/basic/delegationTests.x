
package delegationTests {

    typedef String|Int as StringOrInt;

    void run() {
        Test t = new Test("text", 17);
        assert t.showText() == "text";
        assert t.showValue() == 17;
        assert t.showNullable(19) == 19;
        assert t.showNullable(Null) == -1;

        Char ch = 'A';
        assert ch.toInt()    == 65;
        assert ch.toInt128() == 65;

    }

    service Test
            delegates ReportableAsString-Object(value1)
            delegates ReportableAsInt(value2)
            delegates ReportableAsNullableInt(value3) {

        construct(String text, Int value) {
            value1 = new ReportableString(text);
            value2 = new ReportableInt(value);
            value3 = new ReportableNullableInt();
        }
        private ReportableAsString      value1;
        private ReportableAsInt         value2;
        private ReportableAsNullableInt value3;
    }

    interface ReportableAsString {
        String showText();
    }

    interface ReportableAsInt {
        Int showValue();
    }

    interface ReportableAsNullableInt {
        Int showNullable(Int? value);
    }

    class ReportableString(String name)
            implements ReportableAsString {
        @Override String showText() = name;
    }

    class ReportableInt(Int value)
            implements ReportableAsInt {
        @Override Int showValue() = value;
    }

    class ReportableNullableInt
            implements ReportableAsNullableInt {
        @Override Int showNullable(Int? value) = value ?: -1;
    }
}
