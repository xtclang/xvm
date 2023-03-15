module TestSimple
        incorporates Report
    {
    @Inject Console console;

    void run()
        {
        console.print(report);
        }

    String value = "hello";

    interface Duck
        {
        String value;
        }

    mixin Report
            into Module
        {
        @Lazy String report.calc()
            {
            return this.is(Duck)
                ? this.value // this used to fail to compile
                : "?";
            }
        }
    }