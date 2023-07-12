import xunit.ExecutionContext;
import xunit.ParameterResolver;

import xunit_engine.Model;
import xunit_engine.UniqueId;

import xunit_engine.executor.EngineExecutionContext;
import xunit_engine.executor.ExecutionLifecycle;
import xunit_engine.executor.MethodExecutor;

import xunit_engine.models.BaseModel;

/**
 * Test for the `MethodExecutorTest` class.
 */
class MethodExecutorTest {

    @Test
    void shouldExecuteNoArgFunction() {
        function Int () fn = () -> 19;

        Model                  model    = new ModelStub();
        EngineExecutionContext ctx      = EngineExecutionContext.create(model);
        MethodExecutor         executor = new MethodExecutor();
        Tuple                  result   = executor.invoke(fn, ctx);
        assert:test result.size == 1;
        assert:test result[0].is(Int);
        assert:test result[0].as(Int) == 19;
    }

    @Test
    void shouldExecuteFunctionWithArgs() {
        function String (String, String) fn = (s1, s2) -> s1 + s2;

        Model                  model    = new ModelStub();
        EngineExecutionContext ctx      = EngineExecutionContext.create(model);
        ResolverStub           resolver = new ResolverStub(["Foo", "Bar"]);

        ctx.registry.register(ParameterResolver, resolver);

        MethodExecutor executor = new MethodExecutor();
        Tuple                 result   = executor.invoke(fn, ctx);
        assert:test result.size == 1;
        assert:test result[0].is(String);
        assert:test result[0].as(String) == "FooBar";
    }

    static const ModelStub()
            extends BaseModel(UniqueId.forClass(MethodExecutorTest), "Stub", False) {
        @Override
        ExecutionLifecycle createExecutionLifecycle() {
            TODO
        }
    }

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