
package delegationTests {

    typedef String|Int as StringOrInt;

    void run() {
        console.print(">>>> Running DelegationTests >>>>");
        Test t = new Test("text", 17);
        assert t.showText() == "text";
        assert t.showValue() == 17;
        console.print("<<<< Finished DelegationTests <<<<<");
    }

    service Test
            delegates ReportableAsString-Object(value1)
            delegates ReportableAsInt(value2) {

        construct(String text, Int value) {
            value1 = new ReportableString(text);
            value2 = new ReportableInt(value);
        }
        private ReportableAsString value1;
        private ReportableAsInt    value2;
    }

    interface ReportableAsString {
        String showText();
    }

    interface ReportableAsInt {
        Int showValue();
    }

    class ReportableString(String name)
            implements ReportableAsString {
        @Override String showText() = name;
    }

    class ReportableInt(Int value)
            implements ReportableAsInt {
        @Override Int showValue() = value;
    }
}
