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
            case Char:
                Int n = 1;
                n := value.as(Char).isEscaped();
                return 2 + n;

            case String:
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
                appender.add('\'');
                value.appendEscaped(appender);
                appender.add('\'');
                break;

            case String:
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
