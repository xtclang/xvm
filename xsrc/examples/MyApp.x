/**
 * This is an entire module in a file.
 */
module MyApp.xqiz.it
    {
    package util
        {
        class SuperMap<KeyType, ValueType>
            implements Map<KeyType, ValueType>
            {
            Int size.get()
                {
                return 0;
                }
            }
        }

    package forms
        {
        class MainWindow
            {
            Void paint()
                {
                // do something here
                }
            }
        }
    }