import ecstasy.reflect.Annotation;

import extensions.ExecutionContext;
import extensions.Extension;
import extensions.ParameterResolver;
import extensions.ResourceRegistry;
import extensions.ResourceRegistry.Resource;

import templates.TestTemplate;
import templates.TestTemplateContext;
import templates.TestTemplateFactory;

/**
 * A parameterized test.
 *
 * @param parameters  the function to use to provide parameters for parameterized test methods
 *                    or parameterized test class constructors.
 * @param group       the test group to which this test belongs
 */
annotation ParameterizedTest(Function<Tuple, Tuple> parameters, String group=Test.Unit)
        extends TestTemplate(group)
        into MethodOrFunction | Class {

    @Override
    TestTemplateFactory[] getTemplateFactories()
            = super() + new ParameterizedTestTemplateFactory(parameters);

    // ---- inner const: Argument ------------------------------------------------------------------

    static const Argument<ArgType>(ArgType arg, String? name = Null);

    // ---- inner const: ParameterizedTestTemplateFactory ------------------------------------------

    /**
     * A `TestTemplateFactory` for parameterized tests.
     *
     * @param parameters  the function to use to provide parameters for parameterized test
     *                    methods or parameterized test class constructors.
     */
    static const ParameterizedTestTemplateFactory(Function<Tuple, Tuple> parameters)
            implements TestTemplateFactory<ParameterizedTest> {

        @Override
        Iterable<TestTemplateContext> getContexts(ExecutionContext context) {
            List<ParameterizedTestTemplateContext> templateContexts = new Array();
            if (var o := context.invokeSingleResult(parameters)) {
                if (o.is(Collection)) {
                    for (var arg : o) {
                        Argument[] args = createArguments(arg);
                        templateContexts.add(new ParameterizedTestTemplateContext(args));
                    }
                } else {
                    Argument[] args = createArguments(o);
                    templateContexts.add(new ParameterizedTestTemplateContext(args));
                }
            }
            return templateContexts;
        }
    
        /**
         * Create the arguments array for a given result from the arguments provider function.
         *
         * The arguments provider function may have returned a `Collection`, a `Tuple`, or a single
         * value. If it returned a `Collection`, the `o` parameter will be one of the values from
         * that `Collection`, which could also be a `Collection, a `Tuple`, or a single value.
         * Consequently the `o` parameter may be a `Collection`, a `Tuple`, or a single value.
         *
         * * `Collection` - each value in the `Collection` is used to create an entry in the
         *   returned `Argument` array. If an entry in the `Collection` is an `Argument` it is
         *   added directly to the argument array, otherwise it is wrapped in an `Argument`.
         *
         * * `Tuple` - each value in the `Tuple` is used to create an entry in the returned
         *   `Argument` array. If an entry in the `Collection` is an `Argument` it is added
         *    directly to the argument array, otherwise it is wrapped in an `Argument`.
         *
         * * Single value - A single value will create a single entry `Argument` array.
         *   If value is an `Argument` it is added directly to the argument array, otherwise it is
         *   wrapped in an `Argument`.
         *
         * @param value  a result from calling the arguments provider function
         *
         * @return the array of `Argument`s created from the parameter `Object`
         */
        private Argument[] createArguments(Object value) {
            switch (value.is(_)) {
            case Tuple:
                // value is a Tuple from a multi return
                Argument[] args = new Array(value.size);
                for (Int i : 0 ..< value.size) {
                    var argument = value[i];
                    if (argument.is(Argument)) {
                        args[i] = argument;
                    } else {
                        args[i] = new Argument(argument);
                    }
                }
                return args;
            case Collection:
                Argument[] args = new Array(value.size);
                Int        i    = 0;
                for (var argument : value) {
                    if (argument.is(Argument)) {
                        args[i++] = argument;
                    } else {
                        args[i++] = new Argument(argument);
                    }
                }
                return args;
            case Argument:
                // value is already an argument
                return [value];
            default:
                // value is a single Object
                return [new Argument(value)];
            }
        }
    }

    // ---- inner const: ParameterizedTestTemplateContext ------------------------------------------

    /**
     * A `TestTemplateContext` representing a parameterized test.
     *
     * @param arguments  the parameterized test arguments
     */
    static const ParameterizedTestTemplateContext(Argument[] arguments)
            implements TestTemplateContext {

        @Override
        String getDisplayName(Int iteration) {
            StringBuffer buf = new StringBuffer();
            buf.append($"[{iteration}] ");
            for (Int i : 0 ..< arguments.size) {
                if (i > 0) {
                    buf.append(", ");
                }
                var arg = arguments[i].arg;
                if (arg.is(String)) {
                    buf.append($"\"{arg}\"");
                } else {
                    buf.append($"{arg}");
                }
            }
            return buf.toString();
        }

        @Override
        Extension[] getExtensions(Int iteration) = [];

        @Override
        Resource[] getResources(Int iteration) {
            ParameterizedTestResolver resolver  = new ParameterizedTestResolver(arguments);
            Resource[]                resources = new Array();

            resources.add(ResourceRegistry.resource(resolver, Replace));
            for (Argument arg: arguments) {
                String? name = arg.name;
                if (name.is(String)) {
                    resources.add(ResourceRegistry.resource(arg, name, Replace));
                }
            }
            return resources;
        }
    }

    // ---- inner const: ParameterizedTestResolver -------------------------------------------------

    /**
     * A `ParameterResolver` that resolves `Parameter`s of a parameterized test method
     * or test class constructor.
     */
    static const ParameterizedTestResolver(Argument[] arguments)
            implements ParameterResolver {
        @Override
        <ParamType> conditional ParamType resolve(ExecutionContext context, Parameter<ParamType> param) {
            if (param.ordinal < arguments.size) {
                Argument argument = arguments[param.ordinal];
                var arg = argument.arg;
                if (arg.is(ParamType)) {
                    return True, arg;
                }
                // arg type did not match directly so try Destringable conversion
                Type type = param.ParamType;
                if (arg.is(Stringable), type.is(Type<Destringable>)) {
                    return True, new type.DataType(arg.toString());
                }
            }
            return False;
        }
    }
}
