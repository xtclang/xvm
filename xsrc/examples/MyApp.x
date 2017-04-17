/**
 * This is an entire module in a file.
 */
module MyApp.xqiz.it
    {
    package x import ecstasy.xtclang.org "1.0"
            avoid "1.0.3"
            allow "1.2.1.rc2", "1.1.beta7"
            prefer "1.2.1", "1.1"
            avoid "2.0";

    package util
        {
        /**
         * So much happiness.
         */
        enum Happiness
                if (true)
                    {
                    if (Crazy.present)
                        {
                        implements Serializable
                        }
                    implements ExternalizableLite

                    }
                else
                    {
                    implements Externalizable
                    }
            { SAD, MEDIOCRE, HAPPY, ECSTATIC }

        /**
         * So much light.
         */
        enum Light
            {
            /**
             * night time
             */
            DARK("turn on a light"),
            /**
             * sun is out
             */
            LIGHT("turn off a light");

            construct Light(String s)
                {
                // this.s = s;
                }
            String s = ╔═════════════════════╗
                       ║This could be any    ║
                       ║freeform text that   ║
                       ║could be inside of an║
                       ║Ecstasy source file  ║
                       ╚═════════════════════╝;
            String s2 = "This could be any    "
                      + "\nfreeform text that"
                      + "\ncould be inside of an"
                      + "\nEcstasy source file";

            Map<Int, String> map = Map:{0="zero", 1="one", 2="two"};
            List<String> list = {"hello", "world", "!"};
            //List<Dec> list = {1.0, 2.0, 3.0};

            Binary b1 = Binary:{"abcdef"};
            Binary b2 = Binary:{
                ╔═════════════════════╗
                ║ 9149AF2aCF75b3B8E123║
                ║ 0f9149AcF2CF73BE123 ║
                ╚═════════════════════╝};
            Binary b3 = Binary:{
                 9149AF2aCF75b3B8E123
                 a0f9149AcF2CF73BE123
                };

            Int result1 = a();
            Int result2 = a(1);
            Int result3 = a("test", "args");
            Int result = a.b.c().d.e(1).f("test", "args");
            }
        class SuperMap<KeyType, ValueType>
            implements Map<KeyType, ValueType>
            {
            Int size.get()
                {
                return 0;
                }

            private <SomeType> List<SomeType> doSomethingWith(SomeType value);
            }
        }

    package forms
        {
        class MainWindow
            {
            Boolean visible.get()
                {
                return true;
                }

            Void move(Int dx, Int dy)
                {
                assert dx >= 0;
                assert:always dx >= 0;
                assert:once dy >= 0;
                assert:test dx > 0 && dy >= 0;
                assert:debug;
                }

            Void paint()
                {
                // do something here
                }
            }
        }
    }