import ecstasy.TypeSystem;

import ecstasy.reflect.Access;
import ecstasy.reflect.Annotation;
import ecstasy.reflect.MultiMethod;
import ecstasy.reflect.TypeTemplate;

/**
 * The native Type implementation.
 */
const RTType<DataType, OuterType>
        implements Type<DataType, OuterType>
    {
    @Override @RO Map<String, Type!<>> childTypes                 .get() { TODO("native"); }
    @Override @RO Property[]           constants                  .get() { TODO("native"); }
    @Override @RO Constructor[]        constructors               .get() { TODO("native"); }
    @Override @RO Boolean              explicitlyImmutable        .get() { TODO("native"); }
    @Override @RO Form                 form                       .get() { TODO("native"); }
    @Override @RO Function[]           functions                  .get() { TODO("native"); }
    @Override @RO Method<DataType>[]   methods                    .get() { TODO("native"); }
    @Override @RO Property<DataType>[] properties                 .get() { TODO("native"); }
    @Override @RO Boolean              recursive                  .get() { TODO("native"); }
    @Override @RO TypeTemplate         template                   .get() { TODO("native"); }
    @Override @RO TypeSystem           typeSystem                 .get() { TODO("native"); }
    @Override @RO Type[]               underlyingTypes            .get() { TODO("native"); }

    @Override conditional Class fromClass()                              { TODO("native"); }
    @Override conditional Property fromProperty()                        { TODO("native"); }
    @Override conditional Type!<> modifying()                            { TODO("native"); }
    @Override conditional (Type!<>, Type!<>) relational()                { TODO("native"); }
    @Override conditional String named()                                 { TODO("native"); }
    @Override conditional Type!<> contained()                            { TODO("native"); }
    @Override conditional Access accessSpecified()                       { TODO("native"); }
    @Override conditional Annotation annotated()                         { TODO("native"); }
    @Override conditional Type!<>[] parameterized()                      { TODO("native"); }

    @Override Type!<> purify()                                           { TODO("native"); }
    @Override Type!<> parameterize(Type!<>... paramTypes)                { TODO("native"); }

    @Override @Op("+") Type!<> add(Type!<> that)                         { TODO("native"); }
    @Override @Op("+") Type!<> add(Method... methods)                    { TODO("native"); }
    @Override @Op("+") Type!<> add(Property... properties)               { TODO("native"); }
    @Override @Op("-") Type!<> sub(Type!<> that)                         { TODO("native"); }
    @Override @Op("-") Type!<> sub(Method... methods)                    { TODO("native"); }
    @Override @Op("-") Type!<> sub(Property... properties)               { TODO("native"); }
    @Override @Op("&") Type!<> and(Type!<> that)                         { TODO("native"); }
    @Override @Op("|") Type!<> or(Type!<> that)                          { TODO("native"); }

    // natural code:
    //   Boolean isA(Type!<> that)
    //   Boolean duckTypeableTo(Type!<> that)
    //   Boolean consumesFormalType(String typeName)
    //   Boolean producesFormalType(String typeName)
    //   Boolean isInstance(Object o)
    //   DataType cast(Object o)
    //   conditional function DataType() defaultConstructor(OuterType? outer = Null)
    //   conditional function DataType(Struct) structConstructor(OuterType? outer = Null)

    @Override
    @Lazy Map<String, MultiMethod<DataType>> multimethods.calc()
        {
        import ecstasy.collections.HashMap;

        Map<String, MultiMethod<DataType>> multis = new HashMap();

        Map<String, MultiMethod<DataType>>.Entry append(
                MultiMethod<DataType>.Callable           callable,
                Map<String, MultiMethod<DataType>>.Entry entry)
            {
            entry.value = entry.exists
                    ? entry.value + callable
                    : new MultiMethod<DataType>(callable.name, [callable]);
            return entry;
            }

        for (Method<DataType> method : methods)
            {
            multis.process(method.name, append(method, _));
            }

        for (Function func : functions)
            {
            multis.process(func.name, append(func, _));
            }

        return multis; // .ensureImmutable(True);
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        if (String name := named())
            {
            if (Access access := accessSpecified())
                {
                return name.size + 1 + access.keyword.size;
                }

            return name.size;
            }

        function Int sum(Int, Int) = (n1, n2) -> n1 + n2 + 2;

        return 6 + properties.iterator().map(p -> p.name.size+2).reduce(0, sum)
                 + methods   .iterator().map(m -> m.name.size+4).reduce(0, sum);
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        if (String name := named())
            {
            appender.add(name);
            if (Access access := accessSpecified())
                {
                appender.add(':').add(access.keyword);
                }
            }
        else
            {
            appender.add("Type[");

            Boolean first = True;
            for (Property property : properties)
                {
                if (first)
                    {
                    first = False;
                    }
                else
                    {
                    appender.add(", ");
                    }
                property.name.appendTo(appender);
                }

            for (Method method : methods)
                {
                if (first)
                    {
                    first = False;
                    }
                else
                    {
                    appender.add(", ");
                    }
                method.name.appendTo(appender);
                appender.add("())");
                }

            appender.add(']');
            }
        }
    }
