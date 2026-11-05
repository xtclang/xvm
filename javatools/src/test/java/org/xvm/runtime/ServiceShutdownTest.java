package org.xvm.runtime;

import java.io.File;

import java.net.URL;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.xvm.api.Connector;
import org.xvm.api.EmbeddingSupport;

import org.xvm.asm.DirRepository;

import org.xvm.runtime.ServiceContext.CallLaterRequest;

import org.xvm.testing.Await;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A service's shutdown, which its container's kill waits for, completes however it goes: when it
 * is requested after the service's worker last looked for one, when the service has ended already,
 * and when a step of it fails, which the runtime then reports as its own failure. The test holds a
 * service's scheduling lock where a worker would, so the order of the steps is the test's, not the
 * machine's.
 */
public class ServiceShutdownTest {
    @Test
    public void aShutdownRequestedAfterTheWorkerLastLookedIsPerformed() throws Exception {
        // a service with no handle yet, as one being made when its container is killed
        ServiceContext service = container.createServiceContext("Requested");
        // its worker holds the scheduling lock and has drained it, before the request comes
        assertTrue(service.tryAcquireSchedulingLock());
        CompletableFuture<Void> shutdown = service.requestShutdown();
        assertFalse(shutdown.isDone(), "the request waits for the worker that holds the service");

        service.drained();
        Await.get(shutdown, "the shutdown of a service whose worker had drained it");
        assertFalse(container.getServices().contains(service));
    }

    @Test
    public void aShutdownRequestedOfAServiceThatHasEndedCompletesAtOnce() {
        ServiceContext service = container.createServiceContext("Ended");
        assertTrue(service.tryAcquireSchedulingLock());
        // a service that ran and then shut itself down has no handle; its worker finds it ended
        service.m_iFrameCounter = 1;
        service.drained();
        assertFalse(container.getServices().contains(service));

        assertTrue(service.requestShutdown().isDone(), "nothing is left to shut down");
    }

    @Test
    public void aShutdownThatFailsCompletesAndIsReportedAsTheRuntimesFailure() throws Exception {
        assertTrue(ServiceContext.class.desiredAssertionStatus(),
                "the failure here is the shutdown's check that it ended every fiber");
        BlockingQueue<String> reports = new LinkedBlockingQueue<>();
        Consumer<String>      prior   = Runtime.setFailureReporter(reports::add);
        try {
            ServiceContext service = container.createServiceContext("Failing");
            // a fiber that no queue of the service holds, so its shutdown cannot end it
            service.createFiber(new CallLaterRequest(null, null, new ObjectHandle[0], 0));

            Await.get(service.requestShutdown(), "the shutdown of a service whose shutdown fails");
            String report = Await.poll(reports, "the report of the failed shutdown");
            assertTrue(report.startsWith("Unexpected service execution failure: Failing"), report);
            assertTrue(report.contains("AssertionError"), report);
        } finally {
            Runtime.setFailureReporter(prior);
        }
    }

    @BeforeAll
    static void startRuntime() throws Exception {
        URL modules = ServiceShutdownTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        // an interpreter of the test's own, as the API's tests make: its constructor is the API package's
        var make = EmbeddingSupport.class.getDeclaredConstructor();
        make.setAccessible(true);
        connector = make.newInstance()
                .configure(new DirRepository(new File(modules.toURI()), true), null)
                .ensureConnector();
        var field = connector.getClass().getDeclaredField("f_containerNative");
        field.setAccessible(true);
        container = (NativeContainer) field.get(connector);
    }

    @AfterAll
    static void stopRuntime() {
        connector.shutdown();
    }

    private static Connector       connector;
    private static NativeContainer container;
}
