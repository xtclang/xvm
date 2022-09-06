import ecstasy.io.UTF8Reader;
import ecstasy.io.UTF8Writer;

import json.Mapping;
import json.ObjectInputStream;
import json.ObjectInputStream.ElementInputStream;
import json.Schema;

/**
 * A codec that handles the `application/json` MediaType.
 */
const JsonCodec
        implements Codec
    {
    construct (Schema schema = Schema.DEFAULT, Set<MediaType> additionalTypes = Set:[])
        {
        HashSet<MediaType> mediaTypes = new HashSet();
        mediaTypes.addAll(additionalTypes);
        mediaTypes.add(Json);
        mediaTypes.freeze(True);
        this.types  = mediaTypes;
        this.schema = schema;
        }

    private Set<MediaType> types;

    private Schema schema;

    @Override
    MediaType[] mediaTypes.get()
        {
        return types.toArray();
        }

    @Override
    Boolean supports(Type type)
        {
        return type.isA(Stringable);
        }

    @Override
    <ObjectType> ObjectType decode<ObjectType>(Type type, InputStream in)
        {
        assert Mapping     mapper := schema.findMapping(type.DataType);
        ObjectInputStream  o_in   = schema.createObjectInput(new UTF8Reader(in)).as(ObjectInputStream);
        ElementInputStream e_in   = o_in.ensureElementInput();
        return mapper.read(e_in).as(ObjectType);
        }

    @Override
    <ObjectType> void encode<ObjectType>(ObjectType value, OutputStream out)
        {
        if (value.is(Iterable<Char>))
            {
            for (Char c : value)
                {
                out.writeBytes(c.utf8());
                }
            }
        else
            {
            schema.createObjectOutput(new UTF8Writer(out)).write(value);
            }
        }
    }
