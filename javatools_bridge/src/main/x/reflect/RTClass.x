import ecstasy.collections.ListMap;
import ecstasy.reflect.Annotation;
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
        Int size = 0;

        (_, Annotation[] annotations) = deannotate();
        if (annotations.size > 0)
            {
            for (Annotation annotation : annotations)
                {
                size += annotation.estimateStringLength();
                }
            size += annotations.size; // spaces
            }

        size += displayName.size;

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
    void appendTo(Appender<Char> buf)
        {
        (_, Annotation[] annotations) = deannotate();
        if (annotations.size > 0)
            {
            for (Annotation annotation : annotations.reverse())
                {
                annotation.appendTo(buf);
                buf.add(' ');
                }
            }

        displayName.appendTo(buf);

        ListMap<String, Type> params = formalTypes;
        if (!params.empty)
            {
            buf.add('<');
            Params: for (Type type : params.values)
                {
                if (!Params.first)
                    {
                    ", ".appendTo(buf);
                    }
                type.appendTo(buf);
                }
            buf.add('>');
            }
        }
    }
