import xunit.ExtensionProvider;

import xunit.annotations.AfterEach;
import xunit.annotations.BeforeEach;
import xunit.annotations.Disabled;

import xunit.extensions.AfterEachCallback;
import xunit.extensions.BeforeEachCallback;

import xunit_engine.ExecutionConfiguration;
import xunit_engine.Model;
import xunit_engine.UniqueId;

import xunit_engine.executor.EngineExecutionContext;
import xunit_engine.executor.TestExecutor;

import xunit_engine.models.MethodModel;


/**
 * Tests to verify operation of the `TestExecutor` class when executing
 * tests in a `MethodModel`.
 */
class TestExecutorMethodModelTest {

    @Test
    void shouldExecuteSimpleTestMethod() {
        MethodModel                model    = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener = new AssertingExecutionListener();
        TestExecutor               executor = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx      = EngineExecutionContext.builder(model)
                                                                    .withListener(listener)
                                                                    .build();

        executor.execute(ctx);
        listener.forTest(model.uniqueId).assertSuccessfulTest();
    }

    @Test
    void shouldExecuteFailingTestMethod() {
        MethodModel                model    = createMethodModel(TestStub.failingTest);
        AssertingExecutionListener listener = new AssertingExecutionListener();
        TestExecutor               executor = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx      = EngineExecutionContext.builder(model)
                                                                    .withListener(listener)
                                                                    .build();

        executor.execute(ctx);

        listener.forTest(model.uniqueId).assertFailedTest(Assertion);
    }

    @Test
    void shouldExecuteDisabledTestMethod() {
        MethodModel                model    = createMethodModel(TestStub.disabledTest);
        AssertingExecutionListener listener = new AssertingExecutionListener();
        TestExecutor               executor = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx      = EngineExecutionContext.builder(model)
                                                                    .withListener(listener)
                                                                    .build();

        executor.execute(ctx);
        listener.forTest(model.uniqueId).assertSkippedTest("should not be executed");
    }

    @Test
    void shouldExecuteTestWithSingleBefore() {
        Method                     before    = TestStub.beforeTestOne;
        ExtensionProvider[]        providers = before.as(BeforeEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.beforeTestOne)
            .verify(stub, TestStub.successfulTest);
    }

    @Test
    void shouldExecuteTestWithMultipleBefores() {
        Method                     beforeOne = TestStub.beforeTestOne;
        Method                     beforeTwo = TestStub.beforeTestTwo;
        ExtensionProvider[]        providers = beforeOne.as(BeforeEach).getExtensionProviders()
                                               + beforeTwo.as(BeforeEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.beforeTestOne)
            .verify(stub, TestStub.beforeTestTwo)
            .verify(stub, TestStub.successfulTest);
    }

    @Test
    void shouldExecuteTestWithSingleAfter() {
        Method                     after     = TestStub.afterTestOne;
        ExtensionProvider[]        providers = after.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.successfulTest)
            .verify(stub, TestStub.afterTestOne);
    }

    @Test
    void shouldExecuteTestWithMultipleAfters() {
        Method                     afterOne  = TestStub.afterTestOne;
        Method                     afterTwo  = TestStub.afterTestTwo;
        ExtensionProvider[]        providers = afterOne.as(AfterEach).getExtensionProviders()
                                               + afterTwo.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.successfulTest)
            .verify(stub, TestStub.afterTestOne)
            .verify(stub, TestStub.afterTestTwo);
    }

    @Test
    void shouldExecuteTestWithBeforeAndAfter() {
        Method                     before    = TestStub.beforeTestOne;
        Method                     after     = TestStub.afterTestOne;
        ExtensionProvider[]        providers = before.as(BeforeEach).getExtensionProviders()
                                               + after.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.beforeTestOne)
            .verify(stub, TestStub.successfulTest)
            .verify(stub, TestStub.afterTestOne);
    }

    @Test
    void shouldNotExecuteBeforeAndAfterForDisabledTest() {
        Method                     before    = TestStub.beforeTestOne;
        Method                     after     = TestStub.afterTestOne;
        ExtensionProvider[]        providers = before.as(BeforeEach).getExtensionProviders()
                                               + after.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.disabledTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSkippedTest("should not be executed");

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.verifyNoMethodsCalled();
    }

    @Test
    void shouldExecuteBeforeAndAfterForFailedTest() {
        Method                     before    = TestStub.beforeTestOne;
        Method                     after     = TestStub.afterTestOne;
        ExtensionProvider[]        providers = before.as(BeforeEach).getExtensionProviders()
                                               + after.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(TestStub.failingTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertFailedTest(Assertion);

        assert:test ctxFinal.testFixture.is(TestStub);
        TestStub stub = ctxFinal.testFixture.as(TestStub);

        stub.methods.inOrder()
            .verify(stub, TestStub.beforeTestOne)
            .verify(stub, TestStub.failingTest)
            .verify(stub, TestStub.afterTestOne);
    }

    @Test
    void shouldExecuteBeforesAndAftersInPriorityOrder() {
        Method                     beforeOne   = OrderedTestStub.beforeTestOne;
        Method                     beforeTwo   = OrderedTestStub.beforeTestTwo;
        Method                     beforeThree = OrderedTestStub.beforeTestThree;
        Method                     afterOne    = OrderedTestStub.afterTestOne;
        Method                     afterTwo    = OrderedTestStub.afterTestTwo;
        Method                     afterThree  = OrderedTestStub.afterTestThree;
        ExtensionProvider[]        providers   = beforeOne.as(BeforeEach).getExtensionProviders()
                                                 + beforeTwo.as(BeforeEach).getExtensionProviders()
                                                 + beforeThree.as(BeforeEach).getExtensionProviders()
                                                 + afterOne.as(AfterEach).getExtensionProviders()
                                                 + afterTwo.as(AfterEach).getExtensionProviders()
                                                 + afterThree.as(AfterEach).getExtensionProviders();
        MethodModel                model     = createMethodModel(OrderedTestStub.successfulTest);
        AssertingExecutionListener listener  = new AssertingExecutionListener();
        TestExecutor               executor  = new TestExecutor(model, ExecutionConfiguration.create());
        EngineExecutionContext     ctx       = EngineExecutionContext.builder(model)
                                                                     .withListener(listener)
                                                                     .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx, providers);

        listener.forTest(model.uniqueId).assertSuccessfulTest();

        assert:test ctxFinal.testFixture.is(OrderedTestStub);
        OrderedTestStub stub = ctxFinal.testFixture.as(OrderedTestStub);

        stub.methods.inOrder()
            .verify(stub, OrderedTestStub.beforeTestTwo)
            .verify(stub, OrderedTestStub.beforeTestThree)
            .verify(stub, OrderedTestStub.beforeTestOne)
            .verify(stub, OrderedTestStub.successfulTest)
            .verify(stub, OrderedTestStub.afterTestTwo)
            .verify(stub, OrderedTestStub.afterTestThree)
            .verify(stub, OrderedTestStub.afterTestOne);
    }


    /**
     * A stub to represent a test class.
     */
    @MockTest
    static class TestStub {
        MethodVerifier methods = new MethodVerifier();

        construct() {
            methods.reset();
        }

        @BeforeEach
        void beforeTestOne() {
            methods.called(this, beforeTestOne);
        }

        @BeforeEach
        void beforeTestTwo() {
            methods.called(this, beforeTestTwo);
        }

        @AfterEach
        void afterTestOne() {
            methods.called(this, afterTestOne);
        }

        @AfterEach
        void afterTestTwo() {
            methods.called(this, afterTestTwo);
        }

        void successfulTest() {
            methods.called(this, successfulTest);
        }

        void failingTest() {
            methods.called(this, failingTest);
            assert:test;
        }

        @Disabled("should not be executed")
        void disabledTest() {
            methods.called(this, disabledTest);
        }
    }

    /**
     * A stub to represent a test class with ordered methods.
     */
    @MockTest
    static class OrderedTestStub {
        MethodVerifier methods = new MethodVerifier();

        @BeforeEach(priority = 100)
        void beforeTestOne() {
            methods.called(this, beforeTestOne);
        }

        @BeforeEach(priority = 10)
        void beforeTestTwo() {
            methods.called(this, beforeTestTwo);
        }

        @BeforeEach(priority = 50)
        void beforeTestThree() {
            methods.called(this, beforeTestThree);
        }

        @AfterEach(priority = 100)
        void afterTestOne() {
            methods.called(this, afterTestOne);
        }

        @AfterEach(priority = 10)
        void afterTestTwo() {
            methods.called(this, afterTestTwo);
        }

        @AfterEach(priority = 50)
        void afterTestThree() {
            methods.called(this, afterTestThree);
        }

        void successfulTest() {
            methods.called(this, successfulTest);
        }
    }
}