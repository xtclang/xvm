module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Running ...");
        val tests = ["0", "3", "123", "-123", "0x9", "0xf", "-0x3ABC", "0x"];
        NextTest: for (String test : tests)
            {
            IL lit;
            try
                {
                lit = new IL(test);
                }
            catch (Exception e)
                {
                console.println($"exception parsing {test}: {e}");
                continue NextTest;
                }

            console.println($"test={test}, lit={lit}");
            }
        }

    const IL(String text)
        {
        Signum explicitSign = Zero;
        Int    radix        = 10;
        UIntN  magnitude;
        String text;

        construct(String text)
            {
            assert text.size > 0;

            // optional leading sign
            Int of = 0;
            switch (text[of])
                {
                case '-':
                    explicitSign = Signum.Negative;
                    ++of;
                    break;

                case '+':
                    explicitSign = Signum.Positive;
                    ++of;
                    break;
                }

            // optional leading format
            Boolean underscoreOk = False;
            if (text.size - of >= 2 && text[of] == '0')
                {
                switch (text[of+1])
                    {
                    case 'X':
                    case 'x':
                        radix = 16;
                        break;

                    case 'B':
                    case 'b':
                        radix = 2;
                        break;

                    case 'o':
                        radix = 8;
                        break;
                    }

                if (radix != 10)
                    {
                    of += 2;
                    underscoreOk = True;
                    }
                }

            // digits
            UIntN magnitude = 0;
            Int   digits    = 0;
            NextChar: while (of < text.size)
                {
                Char ch = text[of];
                Int  nch;
                switch (ch)
                    {
                    case '0'..'9':
                        nch = ch - '0';
                        break;

                    case 'A'..'F':
                        nch = 10 + (ch - 'A');
                        break;

                    case 'a'..'f':
                        nch = 10 + (ch - 'a');
                        break;

                    case '_':
                        if (underscoreOk)
                            {
                            continue NextChar;
                            }
                        continue;

                    default:
                        throw new IllegalArgument($|Illegal character {ch.toSourceString()} at \
                                                   |offset {of} in integer literal {text.quoted}
                                                 );
                    }

                if (nch >= radix)
                    {
                    throw new IllegalArgument($|Illegal digit {ch.toSourceString()} for radix \
                                               |{radix} in integer literal {text.quoted}
                                             );
                    }

                magnitude    = magnitude * radix + nch;
                underscoreOk = True;
                ++digits;
                ++of;
                }

            if (digits == 0)
                {
                throw new IllegalArgument($"Illegal integer literal {text.quoted()}");
                }

            this.magnitude = magnitude;
            this.text      = text;
            }

        @Override
        String toString()
            {
            return $"IL\{text={text}, magnitude={magnitude}, radix={radix}, explicitSign={explicitSign}}";
            }
        }
    }
