module Runner {

    import ecstasy.mgmt.*;

    import ecstasy.reflect.ModuleTemplate;

    @Inject Console console;

    void run(String[] modules=[]) {
        Int batchSize = getBatchSize();
        console.print($"Running {modules.size} modules in batches of {batchSize}");
        
        for (Int start = 0; start < modules.size; start += batchSize) {
            Int end = (start + batchSize).minOf(modules.size);
            String[] batch = modules[start..<end];
            
            console.print($"Starting batch {start/batchSize + 1}: modules {start+1} to {end}");
            
            // Run batch in parallel
            Tuple<FutureVar, ConsoleBuffer>?[] batchResults = 
                new Array(batch.size, i -> loadAndRun(batch[i]));
            
            // Wait for entire batch to complete before next batch
            waitForBatchCompletion(batchResults);
        }
    }

    void reportResults(Tuple<FutureVar, ConsoleBuffer>?[] results, Int index) {
        try {
            while (index < results.size) {
                Tuple<FutureVar, ConsoleBuffer>? resultTuple = results[index++];
                if (resultTuple != Null) {
                    resultTuple[0].whenComplete((_, e) -> {
                        console.print(resultTuple[1].backService.toString());
                        if (e != Null) {
                            console.print($"Exception during execution of module #{index}: {e}");
                        }
                        reportResults(results, index);
                    });
                return;
                }
            }
        } catch (Exception e) {
            console.print($"Failure to report results for module #{index}: {e}");
        }
    }

    Int getBatchSize() {
        // Use conservative batch size that works well on both platforms
        // TODO: Platform detection not working with @Inject("os.name")
        return 4;
    }

    void waitForBatchCompletion(Tuple<FutureVar, ConsoleBuffer>?[] batchResults) {
        // Process all results in the batch as they complete
        reportResults(batchResults, 0);
        
        // Wait for all futures in this batch to complete
        for (Tuple<FutureVar, ConsoleBuffer>? result : batchResults) {
            if (result != Null) {
                try {
                    result[0].get(); // Block until this future completes
                } catch (Exception e) {
                    console.print($"Batch execution error: {e}");
                }
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
            using (new Timeout(Duration:1m)) {
                @Future Tuple result = container.invoke("run", ());
                return (&result, buffer);
            }
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