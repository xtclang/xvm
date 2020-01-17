import io.IOException;
import io.ObjectInput;
import io.ObjectOutput;
import io.Reader;
import io.Writer;

package json
        implements io.TextFormat
    {
    /**
     * A IllegalJSON exception is raised when a JSON format error is detected.
     */
    const IllegalJSON(String? text = null, Exception? cause = null)
            extends IOException(text, cause);

    /**
     * A IllegalJSON exception is raised when a JSON format error is detected.
     */
    const MissingMapping(String? text = null, Exception? cause = null, Type? type = null)
            extends IllegalJSON(text, cause);

    /**
     * JSON primitive types are all JSON values except for arrays and objects.
     */
    typedef (Nullable | Boolean | IntLiteral | FPLiteral | String) Primitive;

    /**
     * JSON types include primitive types, array types, and map types.
     */
    typedef (Primitive | Map<String, Doc> | Array<Doc>) Doc;


    // ----- TextFormat interface ------------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return Schema.DEFAULT.name;
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        // use the default JSON schema to deserialize objects
        return Schema.DEFAULT.createObjectInput(reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        // use the default JSON schema to serialize objects
        return Schema.DEFAULT.createObjectOutput(writer);
        }
    }