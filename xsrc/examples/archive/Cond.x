module Test
    {
    package Spring import:optional SpringFramework.spring.io allow 1.2, 2.1;

    package Util
        {
        if (Spring.versionPresent(1.2)
            {
            // current assembler context: {Spring}
            class Handy // (Spring)
                {
                Byte[] toBytes
                    {
                    // ...
                    }
                }
            }
        else if (Spring.versionPresent(2.1)
            {
            enum Handy
                {
                Byte[] toBytes
                    {
                    // ...
                    }
                }
            }

        class SuperMap
                implements Map
            {
            // ...
            Void Serialize(DataOutput out)
                {
                if (Spring.present)
                    {
                    // current assembler context: {}
                    Byte[] ab = Handy.toBytes(this);   // error

                    // looking up Handy, we get a composite component of {Handy(Spring), Handy(!Spring)}
                    }
                }
            }
        }
    }