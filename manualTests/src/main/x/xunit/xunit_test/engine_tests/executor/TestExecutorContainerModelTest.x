import xunit.ExtensionProvider;

import xunit.annotations.AfterAll;
import xunit.annotations.AfterEach;
import xunit.annotations.BeforeAll;
import xunit.annotations.BeforeEach;
import xunit.annotations.Disabled;
import xunit.annotations.ExtensionMixin;

import xunit.extensions.AfterEachCallback;
import xunit.extensions.BeforeEachCallback;

import xunit_engine.ExecutionConfiguration;
import xunit_engine.Model;
import xunit_engine.UniqueId;

import xunit_engine.executor.EngineExecutionContext;
import xunit_engine.executor.TestExecutor;

import xunit_engine.models.ContainerModel;
import xunit_engine.models.MethodModel;


/**
 * Tests to verify operation of the `TestExecutor` class when executing
 * tests in a `ContainerModel`.
 */
class TestExecutorContainerModelTest {

    @BeforeEach
    void setup() {
        TestStub.methods.reset();
        OrderedTestStub.methods.reset();
    }

    @Test
    void shouldExecuteSimpleTestClass() {
        MethodModel                methodModel    = createMethodModel(TestStub.successfulTest);
        ContainerModel             containerModel = createContainerModel(TestStub, [methodModel]);
        AssertingExecutionListener listener       = new AssertingExecutionListener();
        TestExecutor               executor       = new TestExecutor(containerModel, ExecutionConfiguration.create());
        EngineExecutionContext     ctx            = EngineExecutionContext.builder(containerModel)
                                                                          .withListener(listener)
                                                                          .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx);
        listener.forTest(methodModel.uniqueId).assertSuccessfulTest();

        TestStub stub = TestStub.methods.assertTarget(TestStub);

        TestStub.methods.inOrder()
            .verify(stub, TestStub.successfulTest);
    }

    @Test
    void shouldExecuteSimpleTestClassWithBeforeAll() {
        ExtensionProvider[]        providers      = xunit_test.getProviders(TestStub, TestStub.beforeAllTestsOne);
        MethodModel                methodModel    = createMethodModel(TestStub.successfulTest);
        ContainerModel             containerModel = createContainerModel(TestStub, [methodModel], providers);
        AssertingExecutionListener listener       = new AssertingExecutionListener();
        TestExecutor               executor       = new TestExecutor(containerModel, ExecutionConfiguration.create());
        EngineExecutionContext     ctx            = EngineExecutionContext.builder(containerModel)
                                                                          .withListener(listener)
                                                                          .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx);
        listener.forTest(methodModel.uniqueId).assertSuccessfulTest();

        TestStub stub = TestStub.methods.assertTarget(TestStub);

        TestStub.methods.inOrder()
            .verify(TestStub, TestStub.beforeAllTestsOne)
            .verify(stub, TestStub.successfulTest);
    }

    @Test
    void shouldExecuteSimpleTestClassWithAfterAll() {
        ExtensionProvider[]        providers      = xunit_test.getProviders(TestStub, TestStub.afterAllTestsOne);
        MethodModel                methodModel    = createMethodModel(TestStub.successfulTest);
        ContainerModel             containerModel = createContainerModel(TestStub, [methodModel], providers);
        AssertingExecutionListener listener       = new AssertingExecutionListener();
        TestExecutor               executor       = new TestExecutor(containerModel, ExecutionConfiguration.create());
        EngineExecutionContext     ctx            = EngineExecutionContext.builder(containerModel)
                                                                          .withListener(listener)
                                                                          .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx);
        listener.forTest(methodModel.uniqueId).assertSuccessfulTest();

        TestStub stub = TestStub.methods.assertTarget(TestStub);

        TestStub.methods.inOrder()
            .verify(stub, TestStub.successfulTest)
            .verify(TestStub, TestStub.afterAllTestsOne);
    }

    @Test
    void shouldExecuteSimpleTestClassWithBeforeAndAfterAll() {
        ExtensionProvider[]        providers      = xunit_test.getProviders(TestStub, [TestStub.beforeAllTestsOne,
                                                            TestStub.afterAllTestsOne]);
        MethodModel                methodModel    = createMethodModel(TestStub.successfulTest);
        ContainerModel             containerModel = createContainerModel(TestStub, [methodModel], providers);
        AssertingExecutionListener listener       = new AssertingExecutionListener();
        TestExecutor               executor       = new TestExecutor(containerModel, ExecutionConfiguration.create());
        EngineExecutionContext     ctx            = EngineExecutionContext.builder(containerModel)
                                                                          .withListener(listener)
                                                                          .build();

        EngineExecutionContext ctxFinal = executor.execute(ctx);
        listener.forTest(methodModel.uniqueId).assertSuccessfulTest();

        TestStub stub = TestStub.methods.assertTarget(TestStub);

        TestStub.methods.inOrder()
            .verify(TestStub, TestStub.beforeAllTestsOne)
            .verify(stub, TestStub.successfulTest)
            .verify(TestStub, TestStub.afterAllTestsOne);
    }

    /**
     * A stub to represent a test class.
     */
    @MockTest
    static class TestStub {
        static MethodVerifier methods = new MethodVerifier();

        @BeforeAll
        static void beforeAllTestsOne() {
            methods.called(TestStub, beforeAllTestsOne);
        }

        @BeforeAll
        static void beforeAllTestsTwo() {
            methods.called(TestStub, beforeAllTestsTwo);
        }

        @AfterAll
        static void afterAllTestsOne() {
            methods.called(TestStub, afterAllTestsOne);
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
        static MethodVerifier methods = new MethodVerifier();

        @BeforeAll
        static void beforeAllTestsOne() {
            methods.called(TestStub, beforeAllTestsOne);
        }

        @AfterAll
        static void afterAllTestsOne() {
            methods.called(OrderedTestStub, afterAllTestsOne);
        }

        @BeforeEach(priority = 100)
        void beforeTestOne() {
            methods.called(OrderedTestStub, beforeTestOne);
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
