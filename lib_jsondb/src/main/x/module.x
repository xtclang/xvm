/**
 * JSON-based database implementation of the OODB API, with storage in a FileSystem.
 */
module jsondb.xtclang.org
    {
    package oodb import oodb.xtclang.org;
    package json import json.xtclang.org;


    // ----- temporary helpers ---------------------------------------------------------------------

    static <Serializable> immutable Byte[] toBytes(Serializable value)
        {
        import ecstasy.io.*;
        val raw = new ByteArrayOutputStream();
        json.Schema.DEFAULT.createObjectOutput(new UTF8Writer(raw)).write(value);
        return raw.bytes.freeze(True);
        }

    static <Serializable> Serializable fromBytes(Type<Serializable> type, Byte[] bytes)
        {
        import ecstasy.io.*;
        return json.Schema.DEFAULT.createObjectInput(new UTF8Reader(new ByteArrayInputStream(bytes))).read();
        }

    static void dump(String desc, Object o)
        {
        @Inject Console console;
        String s = switch()
            {
            case o.is(Byte[]): o.all(b -> b >= 32 && b <= 127 || new Char(b).isWhitespace())
                    ? new String(new Char[o.size](i -> new Char(o[i])))
                    : o.toString();

            default: o.toString();
            };

        console.println($"{desc}={s}");
        }
    }