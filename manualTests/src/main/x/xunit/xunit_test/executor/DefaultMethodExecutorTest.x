import xunit.ExecutionContext;
import xunit.Model;
import xunit.ParameterResolver;
import xunit.UniqueId;
import xunit.executor.DefaultExecutionContext;
import xunit.executor.DefaultMethodExecutor;
import xunit.models.BaseModel;

/**
 * Test for the `DefaultMethodExecutorTest` class.
 */
class DefaultMethodExecutorTest {

    @Test
    void shouldExecuteNoArgFunction() {
        function Int () fn = () -> 19;

        Model                 model    = new ModelStub();
        ExecutionContext      ctx      = DefaultExecutionContext.create(model);
        DefaultMethodExecutor executor = new DefaultMethodExecutor();
        Tuple                 result   = executor.invoke(fn, ctx);
        assert:test result.size == 1;
        assert:test result[0].is(Int);
        assert:test result[0].as(Int) == 19;
    }

    @Test
    void shouldExecuteFunctionWithArgs() {
        function String (String, String) fn = (s1, s2) -> s1 + s2;

        Model            model    = new ModelStub();
        ExecutionContext ctx      = DefaultExecutionContext.create(model);
        ResolverStub     resolver = new ResolverStub(["Foo", "Bar"]);

        ctx.repository.register(ParameterResolver, resolver);

        DefaultMethodExecutor executor = new DefaultMethodExecutor();
        Tuple                 result   = executor.invoke(fn, ctx);
        assert:test result.size == 1;
        assert:test result[0].is(String);
        assert:test result[0].as(String) == "FooBar";
    }

    static const ModelStub()
            extends BaseModel(UniqueId.forClass(DefaultMethodExecutorTest), "Stub", False);

    static const ResolverStub(Object[] values)
            implements ParameterResolver {
        @Override
        <ParamType> conditional ParamType resolve(ExecutionContext context, Parameter<ParamType> param) {
            if (param.ordinal < values.size) {
                Object o = values[param.ordinal];
                if (o.is(ParamType))
                    {
                    return True, o.as(ParamType);
                    }
            }
            return False;
        }
    }
}