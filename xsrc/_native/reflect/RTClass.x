import ecstasy.collections.ListMap;
import ecstasy.reflect.ClassTemplate.Composition;

/**
 * The native Class implementation.
 */
const RTClass<PublicType, ProtectedType extends PublicType,
                        PrivateType   extends ProtectedType,
                        StructType    extends Struct>
        extends Class<PublicType, ProtectedType, PrivateType, StructType>
    {
    construct() {}

    @Override Boolean                                      abstract       .get() { TODO("native"); }
    @Override @Unassigned protected function StructType()? allocateStruct .get() { assert;         }
    @Override @Unassigned Composition                      composition    .get() { TODO("native"); }
    @Override String?                                      implicitName   .get() { TODO("native"); }
    @Override String                                       name           .get() { TODO("native"); }
    @Override String                                       path           .get() { TODO("native"); }
    @Override Boolean                                      virtualChild   .get() { TODO("native"); }

    @Override             Boolean    extends(Class!<> clz)                       { TODO("native"); }
    @Override             Boolean    incorporates(Class!<> clz)                  { TODO("native"); }
    @Override             Boolean    implements(Class!<> clz)                    { TODO("native"); }
    @Override             Boolean    derivesFrom(Class!<> clz)                   { TODO("native"); }
    @Override conditional PublicType isSingleton()                               { TODO("native"); }
    @Override conditional StructType allocate()                                  { TODO("native"); }
    (String[], Type[])               getFormalNamesAndTypes()                    { TODO("native"); }

    @Override
    @Lazy ListMap<String, Type> canonicalParams.calc()
        {
        (String[] names, Type[] types) = getFormalNamesAndTypes();
        return new ListMap<String, Type>(names, types).ensureImmutable(true);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int size = displayName.size;

        ListMap<String, Type> params = formalTypes;
        if (!params.empty)
            {
            size += 2;
            Params: for (Type type : params.values)
                {
                if (!Params.first)
                    {
                    size += 2;
                    }
                size += type.estimateStringLength();
                }
            }

        return size;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        // TODO annotations

        appender.add(displayName);

        ListMap<String, Type> params = formalTypes;
        if (!params.empty)
            {
            appender.add('<');
            Params: for (Type type : params.values)
                {
                if (!Params.first)
                    {
                    appender.add(", ");
                    }
                type.appendTo(appender);
                }
            appender.add('>');
            }
        }
    }
