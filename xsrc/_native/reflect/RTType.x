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

    @Override conditional Access       accessSpecified()                         { TODO("native"); }
    @Override             Type!<>      annotate(Annotation... annotations)       { TODO("native"); }
    @Override conditional Annotation   annotated()                               { TODO("native"); }
    @Override conditional Type         contained()                               { TODO("native"); }
    @Override conditional Class        fromClass()                               { TODO("native"); }
    @Override conditional Property     fromProperty()                            { TODO("native"); }
    @Override conditional Type         modifying()                               { TODO("native"); }
    @Override conditional String       named()                                   { TODO("native"); }
    @Override             Type         parameterize(Type... paramTypes)          { TODO("native"); }
    @Override conditional Type[]       parameterized()                           { TODO("native"); }
    @Override             Type         purify()                                  { TODO("native"); }
    @Override conditional (Type, Type) relational()                              { TODO("native"); }

    @Override @Op("+") Type add(Type! that)                                      { TODO("native"); }
    @Override @Op("+") Type add(Method... methods)                               { TODO("native"); }
    @Override @Op("+") Type add(Property... properties)                          { TODO("native"); }
    @Override @Op("-") Type sub(Type! that)                                      { TODO("native"); }
    @Override @Op("-") Type sub(Method... methods)                               { TODO("native"); }
    @Override @Op("-") Type sub(Property... properties)                          { TODO("native"); }
    @Override @Op("&") Type and(Type! that)                                      { TODO("native"); }
    @Override @Op("|") Type or(Type! that)                                       { TODO("native"); }

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
        switch (form)
            {
            case Pure:
                function Int sum(Int, Int) = (n1, n2) -> n1 + n2 + 2;
                return 6 + properties.iterator().map(p -> p.name.size+2).reduce(0, sum)
                         + methods   .iterator().map(m -> m.name.size+4).reduce(0, sum);

            case Class:
                assert Class clz := fromClass();
                return clz.displayName.size + estimateParameterizedStringLength();

            case Property:
                assert Property prop := fromProperty();
                return prop.Target.estimateStringLength() + 1 + prop.name.size;

            case Child:
                assert String name := named();
                return OuterType.estimateStringLength() + 1 + name.size + estimateParameterizedStringLength();

            case Intersection:
                assert (Type t1, Type t2) := relational();
                return t1 == Type<Nullable>
                        ? t2.estimateStringLength() + (t2.relational() ? 3 : 1)
                        : t1.estimateStringLength() + 3 + t2.estimateStringLength();

            case Union:
            case Difference:
                assert (Type t1, Type t2) := relational();
                return t1.estimateStringLength() + 3 + t2.estimateStringLength();

            case Immutable:
                assert Type t := modifying();
                return "immutable ".size + t.estimateStringLength();

            case Access:
                assert Type t := modifying();
                assert Access access := accessSpecified();
                return t.estimateStringLength() + 1 + access.keyword.size;

            case Annotated:
                assert Annotation anno := annotated();
                assert Type t := modifying();
                return anno.estimateStringLength() + 1 + t.estimateStringLength();

            case Sequence:
                Type[] types = underlyingTypes;
                return types.size == 0
                        ? 2
                        : types.iterator().map(t -> t.estimateStringLength() + 2)
                                          .reduce(0, (n1, n2) -> n1 + n2);

            case FormalProperty:
            case FormalParameter:
            case FormalChild:
            case Typedef:
                assert String name := named();
                return name.size;
            }
        }

    private Int estimateParameterizedStringLength()
        {
        if (Type[] params := parameterized())
            {
            return params.size == 0
                    ? 2
                    : params.iterator().map(t -> t.estimateStringLength() + 2)
                                       .reduce(0, (n1, n2) -> n1 + n2);
            }
        else
            {
            return 0;
            }
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        switch (form)
            {
            case Pure:
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
                break;

            case Class:
                assert Class clz := fromClass();
                clz.displayName.appendTo(appender);
                appendParameterizedTo(appender);
                break;

            case Property:
                assert Property prop := fromProperty();
                prop.Target.appendTo(appender);
                appender.add('.');
                prop.name.appendTo(appender);
                break;

            case Child:
                OuterType.appendTo(appender);
                appender.add('.');
                assert String name := named();
                name.appendTo(appender);
                appendParameterizedTo(appender);
                break;

            case Intersection:
                assert (Type t1, Type t2) := relational();
                if (t1 == Type<Nullable>)
                    {
                    if (t2.relational())
                        {
                        appender.add('(');
                        t2  .appendTo(appender);
                        ")?".appendTo(appender);
                        }
                    else
                        {
                        t2.appendTo(appender);
                        appender.add('?');
                        }
                    }
                else
                    {
                    t1   .appendTo(appender);
                    " | ".appendTo(appender);
                    t2   .appendTo(appender);
                    }
                break;


            case Union:
                assert (Type t1, Type t2) := relational();
                t1   .appendTo(appender);
                " + ".appendTo(appender);
                t2   .appendTo(appender);
                break;

            case Difference:
                assert (Type t1, Type t2) := relational();
                t1   .appendTo(appender);
                " - ".appendTo(appender);
                t2   .appendTo(appender);
                break;

            case Immutable:
                "immutable ".appendTo(appender);
                assert Type t := modifying();
                t.appendTo(appender);
                break;

            case Access:
                assert Type t := modifying();
                t.appendTo(appender);
                appender.add(':');
                assert Access access := accessSpecified();
                access.keyword.appendTo(appender);
                break;

            case Annotated:
                assert Annotation anno := annotated();
                anno.appendTo(appender);
                appender.add(' ');
                assert Type t := modifying();
                t.appendTo(appender);
                break;

            case Sequence:
                Type[] types = underlyingTypes;
                appender.add('<');
                Loop: for (Type type : types)
                    {
                    if (!Loop.first)
                        {
                        ", ".appendTo(appender);
                        }
                    type.appendTo(appender);
                    }
                appender.add('>');
                break;

            case FormalProperty:
            case FormalParameter:
            case FormalChild:
            case Typedef:
                assert String name := named();
                name.appendTo(appender);
                break;
            }
        }

    private void appendParameterizedTo(Appender<Char> appender)
        {
        if (Type[] params := parameterized())
            {
            appender.add('<');
            Loop: for (Type param : params)
                {
                if (!Loop.first)
                    {
                    ", ".appendTo(appender);
                    }
                param.appendTo(appender);
                }
            appender.add('>');
            }
        }
    }
