module Runner {

    import ecstasy.mgmt.*;

    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(String[] modules=[]) {
        Tuple<Future, ConsoleBuffer>[] results =
            new Array(modules.size, i -> loadAndRun(modules[i]));
        reportResults(results);
    }

    void reportResults(Tuple<Future, ConsoleBuffer>[] results) {
        Exception? failure = Null;

        for (Int index : 0 ..< results.size) {
            (Future future, ConsoleBuffer buffer) = results[index];

            future.waitForCompletion();
            console.print(buffer.backService.toString());

            try {
                // TODO: Use get() after waiting instead of peekException(): in practice the latter
                // hit an "Un-initialized property \"failure\"" edge case for completed module
                // futures, while get() reliably rethrows exceptional completion and stays silent
                // on success.
                future.get();
            } catch (Exception e) {
                console.print($"Exception during execution of module #{index + 1}: {e}");
                failure ?:= e;
            }
        }

        throw failure?;
    }

    Tuple<Future, ConsoleBuffer> loadAndRun(String moduleName) {
        @Inject("repository") ModuleRepository repository;

        try {
            ModuleTemplate   template = repository.getResolvedModule(moduleName);
            ConsoleBuffer    buffer   = new ConsoleBuffer();
            ResourceProvider injector = new RunnerResourceProvider(&buffer.maskAs(Console));

            Container container =
                new Container(template, Lightweight, repository, injector);

            buffer.print($"++++++ Loading module: {moduleName} +++++++\n");
            using (new Timeout(Duration:1m)) {
                @Future Tuple result = container.invoke("run", ());
                return (&result, buffer);
            }
        } catch (Exception e) {
            console.print($"Failed to run module {moduleName.quoted()}: {e.text}");
            throw e;
        }
    }

    const ConsoleBuffer
            implements Console {
        ConsoleBack backService = new ConsoleBack();

        @Override
        void print(Object object = "", Boolean suppressNewline = False) {
            backService.print(object.toString(), suppressNewline);
        }

        @Override
        String readLine(String prompt = "", Boolean suppressEcho = False) {
            throw new Unsupported();
        }
    }

    service ConsoleBack {
        private StringBuffer buffer = new StringBuffer();

        void print(Object object = "", Boolean suppressNewline = False) {
            buffer.addAll(object.toString());
            if (!suppressNewline) {
                buffer.add('\n');
            }
        }

        @Override
        String toString() {
            return buffer.toString();
        }
    }

    service RunnerResourceProvider(Console console)
            extends PassThroughResourceProvider {
        @Override
        Supplier getResource(Type type, String name) {
            return type == Console && name == "console"
                ? console
                : super(type, name);
        }
    }
}
