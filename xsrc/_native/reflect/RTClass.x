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

    @Override String name                                                               .get() { TODO("native"); }
    @Override @Unassigned Composition composition                                       .get() { TODO("native"); }
    @Override @Unassigned ListMap<String, Type> formalTypes                             .get() { TODO("native"); }
    @Override @Unassigned protected function StructType()? allocateStruct               .get() { assert;         }
    @Override Boolean abstract                                                          .get() { TODO("native"); }
    @Override Boolean virtualChild                                                      .get() { TODO("native"); }

    @Override Boolean extends(Class!<> clz)                                                    { TODO("native"); }
    @Override Boolean incorporates(Class!<> clz)                                               { TODO("native"); }
    @Override Boolean implements(Class!<> clz)                                                 { TODO("native"); }
    @Override Boolean derivesFrom(Class!<> clz)                                                { TODO("native"); }
    @Override conditional PublicType isSingleton()                                             { TODO("native"); }
    @Override conditional StructType allocate()                                                { TODO("native"); }
    }
