import Ecstasy.reflect.Annotation;
import Ecstasy.reflect.MultiMethod;

/**
 * The native Type implementation.
 */
const RTType<DataType, OuterType>
        implements Type<DataType, OuterType>
    {
    @Override @RO Form form                               .get() { TODO("native"); }
    @Override @RO Type[] underlyingTypes                  .get() { TODO("native"); }
    @Override @RO Property<DataType>[] properties         .get() { TODO("native"); }
    @Override @RO Property[] constants                    .get() { TODO("native"); }
    @Override @RO Map<String, MultiMethod> multimethods   .get() { TODO("native"); }
    @Override @RO Method<DataType>[] methods              .get() { TODO("native"); }
    @Override @RO Function[] functions                    .get() { TODO("native"); }
    @Override @RO Constructor[] constructors              .get() { TODO("native"); }
    @Override @RO Type!<>[] childTypes                    .get() { TODO("native"); }
    @Override @RO Boolean recursive                       .get() { TODO("native"); }
    @Override @RO Boolean explicitlyImmutable             .get() { TODO("native"); }

    @Override conditional Class fromClass()                      { TODO("native"); }
    @Override conditional Property fromProperty()                { TODO("native"); }
    @Override conditional Type!<> modifying()                    { TODO("native"); }
    @Override conditional (Type!<>, Type!<>) relational()        { TODO("native"); }
    @Override conditional String named()                         { TODO("native"); }
    @Override conditional Type!<> contained()                    { TODO("native"); }
    @Override conditional Access accessSpecified()               { TODO("native"); }
    @Override conditional Annotation annotated()                 { TODO("native"); }
    @Override conditional Type!<>[] parameterized()              { TODO("native"); }

    @Override Type!<> purify()                                   { TODO("native"); }

    @Override @Op("+") Type!<> add(Type!<> that)                 { TODO("native"); }
    @Override @Op("+") Type!<> add(Method... methods)            { TODO("native"); }
    @Override @Op("+") Type!<> add(Property... properties)       { TODO("native"); }
    @Override @Op("|") Type!<> or(Type!<> that)                  { TODO("native"); }
    @Override @Op("&") Type!<> and(Type!<> that)                 { TODO("native"); }
    @Override @Op("-") Type!<> sub(Type!<> that)                 { TODO("native"); }
    @Override @Op("-") Type!<> sub(Method... methods)            { TODO("native"); }
    @Override @Op("-") Type!<> sub(Property... properties)       { TODO("native"); }

    // natural code:
    //   Boolean isA(Type!<> that)
    //   Boolean duckTypeableTo(Type!<> that)
    //   Boolean consumesFormalType(String typeName)
    //   Boolean producesFormalType(String typeName)
    //   Boolean isInstance(Object o)
    //   DataType cast(Object o)
    //   conditional function DataType() defaultConstructor(OuterType? outer = Null)
    //   conditional function DataType(Struct) structConstructor(OuterType? outer = Null)
    //   Int estimateStringLength()
    //   void appendTo(Appender<Char> appender)
    }
