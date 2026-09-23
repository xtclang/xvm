package org.xvm.runtime.template._native.fs;

import java.io.IOException;

import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.OwnedResource;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

/**
 * Native OSStorage implementation.
 */
public class xOSStorage
        extends xService {
    public xOSStorage(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        s_methodOnEvent = getStructure().findMethodDeep("onEvent", Utils.ANY);

        markNativeProperty("homeDir");
        markNativeProperty("curDir");
        markNativeProperty("tmpDir");

        markNativeMethod("find", new String[] {"_native.fs.OSFileStore", "text.String"}, null);
        markNativeMethod("names", STRING, null);
        markNativeMethod("createDir", STRING, BOOLEAN);
        markNativeMethod("createFile", STRING, BOOLEAN);
        markNativeMethod("delete", STRING, BOOLEAN);
        markNativeMethod("watch", null, INT);
        markNativeMethod("unwatch", INT, VOID);
        markNativeMethod("lookupWatch", INT, null);
        markNativeMethod("instance", VOID, THIS);

        invalidateTypeInfo();
    }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn) {
        if ("fileStore".equals(idProp.getName())) {
            // optimize out the cross-service call
            return frame.assignValue(iReturn,
                ((ServiceHandle) hTarget).getField(frame, "fileStore"));
        }

        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;
        ObjectHandle  hStore   = hStorage.getField(frame, "fileStore");

        // the handles below are cached by the Container.initResources()
        switch (sPropName) {
        case "homeDir":
            return xOSDirectory.INSTANCE.createHandle(frame, hStore,
                Paths.get(System.getProperty("user.home")), iReturn);

        case "curDir":
            return xOSDirectory.INSTANCE.createHandle(frame, hStore,
                Paths.get(System.getProperty("user.dir")), iReturn);

        case "tmpDir":
            return xOSDirectory.INSTANCE.createHandle(frame, hStore,
                Paths.get(System.getProperty("java.io.tmpdir")), iReturn);
        }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context) {
            return xRTFunction.makeAsyncNativeHandle(method).
                call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
        }

        switch (method.getName()) {
        case "names": {
            StringHandle hPathString = (StringHandle) hArg;

            try {
                Path     path   = Paths.get(hPathString.getStringValue());
                String[] asName = path.toFile().list();
                int      cNames = asName == null ? 0 : asName.length;

                return cNames == 0
                         ? frame.assignValue(iReturn, xString.ensureEmptyArray())
                         : frame.assignValue(iReturn, xString.makeArrayHandle(asName));
            } catch (InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

        case "createFile": { // (pathString)
            StringHandle hPathString = (StringHandle) hArg;

            try {
                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && !Files.isDirectory(path)) {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                }

                Path parent = path.getParent();
                if (!Files.exists(parent)) {
                    return frame.raiseException(xException.ioException(frame,
                            "Cannot create file, parent directory does not exist: " + path));
                }
                if (!Files.isDirectory(parent)) {
                    return frame.raiseException(xException.ioException(frame,
                            "Cannot create file, parent is not a directory: " + path));
                }
                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(path.toFile().createNewFile()));
            } catch (IOException|InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

        case "createDir": { // (pathString)
            StringHandle hPathString = (StringHandle) hArg;

            try {
                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && Files.isDirectory(path)) {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                }

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(path.toFile().mkdirs()));
            } catch (InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }

        case "delete": { // (pathString)
            StringHandle hPathString = (StringHandle) hArg;

            Path path = Paths.get(hPathString.getStringValue());
            if (!Files.exists(path)) {
                return frame.assignValue(iReturn, xBoolean.FALSE);
            }

            return frame.assignValue(iReturn,
                xBoolean.makeHandle(path.toFile().delete()));
        }

        case "unwatch": {
            if (watchDaemon != null) {
                watchDaemon.unwatch(((JavaLong) hArg).getValue());
            }
            return Op.R_NEXT;
        }
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (hStorage != null && frame.f_context != hStorage.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
        }

        switch (method.getName()) {
        case "watch": {
            try {
                Path path = Paths.get(((StringHandle) ahArg[0]).getStringValue());
                WatchServiceDaemon daemon = ensureWatchDaemon();
                var resource = frame.acquireResource(() -> daemon.register(path, hStorage, ahArg[1]));
                WatchRegistration registration = resource.get();
                registration.attach(resource);
                if (iReturn == Op.A_IGNORE) {
                    resource.closeAsync();
                    return Op.R_NEXT;
                }
                return frame.assignValue(iReturn, xInt64.makeHandle(registration.id));
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }
        case "instance":
            return frame.assignValue(iReturn,
                    ((NativeContainer) f_container).ensureOSStorage(frame, null));
        }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).callN(frame, hTarget, ahArg, aiReturn);
        }

        switch (method.getName()) {
        case "lookupWatch": {
            ObjectHandle watcher = watchDaemon == null ? null
                    : watchDaemon.watcher(((JavaLong) ahArg[0]).getValue());
            return watcher == null
                    ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                    : frame.assignValues(aiReturn, xBoolean.TRUE, watcher);
        }
        case "find": { // (store, pathString)
            ObjectHandle hStore      = ahArg[0];
            StringHandle hPathString = (StringHandle) ahArg[1];

            try {
                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path)) {
                    return Utils.assignConditionalResult(frame,
                        xOSFileNode.createHandle(frame, hStore, path, Files.isDirectory(path), Op.A_STACK),
                        aiReturn);
                }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            } catch (InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }
        }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    // ----- helper methods ------------------------------------------------------------------------

    protected synchronized WatchServiceDaemon ensureWatchDaemon() throws IOException {
        if (watchDaemon == null) {
            var daemon = new WatchServiceDaemon(pool());
            try {
                f_container.onTermination(daemon::closeAsync);
                daemon.start();
                watchDaemon = daemon;
            } catch (RuntimeException | Error e) {
                daemon.closeAsync();
                throw e;
            }
        }
        return watchDaemon;
    }

    protected static class WatchServiceDaemon
            extends Thread {
        public WatchServiceDaemon(ConstantPool pool)
                throws IOException {
            super("WatchServiceDaemon");

            setDaemon(true);

            f_pool       = pool;
            f_service    = FileSystems.getDefault().newWatchService();
            f_mapWatches = new ConcurrentHashMap<>();
        }

        synchronized WatchRegistration register(Path pathDir, ServiceHandle storage, ObjectHandle watcher)
                throws IOException {
            Path path = pathDir.toAbsolutePath().normalize();
            WatchKey key = path.register(f_service, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            WatchContext context = f_mapWatches.computeIfAbsent(key, _ -> new WatchContext(path));
            var registration = new WatchRegistration(this, ++nextId, key, storage, watcher);
            context.subscriptions.put(registration.id, registration);
            subscriptions.put(registration.id, registration);
            return registration;
        }

        void unwatch(long id) {
            WatchRegistration registration;
            synchronized (this) {
                registration = subscriptions.get(id);
            }
            if (registration != null) {
                registration.cancel();
            }
        }

        ObjectHandle watcher(long id) {
            WatchRegistration registration;
            synchronized (this) {
                registration = subscriptions.get(id);
            }
            if (registration == null) {
                return null;
            }
            synchronized (registration) {
                return registration.watcher;
            }
        }

        synchronized void remove(WatchRegistration registration) {
            subscriptions.remove(registration.id);
            WatchContext context = f_mapWatches.get(registration.key);
            if (context != null) {
                context.subscriptions.remove(registration.id);
                if (context.subscriptions.isEmpty()) {
                    f_mapWatches.remove(registration.key);
                    registration.key.cancel();
                }
            }
        }

        @Override
        public void run() {
            try (var ignore = ConstantPool.withPool(f_pool); f_service) {
                while (true) {
                    processKey(f_service.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ClosedWatchServiceException e) {
                // Normal container shutdown unblocks take() by closing the service.
            } catch (IOException | RuntimeException | Error e) {
                completion.completeExceptionally(e);
            } finally {
                List<WatchRegistration> remaining;
                synchronized (this) {
                    remaining = List.copyOf(subscriptions.values());
                }
                remaining.forEach(WatchRegistration::cancel);
                completion.complete(null);
            }
        }

        CompletableFuture<Void> closeAsync() {
            try {
                f_service.close();
            } catch (IOException e) {
                completion.completeExceptionally(e);
                interrupt();
            }
            return completion;
        }

        protected void processKey(WatchKey key) {
            WatchContext context;
            List<WatchRegistration> listeners;
            synchronized (this) {
                context = f_mapWatches.get(key);
                listeners = context == null ? List.of() : List.copyOf(context.subscriptions.values());
            }
            if (context == null) {
                key.cancel();
                return;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                int kind = getKindId(event.kind());
                if (kind >= 0) {
                    Path node = context.pathDir.resolve((Path) event.context());
                    listeners.forEach(listener -> listener.onEvent(context.pathDir, node, kind));
                }
            }
            if (!key.reset()) {
                listeners.forEach(WatchRegistration::cancel);
            }
        }

        /**
         * @return 0 - for CREATE, 1 - for MODIFY, 2 - for DELETE, -1 for OVERFLOW;
         *        -2 for anything else
         */
        private int getKindId(WatchEvent.Kind kind) {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                return 0;
            }
            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                return 1;
            }
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                return 2;
            }
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                return -1;
            }
            return -2;
        }

        // ----- WatchContext class --------------------------------------------------------------

        private record WatchContext(Path pathDir, Map<Long, WatchRegistration> subscriptions) {
            WatchContext(Path pathDir) {
                this(pathDir, new HashMap<>());
            }
        }

        private final Map<Long, WatchRegistration> subscriptions = new HashMap<>();
        private long nextId;

        private final ConstantPool                f_pool;
        private final Map<WatchKey, WatchContext> f_mapWatches;
        private final WatchService                f_service;
        private final CompletableFuture<Void>     completion = new CompletableFuture<>();
    }

    /**
     * One subscriber, independently owned even when its directory key is shared.
     */
    protected static final class WatchRegistration implements AutoCloseable {
        private WatchRegistration(WatchServiceDaemon daemon, long id, WatchKey key,
                                  ServiceHandle storage, ObjectHandle watcher) {
            this.daemon = daemon;
            this.id = id;
            this.key = key;
            this.storage = storage;
            this.watcher = watcher;
        }

        void attach(OwnedResource<WatchRegistration> resource) {
            boolean wasClosed;
            synchronized (this) {
                wasClosed = closed;
                if (!wasClosed) {
                    this.resource = resource;
                }
            }
            if (wasClosed) {
                resource.closeAsync();
            }
        }

        void cancel() {
            OwnedResource<WatchRegistration> owned;
            synchronized (this) {
                owned = resource;
            }
            if (owned == null) {
                close();
            } else {
                owned.closeAsync();
            }
        }

        synchronized void onEvent(Path directory, Path node, int kind) {
            if (!closed) {
                FunctionHandle callback = xRTFunction.makeInternalHandle(null, s_methodOnEvent)
                        .bindTarget(null, storage);
                storage.f_context.callLater(callback, new ObjectHandle[] {
                    xString.makeHandle(directory.toString()), xString.makeHandle(node.toString()),
                    xBoolean.TRUE, xInt64.makeHandle(kind), xInt64.makeHandle(id)
                });
            }
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                storage = null;
                watcher = null;
                resource = null;
            }
            daemon.remove(this);
        }

        private final WatchServiceDaemon daemon;
        private final long id;
        private final WatchKey key;
        private ServiceHandle storage;
        private ObjectHandle watcher;
        private OwnedResource<WatchRegistration> resource;
        private boolean closed;
    }

    // ----- constants -----------------------------------------------------------------------------

    private static MethodStructure s_methodOnEvent;

    private WatchServiceDaemon watchDaemon;
}
