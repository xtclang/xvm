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

            case Register:
                return 2;

            default:
                return value.estimateStringLength();
            }
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        switch (Referent)
            {
            case Char:
                buf.add('\'');
                value.appendEscaped(buf);
                buf.add('\'');
                break;

            case String:
                buf.add('\"');
                value.appendEscaped(buf);
                buf.add('\"');
                break;

            case Register:
                buf.add('#');
                value.register.appendTo(buf);
                break;

            default:
                value.appendTo(buf);
                break;
            }
        return buf;
        }
    }
