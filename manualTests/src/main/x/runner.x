module Runner {

    import ecstasy.mgmt.*;

    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(String[] modules=[]) {
        Tuple<FutureVar, ConsoleBuffer>?[] results =
            new Array(modules.size, i -> loadAndRun(modules[i]));
        reportResults(results, 0);
    }

    void reportResults(Tuple<FutureVar, ConsoleBuffer>?[] results, Int index) {
        while (index < results.size) {
            Tuple<FutureVar, ConsoleBuffer>? resultTuple = results[index++];
            if (resultTuple != Null) {
                resultTuple[0].whenComplete((_, e) -> {
                    try {
                        if (e == Null) {
                            console.print(resultTuple[1].backService.toString());
                        } else {
                            console.print(e);
                        }
                        reportResults(results, index);
                    } catch (Exception e2) {
                        console.print($"Failure to report results for module #{index}: {e2}");
                    }
                });
                return;
            }
        }
    }

    Tuple<FutureVar, ConsoleBuffer>? loadAndRun(String moduleName) {
        @Inject("repository") ModuleRepository repository;

        try {
            ModuleTemplate   template = repository.getResolvedModule(moduleName);
            ConsoleBuffer    buffer   = new ConsoleBuffer();
            ResourceProvider injector = new RunnerResourceProvider(&buffer.maskAs(Console));

            Container container =
                new Container(template, Lightweight, repository, injector);

            buffer.print($"++++++ Loading module: {moduleName} +++++++\n");

            @Future Tuple result = container.invoke("run", Tuple:());
            return (&result, buffer);
        } catch (Exception e) {
            console.print($"Failed to run module {moduleName.quoted()}: {e.text}");
            return Null;
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