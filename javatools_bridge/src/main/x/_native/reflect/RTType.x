import ecstasy.collections.Hasher;
import ecstasy.collections.NaturalHasher;

import ecstasy.reflect.Access;
import ecstasy.reflect.Annotation;
import ecstasy.reflect.MultiMethod;
import ecstasy.reflect.TypeTemplate;

/**
 * The native Type implementation.
 */
const RTType<DataType, OuterType>
        implements Type<DataType, OuterType> {
    @Override @RO Map<String, Type>    childTypes                         .get() { TODO("native"); }
    @Override @RO Property[]           constants                          .get() { TODO("native"); }
    @Override @RO Constructor[]        constructors                       .get() { TODO("native"); }
    @Override @RO Boolean              explicitlyImmutable                .get() { TODO("native"); }
    @Override @RO Form                 form                               .get() { TODO("native"); }
    @Override @RO Function[]           functions                          .get() { TODO("native"); }
    @Override @RO Method<DataType>[]   methods                            .get() { TODO("native"); }
    @Override @RO Property<DataType>[] properties                         .get() { TODO("native"); }
    @Override @RO Boolean              recursive                          .get() { TODO("native"); }
    @Override @RO TypeTemplate         template                           .get() { TODO("native"); }
    @Override @RO TypeSystem           typeSystem                         .get() { TODO("native"); }
    @Override @RO Type[]               underlyingTypes                    .get() { TODO("native"); }

    @Override conditional Access          accessSpecified()                      { TODO("native"); }
    @Override             Type            annotate(Annotation annotation)        { TODO("native"); }
    @Override conditional Annotation      annotated()                            { TODO("native"); }
    @Override conditional Type            contained()                            { TODO("native"); }
    @Override conditional Class<DataType> fromClass()                            { TODO("native"); }
    @Override conditional Property        fromProperty()                         { TODO("native"); }
    @Override conditional (Type, Type)    relational()                           { TODO("native"); }
    @Override conditional Type            modifying()                            { TODO("native"); }
    @Override conditional String          named()                                { TODO("native"); }
    @Override             Type            parameterize(Type[] paramTypes=[])     { TODO("native"); }
    @Override conditional Type[]          parameterized()                        { TODO("native"); }
    @Override             Type            purify()                               { TODO("native"); }
    @Override conditional Type            resolveFormalType(String typeName)     { TODO("native"); }

    @Override @Op("+") Type add(Type that)                                       { TODO("native"); }
    @Override @Op("+") Type add(Method[] methods=[])                             { TODO("native"); }
    @Override @Op("+") Type add(Property[] properties=[])                        { TODO("native"); }
    @Override @Op("-") Type sub(Type that)                                       { TODO("native"); }
    @Override @Op("-") Type sub(Method[] methods=[])                             { TODO("native"); }
    @Override @Op("-") Type sub(Property[] properties=[])                        { TODO("native"); }
    @Override @Op("&") Type and(Type that)                                       { TODO("native"); }
    @Override @Op("|") Type or(Type that)                                        { TODO("native"); }

    // natural code:
    //   Boolean isA(Type that)
    //   Boolean duckTypeableTo(Type that)
    //   Boolean consumesFormalType(String typeName)
    //   Boolean producesFormalType(String typeName)
    //   Boolean isInstance(Object o)
    //   DataType cast(Object o)
    //   conditional function DataType() defaultConstructor(OuterType? outer = Null)
    //   conditional function DataType(Struct) structConstructor(OuterType? outer = Null)

    @Override
    conditional Hasher<DataType> hashed() {
        Hasher<DataType>? hasher = this.hasher;
        if (hasher == Null) {
            return False;
        }
        return True, hasher;
    }

    private @Lazy Hasher<DataType>? hasher.calc() {
        Type typeActual = this;
        if (Hasher<typeActual.DataType> hasher := createHasher()) {
            return hasher;
        }
        return Null;
    }

    @Override
    @Lazy Iterator<DataType> emptyIterator.calc() {
        return createEmptyIterator();
    }

    @Override
    @Lazy Map<String, MultiMethod<DataType>> multimethods.calc() {
        return collectMultimethods();
    }
}