module Runner {

    import ecstasy.mgmt.*;

    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(String[] modules=[]) {
        Tuple<String, Future, ConsoleBuffer>[] results =
            new Array(modules.size, i -> loadAndRun(modules[i]));
        @Future Tuple done;
        reportResults(results, 0, &done);
        return done;
    }

    void reportResults(Tuple<String, Future, ConsoleBuffer>[] results, Int index, Future<Tuple> done) {
        if (index >= results.size) {
            done.complete(());
            return;
        }

        (String moduleName, Future future, ConsoleBuffer buffer) = results[index];
        future.whenComplete((_, e) -> {
            console.print(buffer.backService.toString());

            if (e != Null) {
                console.print($"Exception during execution of module {moduleName.quoted()} (#{index + 1}): {e}");

                String[] abandoned = new String[];
                for (Int pending : index + 1 ..< results.size) {
                    (String pendingModule, Future ignoredFuture, ConsoleBuffer ignoredBuffer) = results[pending];
                    abandoned.add(pendingModule);
                }
                if (!abandoned.empty) {
                    console.print($"Abandoning remaining modules after failure in {moduleName.quoted()}: {abandoned}");
                }

                done.completeExceptionally(e);
                return;
            }

            reportResults(results, index + 1, done);
        });
    }

    Tuple<String, Future, ConsoleBuffer> loadAndRun(String moduleName) {
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
                return (moduleName, &result, buffer);
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
