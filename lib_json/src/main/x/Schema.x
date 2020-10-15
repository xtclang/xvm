import ecstasy.collections.Hasher;

import ecstasy.io.Reader;
import ecstasy.io.Writer;
import ecstasy.io.ObjectInput;
import ecstasy.io.ObjectOutput;
import ecstasy.io.TextFormat;


/**
 * A JSON Schema represents the ability to read and write Ecstasy objects (of a particular group of
 * Ecstasy types) from and to JSON textual data, or conversely, the ability to transform JSON
 * textual data from and to Ecstasy object graphs.
 *
 * TODO ReflectionMapping creation
 * TODO integrate with new TypeSystem API
 * TODO versioning support
 */
const Schema
        implements TextFormat
    {
    /**
     * Construct a schema for a specified set of Mappings.
     *
     * @param mappings          pass a sequence of `Mapping` objects to use to read and write
     *                          various Ecstasy types; order defines precedence of selection in
     *                          cases of ties
     * @param version           pass a `Version` to use for the schema, otherwise one is calculated
     * @param randomAccess      pass `True` to enable random access of name/value pairs within JSON
     *                          objects
     * @param enableMetadata    pass `True` to enable metadata collection from the beginning of JSON
     *                          objects when reading, and to emit metadata to the beginning of JSON
     *                          objects when writing
     * @param enablePointers    pass `True` to use JSON pointers to eliminate duplicate
     *                          serialization of custom objects non-primitive JSON types
     * @param enableReflection  pass `True` to use reflection as the basis for serialization and
     *                          deserialization of types for which the Mapping is missing
     * @param typeSystem        pass the TypeSystem to use for reflection; the TypeSystem must be
     *                          the type system that contains the JSON module, or a TypeSystem
     *                          derived thereof; defaults to the type system that contains the JSON
     *                          module
     * @param retainNulls       pass `True` to treat JSON `null` values as significant
     * @param storeRemainders   pass `True` to collect and store any metadata and unread properties
     *                          together as a means of supporting forward version compatibility
     */
    construct(Mapping[]   mappings         = [],
              Version?    version          = Null,
              Boolean     randomAccess     = False,
              Boolean     enableMetadata   = False,
              String      typeKey          = "$type",
              Boolean     enablePointers   = False,
              String      pointerKey       = "$ref",
              Boolean     enableReflection = False,
              TypeSystem? typeSystem       = Null,
              Boolean     retainNulls      = False,
              Boolean     storeRemainders  = False)
        {
        // verify that the type system is the TypeSystem that includes this class, or a TypeSystem
        // that derives from the TypeSystem that includes this class
        if (typeSystem? != &this.actualType.typeSystem)
            {
            TODO
            }

        // use the module version if no version is specified
        if (version == Null)
            {
            version = v:1; // TODO calculate version of the TypeSystem: version = ...
            }

        // store off specified options
        this.version          = version;
        this.randomAccess     = randomAccess;
        this.enableMetadata   = enableMetadata | enablePointers | enableReflection;
        this.typeKey          = typeKey;
        this.enablePointers   = enablePointers;
        this.pointerKey       = pointerKey;
        this.enableReflection = enableReflection;
        this.retainNulls      = retainNulls;
        this.storeRemainders  = storeRemainders;
        this.typeSystem       = typeSystem ?: &this.actualType.typeSystem;

        // build indexes for the provided mappings
        ListMap<Type, Mapping> mappingByType   = new ListMap();
        HashMap<String, Type>  typeByName      = new HashMap();
        Mapping[]              defaultMappings = mapping.DEFAULT_MAPPINGS;
        mappings = mappings.empty ? defaultMappings : (new Mapping[]) + mappings + defaultMappings;
        for (Mapping mapping : mappings)
            {
            mappingByType.putIfAbsent(mapping.Serializable, mapping);
            typeByName.putIfAbsent(mapping.typeName, mapping.Serializable);
            }
        this.mappingByType = mappingByType;
        this.typeByName    = typeByName.makeImmutable();
        }

    /**
     * The "default" Schema, which is not aware of any custom serialization or deserialization
     * mechanisms.
     */
    static Schema DEFAULT = new Schema(
            enableReflection = True,
            enableMetadata   = True,
            enablePointers   = True,
            randomAccess     = True);


    // ----- TextFormat interface ------------------------------------------------------------------

    @Override
    @RO String name.get()
        {
        return "JSON";
        }

    @Override
    ObjectInput createObjectInput(Reader reader)
        {
        return new ObjectInputStream(this, reader);
        }

    @Override
    ObjectOutput createObjectOutput(Writer writer)
        {
        return new ObjectOutputStream(this, writer);
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
     * The String name in a name/value pair that identifies the type of the JSON object that is used
     * to determine the type of the Ecstasy object if the use of metadata is enabled in the schema.
     */
    String typeKey;

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
     * The String name in a name/value pair that identifies that the JSON object acts as a pointer
     * to a previously read object, if the use of pointers is enabled in the schema.
     */
    String pointerKey;

    /**
     * An option to handle any types and classes of objects that do not have a known mapping by
     * using a "catch-all" mapping. The "catch-all" mapping uses reflection to serialize and
     * deserialize objects without a custom [Mapping] implementation.
     */
    Boolean enableReflection;

    /**
     * The TypeSystem that the reflection-based capabilities will use.
     */
    TypeSystem typeSystem;

    /**
     * An option to treat null values as significant. This is generally only useful for "pretty
     * printing" and debugging.
     */
    Boolean retainNulls;

    /**
     * An option to collect and store any metadata and unread properties together as a means of
     * supporting inter-op across loosely compatible systems, and to support forward version
     * compatibility.
     *
     * By enabling remainder collection and storage, JSON serialization and deserialization will
     * incur a minor additional cost.
     */
    Boolean storeRemainders;

    /**
     * A prioritized mapping from Ecstasy types to JSON Mapping implementations.
     */
    ListMap<Type, Mapping> mappingByType;

    /**
     * A mapping from JSON metadata type names to Ecstasy types.
     */
    Map<String, Type> typeByName;


    // ----- Mapping look-up -----------------------------------------------------------------------

    /**
     * Find or create a mapping for the specified type and optional metadata.
     *
     * @param type  the type for which a Mapping is required
     * @param in    (optional) the ElementInput that the mapping will be used to read from; this
     *              allows the schema to look for type metadata, if that option is enabled
     *
     * @return the Mapping for the specified type and optional metadata
     *
     * @throws IllegalJSON     for exceptions related to reading or processing metadata
     * @throws MissingMapping  if no appropriate mapping can be provided
     */
    <ObjectType> Mapping<ObjectType> ensureMapping(Type<ObjectType> type, ElementInput? in=Null)
        {
        if (in != Null && enableMetadata && !in.isNull())
            {
            Doc typeInfo = in.metadataFor(typeKey, peekAhead=True);
            if (typeInfo.is(String))
                {
                return ensureMappingByName(typeInfo, type);
                }
            else if (typeInfo != Null)
                {
                throw new IllegalJSON(
                        $"\"{typeKey}\" metadata at \"{in.pointer}\" must be a \"String\"");
                }
            }

        return ensureMappingByType(type);
        }

    /**
     * Search for a mapping for the specified type name and type constraint.
     *
     * @param typeName        the name used to find the specific type mapping
     * @param typeConstraint  the type constraint for the Mapping (which may indicate the same
     *                        type as the `typeName`, or may be a wider type thereof)
     *
     * @return the selected Mapping
     *
     * @throws MissingMapping  if no appropriate mapping can be provided
     */
    <ObjectType> Mapping<ObjectType> ensureMappingByName(String typeName, Type<ObjectType> typeConstraint)
        {
        if (Type type := typeByName.get(typeName))
            {
            if (type.isA(typeConstraint))
                {
                return ensureMappingByType(type.as(Type<ObjectType>));
                }
            else
                {
                throw new MissingMapping($"Mapping for \"{typeName}\" is of type {type},"
                        + $" but specified type constraint is {typeConstraint}");
                }
            }
        else
            {
            return mapper.ensureMappingByName(typeName, typeConstraint);
            }
        }

    /**
     * Search for (or create if possible) a mapping for the specified type.
     *
     * @param type  the type for the Mapping
     *
     * @return the selected Mapping
     *
     * @throws MissingMapping  if no appropriate mapping can be provided
     */
    <ObjectType> Mapping<ObjectType> ensureMappingByType(Type<ObjectType> type)
        {
        if (val mapping := mappingByType.get(type))
            {
            return mapping.as(Mapping<ObjectType>);
            }
        else
            {
            return mapper.ensureMappingByType(type);
            }
        }

    /**
     * A lazily instantiated type mapping service.
     */
    @Lazy
    MappingService mapper.calc()
        {
        return new MappingService();
        }

    /**
     * A service that handles the Mapping lookups (and if necessary, creation) when the Mapping is
     * not obvious or present in the Schema.
     */
    service MappingService
        {
        construct()
            {
            // clone the look-up tables from the schema
            this.mappingByType = this.Schema.mappingByType; // TODO CP add clone
            this.typeByName    = new HashMap<String, Type>().putAll(this.Schema.typeByName);
            }


        // ----- API -------------------------------------------------------------------------------

        /**
         * Search for a mapping for the specified type name and type constraint.
         *
         * @param typeName        the name used to find the specific type mapping
         * @param typeConstraint  the type constraint for the Mapping (which may indicate the same
         *                        type as the `typeName`, or may be a wider type thereof)
         *
         * @return the selected Mapping
         *
         * @throws MissingMapping  if no appropriate mapping can be provided
         */
        <ObjectType> Mapping<ObjectType> ensureMappingByName(String typeName, Type<ObjectType> typeConstraint)
            {
            // try to parse the name into a type of the current type system
            TODO reflection-based name-to-type conversion
            }

        /**
         * Search for (or create if possible) a mapping for the specified type.
         *
         * @param type  the type for the Mapping
         *
         * @return the selected Mapping
         *
         * @throws MissingMapping  if no appropriate mapping can be provided
         */
        <ObjectType> Mapping<ObjectType> ensureMappingByType(Type<ObjectType> type)
            {
// TODO
//                {
//                return True, mappingByType[typeAlt].as(Mapping<ObjectType>);
//                }

            if (enableReflection)
                {
//                return mapper.createReflectionMapping(type);
                }

            TODO
            }


        // ----- properties ------------------------------------------------------------------------

        /**
         * A lookup cache from Ecstasy type to JSON Mapping.
         */
        protected/private ListMap<Type, Mapping> mappingByType;

        /**
         * A lookup cache from JSON metadata type name to Ecstasy type.
         */
        protected/private Map<String, Type> typeByName;

        /**
         * Given a `Type`, check if there is a compatible `Mapping`, such as one that handles a
         * superclass of the specified `Type`.
         *
         * @param type  the `Type` to find a Mapping for
         *
         * @return True iff a compatible Mapping type was found
         * @return (conditional) the `Type` for which a compatible `Mapping` exists
         *
         * @throws UnsupportedOperation if no compatible Mapping can be found
         */
        <ObjectType> conditional Type<ObjectType> selectType(Type<ObjectType> type)
            {
// TODO
//            if (Type mappingType := cache.get(type))
//                {
//                return True, mappingType.as(Type<ObjectType>);
//                }

// TODO
//            for (Type mappingType : mappingByType.keys)
//                {
//                if (type.isA(mappingType))
//                    {
//                    cache.put(type, mappingType);
//                    return True, mappingType.as(Type<ObjectType>);
//                    }
//                }

            return False;
            }

        /**
         * Create a reflection-based mapping for the specified type.
         *
         * @param type  the type for the Mapping
         *
         * @return True iff a Mapping could be created
         * @return (conditional) the created Mapping for the specified type
         */
        protected <ObjectType> conditional Mapping<ObjectType> createReflectionMapping(Type<ObjectType> type)
            {
            // TODO
            return False;
            }


        /**
         * Given a `Type`, obtain a [ReflectionMapping] instance for that type.
         *
         * @param type  the `Type` to obtain a Mapping for
         *
         * @return a [ReflectionMapping] instance for that type
         */
        <ObjectType> Mapping<ObjectType> ensureMapping(Type<ObjectType> type)
            {
//            if (Mapping<> mapping := cache.get(type))
//                {
//                return mapping.as(Mapping<ObjectType>);
//                }

            assert type == ObjectType;
            TODO CP
//            val mapping = new ReflectionMapping<ObjectType>();
//            cache.put(type, mapping);
//            return mapping;
            }

        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Examine the specified name to determine if it is part of a name/value pair that holds
     * metadata information.
     *
     * @param name  a name from a name/value pair in a JSON object
     *
     * @return True iff the name/value pair should be treated as metadata
     */
    Boolean isMetadata(String name)
        {
        return name.size >= 1 && switch (name[0])
            {
            case '$': True;
            case '@': True;
            case '_': True;
            default : False;
            };
        }

    /**
     * Convert a JSON pointer to a String that can be used as part of a URI fragment.
     *
     * @param a JSON pointer
     *
     * @return the corresponding ASCII string that can be used in the "fragment" portion of a URI
     */
    static String pointerToUri(String pointer)
        {
        Int length = estimatePointerUriLength(pointer);
        return length == pointer.size
                ? pointer
                : appendPointerUri(pointer, new StringBuffer(length)).toString();
        }

    /**
     * For each ASCII code 0-127, this specifies if the ASCII code is permitted in the "fragment"
     * portion of the URI without being escaped.
     */
    static Boolean[] FRAGMENT_ALLOW =
    //    (control characters...............)  !  $ &' ()*+,-./ 01234567 89:; = ? @ABCDEFG HIJKLMNO PQRSTUVW XYZ    _  abcdefg hijklmno pqrstuvw xyz   ~
        0b00000000_00000000_00000000_00000000_01001011_11111111_11111111_11110101_11111111_11111111_11111111_11100001_01111111_11111111_11111111_11100010
        .toUInt128().toBooleanArray();

    /**
     * Estimate the number of ASCII characters necessary to encode a JSON pointer into the fragment
     * portion of a URI.
     *
     * @param the JSON pointer
     *
     * @return the number of ASCII characters necessary to encode the JSON pointer into the fragment
     *         portion of a URI
     */
    static Int estimatePointerUriLength(String pointer)
        {
        Int length = pointer.size;
        for (Char ch : pointer)
            {
            Int n = ch.toInt();
            if (n <= 0x7F)
                {
                if (!FRAGMENT_ALLOW[n])
                    {
                    length += 2;
                    }
                }
            else
                {
                // first encode the character as UTF8, then convert each non-ASCII byte (i.e. all of
                // them) to the %-encoded form
                length += ch.calcUtf8Length() * 3;
                }
            }
        return length;
        }

    /**
     * Encode a JSON pointer into a sequence of ASCII characters that can be used in the fragment
     * portion of a URI.
     *
     * @param pointer  the JSON pointer
     * @param buf      the Char Appender to encode the JSON pointer into
     *
     * @return the Char Appender
     */
    static <Buf extends Appender<Char>> Buf appendPointerUri(String pointer, Buf buf)
        {
        for (Char ch : pointer)
            {
            Int n = ch.toInt();
            if (n <= 0x7F)
                {
                if (FRAGMENT_ALLOW[n])
                    {
                    buf.add(ch);
                    }
                else
                    {
                    buf.add('%').add((n >>> 4).toHexit()).add(n.toHexit());
                    }
                }
            else
                {
                // first encode the character as UTF8, then convert each non-ASCII byte (i.e. all of
                // them) to the %-encoded form
                for (Byte b : ch.utf())
                    {
                    buf.add('%').add((b >>> 4).toHexit()).add(b.toHexit());
                    }
                }
            }
        return buf;
        }
    }