import ecstasy.io.CharArrayReader;

import json.Doc;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectInputStream.ElementInputStream;
import json.Printer;
import json.Schema;

/**
 * A codec that handles the `application/json` MediaType.
 */
const JsonFormat(Printer printer = DEFAULT)
        implements Format<Doc>
    {
    @Override
    String name = "json";

    @Override
    <OtherValue> conditional Format!<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        TODO
        }

    @Override
    Value decode(String text)
        {
        if (json.Doc doc := new json.Parser(new CharArrayReader(text)).next())
            {
            return doc;
            }
        else
            {
            return Null;
            }
        }

    @Override
    String encode(Value value)
        {
        return printer.render(value);
        }

    @Override
    void write(Value value, Appender<Char> stream)
        {
        printer.print(value, stream);
        }


    // ----- SerializationFormat -------------------------------------------------------------------

    /**
     * Provides support for serializing a specific value type to and from JSON.
     */
    static const SerializationFormat<Value>(Schema schema, Mapping<Value> mapping)
            implements Format<Value>
        {
        @Override
        String name = "json:" + Value;

        @Override
        Value decode(String text)
            {
            return schema.createObjectInput(new CharArrayReader(text)).read<Value>();
            }

// TODO CP evaluate Reader vs Writer (and also: BinaryInput / BinaryOutput)
//        @Override
//        Value read(Iterator<Char> stream)
//            {
//            return schema.createObjectInput(stream).read<Value>();
//            }

        @Override
        void write(Value value, Appender<Char> stream)
            {
            schema.createObjectOutput(stream).write(value);
            }
        }
    }
