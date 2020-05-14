/**
 * An Argument represents a value for a parameter. The argument optionally supports a name that
 * can be used to specify the name of the parameter for which the argument's value is intended.
 */
const Argument<Referent extends immutable Const>(Referent value, String? name = Null)
    {
    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        switch (Referent)
            {
            case Type<Char>:
                assert value.is(Char);                      // TODO GG can we remove this line?
                Int n = 1;
                n := value.as(Char).isEscaped();
                return 2 + n;

            case Type<String>:
                assert value.is(String);                    // TODO GG can we remove this line?
                Int n = value.size;
                n := value.isEscaped();
                return 2 + n;

            default:
                return value.estimateStringLength();
            }
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        switch (Referent)
            {
            case Char:
                assert value.is(Char);                      // TODO GG can we remove this line?
                appender.add('\'');
                value.as(Char).appendEscaped(appender);     // TODO GG remove .as(Char)
                appender.add('\'');
                break;

            case String:
                assert value.is(String);                    // TODO GG can we remove this line?
                appender.add('\"');
                value.appendEscaped(appender);
                appender.add('\"');
                break;

            default:
                value.appendTo(appender);
                break;
            }
        }
    }
