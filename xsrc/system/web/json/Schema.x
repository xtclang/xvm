import collections.Hasher;
import collections.HashMap;
import collections.ListMap;

import io.Reader;
import io.Writer;
import io.ObjectInput;
import io.ObjectOutput;


const Schema
        implements io.TextFormat
    {
    /**
     * Construct a schema for a specified set of Mappings.
     *
     * @param mappings         pass a sequence of `Mapping` objects to use to read and write various
     *                         Ecstasy types; order defines precedence of selection in cases of ties
     * @param version          pass a `Version` to use for the schema, otherwise one is calculated
     * @param randomAccess     pass `True` to enable random access of name/value pairs within JSON
     *                         objects
     * @param enableMetadata   pass `True` to enable metadata collection from the beginning of JSON
     *                         objects when reading, and to emit metadata to the beginning of JSON
     *                         objects when writing
     * @param enablePointers   pass `True` to use JSON pointers to eliminate duplicate
     *                         serialization of custom objects non-primitive JSON types
     * @param storeRemainders  pass `True` to
     */
    construct(Mapping[] mappings        = [],
              Version?  version         = Null,
              Boolean   randomAccess    = False,
              Boolean   enableMetadata  = False,
              Boolean   enablePointers  = False,
              Boolean   storeRemainders = False)
        {
        // use the module version if no version is specified
        if (version == Null)
            {
            TODO version = ...
            }

        this.version         = version;
        this.randomAccess    = randomAccess;
        this.enableMetadata  = enableMetadata;
        this.enablePointers  = enablePointers;
        this.storeRemainders = storeRemainders;

        // add specified mappings
        ListMap<Type, Mapping> mappingForType = new ListMap();
        Map<String, Type>      typeForName    = new HashMap();
        for (Mapping mapping : mappings)
            {
            if (mappingForType.putIfAbsent(mapping.Serializable, mapping))
                {
                typeForName.putIfAbsent(mapping.typeName?, mapping.Serializable);
                }
            }

        this.mappings    = mappingForType;
        this.typeForName = typeForName;
        }

    /**
     * The "default" Schema, which is not aware of any custom serialization or deserialization
     * mechanisms.
     */
    static Schema DEFAULT = new Schema(enableMetadata = True, enablePointers = True);


    // ----- nested types --------------------------------------------------------------------------

    /**
     * A IllegalJSON exception is raised when a JSON format error is detected.
     */
    const MissingMapping(String? text = null, Exception? cause = null, Type? type = null)
            extends IllegalJSON(text, cause);

    /**
     * A mapping represents the ability to read and/or write objects of a certain serializable type
     * from/to a JSON document format.
     */
    static interface Mapping<Serializable>
        {
        /**
         * The name of the type for the mapping. This name helps to identify a Mapping from JSON
         * metadata, or allows the Mapping to be identified in the metadata related to objects
         * emitted by this Mapping.
         */
        @RO String? typeName;

        /**
         * Read a value of type `Serializable` from the provided `ElementInput`.
         *
         * @param ObjectType  the type to read, which may be more specific than the `Serializable`
         *                    type
         * @param in          the `ElementInput` to read from
         *
         * @return a `Serializable` object
         */
        <ObjectType extends Serializable> ObjectType read<ObjectType>(ElementInput in);

        /**
         * Write a value of type `Serializable` to the provided `ElementOutput`.
         *
         * @param ObjectType  the type to write, which may be more specific than the `Serializable`
         *                    type
         * @param out    the `ElementOutput` to write to
         * @param value  the `ObjectType` value to write
         */
        <ObjectType extends Serializable> void write(ElementOutput out, ObjectType value);
        }

    /**
     * A service that looks for `Mapping`-by-type when there is **NOT** a Mapping that specifies
     * that exact type. For example, annotated types may be handled by the non-annotated forms, and
     * subclass types may be handled by a superclass type's `Mapping`.
     */
    service TypeMapper
        {
        /**
         * Given a `Type`, check if there is a compatible `Mapping`, such as one that handles a
         * superclass of the specified `Type`.
         *
         * @param type  the `Type` to find a Mapping for
         *
         * @return the `Type` for which a compatible `Mapping` exists
         *
         * @throws UnsupportedOperation if no compatible Mapping can be found
         */
        Type selectType(Type type)
            {
            if (Type mappingType := cache.get(type))
                {
                return mappingType;
                }

            for (Type mappingType : mappings.keys)
                {
                if (type.isA(mappingType))
                    {
                    cache.put(type, mappingType);
                    return mappingType;
                    }
                }

            throw new UnsupportedOperation($"No JSON Schema Mapping found for Type={type}");
            }

        private Map<Type, Type> cache = new HashMap();
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The `Schema` version, which defaults to the module version if the schema is drawn from a
     * single module.
     */
    Version version;

    /**
     * An option to enable random access, which means that JSON objects can have their properties
     * accessed in any order (as if the documents were managed internally as a `Map<String, Doc>`),
     * instead of assuming that each name requested will be the next name in the document.
     *
     * By assuming a strict order, the JSON parsing can avoid materializing intermediate data
     * structures to hold the entire contents of the JSON object in order to support random access
     * to fields.
     *
     * By allowing random access, JSON deserialization is expected to incur a significant additional
     * cost.
     */
    Boolean randomAccess;

    /**
     * An option to look for metadata properties at the beginning of each JSON object, and extract
     * them. All leading properties that begin with an "@", a "$", or an "_" character are assumed
     * to be metadata properties.
     *
     * Similarly, when serializing to JSON, this option will cause the class or type of each object
     * to be emitted as metadata.
     *
     * By enabling metadata, JSON serialization and deserialization will incur a minor additional
     * cost.
     *
     * When `collectRemainders` is `True`, the metadata properties are incorporated into the
     * remainder as well.
     */
    Boolean enableMetadata;

    /**
     * An option to allow lookup of deserialized values and objects using JSON pointers. This allows
     * for "graph serialization", in which the same Ecstasy object instance can be referenced from
     * multiple locations within the deserialized object graph.
     *
     * By enabling pointers, JSON serialization and deserialization will incur a significant
     * additional cost.
     *
     * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
     */
    Boolean enablePointers;

    /**
     * An option to collect and store any metadata and unread properties together as a means of
     * supporting interop across loosely compatible systems, and to support forward version
     * compatibility.
     *
     * By enabling remainder collection and storage, JSON serialization and deserialization will
     * incur a minor additional cost.
     */
    Boolean storeRemainders;

    /**
     * A prioritized mapping from Ecstasy types to JSON Mapping implementations.
     */
    ListMap<Type, Mapping> mappings;

    /**
     * A mapping from JSON metadata type names to Ecstasy types.
     */
    Map<String, Type> typeForName;

    /**
     * A lazily instantiated type mapping service.
     */
    @Lazy
    TypeMapper typeMapper.calc()
        {
        return new TypeMapper();
        }

    /**
     * A lazily instantiated default Mapping that handles any object.
     */
    @Lazy
    Mapping defaultMapping.calc()
        {
        TODO return new ObjectMapping();
        }


    // ----- TextFormat implementation --------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return "JSON";
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        return new ObjectInputStream(reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        return new ObjectOutputStream(writer);
        }


    // ----- helpers

    <Serializable> conditional Mapping<Serializable> mappingFor(Type<Serializable> type)
        {
        TODO
        }

    conditional Mapping getMapping(Doc doc)
        {
        TODO
        }

    <ObjectType> conditional Mapping<ObjectType> getMapping(Type<ObjectType> type)
        {
        TODO
        }


    // ----- input/output stream implementations ---------------------------------------------------

    /**
     * The ObjectInput implementation for JSON deserialization.
     */
    class ObjectInputStream(Reader reader)
            implements ObjectInput
        {
        /**
         * The underlying writer to read the JSON data from.
         */
        public/private Reader reader;

        @Lazy
        public/private Lexer lexer.calc()
            {
            return new Lexer(reader);
            }

        @Lazy
        public/private Parser parser.calc()
            {
            return new Parser(lexer);
            }

        @RO ElementInput<Nullable> root.get()
            {
            TODO
            }

        @RO FieldInput<ElementInput<Nullable>> object.get()
            {
            TODO return elementInput.enterObject();
            }

        @Override
        <ObjectType> ObjectType read<ObjectType>()
            {
            if (Mapping<ObjectType> mapping := this.Schema.getMapping(ObjectType))
                {
                TODO return mapping.read(this);
                }

            Doc doc = new Parser(reader).next();
            if (doc.is(Primitive | Array<Primitive>))
                {
                if (ObjectType != Object)
                    {
                    TODO add conversions, e.g. IntLiteral -> Int, FPLiteral -> Dec, etc.
                    }

                if (doc.is(ObjectType))
                    {
                    return doc;
                    }

                TODO how should this report errors?
                }

            if (Mapping mapping := this.Schema.getMapping(doc), mapping.Serializable.is(ObjectType))
                {
                TODO return mapping.read(this).as(ObjectType);
                }

            return doc.as(ObjectType); // or throw
            }

        @Override
        void close()
            {
            }
        }

    /**
     * The ObjectOutput implementation for JSON serialization.
     */
    class ObjectOutputStream(Writer writer)
            implements ObjectOutput
        {
        /**
         * The underlying writer to direct the JSON data to.
         */
        public/private Writer writer;

        @Lazy
        protected/private ElementOutput output.calc()
            {
            TODO
            }

        @Override
        <ObjectType> void write(ObjectType value)
            {
            TODO
            }

        @Override
        void close()
            {
            }
        }

    /**
     * The ObjectInput implementation for JSON deserialization.
     */
    interface JsonWriter
        {
        <ObjectType> void writeArray<ObjectType>(Iterator<ObjectType> values);
        JsonWriter nest(String name);
        }


    // ----- intrinsic mappings --------------------------------------------------------------------

    /**
     * A "catch-all" mapping that can _theoretically_ handle any object.
     */
    const ObjectMapping
            implements Mapping<Object>
        {
        @Override
        <ObjectType extends Serializable> ObjectType read<ObjectType>(ElementInput in)
            {
            if (enableMetadata)
                {
                // use metadata to determine what to read
                TODO
                }

            if (ObjectType != Object)
                {
                // use ObjectType to determine what to read
                TODO
                }

            throw new MissingMapping(type=ObjectType);
            }

        @Override
        <ObjectType extends Serializable> void write(ElementOutput out, ObjectType value)
            {
            // if no type information is provided, then use the runtime type of the object itself as
            // the source of reflection information for the contents of the JSON serialization
            Type type = ObjectType == Object
                    ? &value.actualType
                    : ObjectType;

            if (value.is(Nullable))
                {
                // out.
                }

            if (type.is(Type<Number>))
                {
                }
            else if (type.is(Type<Boolean>))
                {
                }
            else
                {
                }

            TODO
            }
        }

//        /**
//         * The containing DomAware object.
//         */
//        public/private DomNode!? parent;
//
//        /**
//         * The reference token for this node.
//         *
//         * @see [RFC 6901 §4](https://tools.ietf.org/html/rfc6901#section-4)
//         */
//        @RO String referenceToken;
//
//        /**
//         * The JSON pointer that represents the entire path to this node from the root object.
//         *
//         * @see [RFC 6901 §5](https://tools.ietf.org/html/rfc6901#section-5)
//         */
//        String pointer.get()
//            {
//            return buildPointer(0).toString();
//            }
//
//        protected StringBuffer buildPointer(Int length)
//            {
//            String token = referenceToken;
//            length += 1 + token.size;
//            StringBuffer buf = parent?.buildPointer(length) : new StringBuffer(length);
//            buf.append('/')
//               .append(token);
//            return buf;
//            }
//
//        /**
//         * The URI fragment that represents the JSON pointer that represents the entire path to this
//         * node from the root object.
//         *
//         * @see [RFC 6901 §6](https://tools.ietf.org/html/rfc6901#section-6)
//         */
//        String uriFragment.get()
//            {
//            TODO encode pointer value as per https://tools.ietf.org/html/rfc3986
//            }

    }