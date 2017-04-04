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
        enum Happiness { SAD, MEDIOCRE, HAPPY, ECSTATIC }

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
            String s;
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
                }

            Void paint()
                {
                // do something here
                }
            }
        }
    }