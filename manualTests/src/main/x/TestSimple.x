module TestSimple
    {
    @Inject Console console;

    void run()
        {
        C c = new C();

        report("outside", c);

        c.reportSelf();
        }

    static void report(String header, Object o)
        {
        console.print(header);
        console.print($"{o.is(Duck1)=}");
        console.print($"{o.is(Duck2)=}"); // used to always be "False"
        console.print($"{o.is(Duck3)=}"); // ditto
        console.print();
        }

    interface Duck1
        {
        String value1;
        }

    interface Duck2
        {
        String value2;
        }

    interface Duck3
        {
        String value3;
        }

    class C
        {
        construct()
            {
            value1 = "1";
            value2 = "2";
            value3 = "3";
            }

        String           value1;
        protected String value2;
        private   String value3;

        void reportSelf()
            {
            report("public",    this:public);
            report("protected", this:protected);
            report("private",   this:private);
            }
        }
    }