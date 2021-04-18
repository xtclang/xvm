module TestSimple.test.org
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    package agg import aggregate.xtclang.org;

    import ecstasy.collections.Aggregator;
    import ecstasy.collections.ParallelAggregator;

    import agg.Sum;

    service Partition(Int id)
        {
        construct(Int id)
            {
            this.id = id;

            Random rnd = new ecstasy.numbers.PseudoRandom(id.toUInt64()+1);
            data = new Int[10](_ -> rnd.int(0..100));
            }

        public/private Int id;

        public/private Int[] data;

        Int exec(ParallelAggregator<Int, Int, Int> parallel)
            {
            return data.reduce(parallel.elementAggregator);
            }
        }

    void run()
        {
        Partition[] partitions = new Partition[10](i -> new Partition(i));

        Sum<Int> sum = new Sum();
        val finalAccumulator = sum.finalAggregator.init();
        Int remain = partitions.size;
        Loop: for (Partition partition : partitions)
            {
            @Future Int pendingPartial = partition.exec(sum);

// TODO GG
//2021-04-17 23:25:25.344 Service "TestSimple.test.org" (id=0) contended @at <TestSimple.test.org>, fiber 35: Unhandled exception: IllegalState: Un-initialized property "Property{property=notify}"
//	at annotations.FutureVar.chain(Function<Tuple<FutureVar.Completion, Nullable | FutureVar.Referent, Nullable | Exception>, Tuple<>>) (line=452, op=JumpNotNull)
//	at annotations.FutureVar.chain(Type<Object>, this:class(FutureVar).DependentFuture<chain(?)#NewType>) (line=439, op=Invoke_10)
//	at annotations.FutureVar.handle(Function<Tuple<Exception>, Tuple<FutureVar.Referent>>) (line=222, op=Invoke_N1)
//	at run() (line=43, op=Invoke_11)
//	at <TestSimple.test.org> (iPC=0, op=)
//            &pendingPartial.handle(e ->
//                {
//                log.add($"exception during partition {partition.id} processing: {e}");
//                return 0;
//                })
//            .passTo(partial ->
//                {
//                finalAccumulator.add(partial);
//                if (--remain <= 0)
//                    {
//                    log.add($"final result=${sum.finalAggregator.reduce(finalAccumulator)}");
//                    }
//                });
            &pendingPartial.whenComplete((partial, e) ->
                {
                if (e == null)
                    {
                    assert partial != null;
                    }
                else
                    {
                    log.add($"exception during partition {partition.id} processing: {e}");
                    partial = 0;
                    }

                finalAccumulator.add(partial);
                --remain;
                log.add($"remaining partitions: {remain}");
                if (remain <= 0)
                    {
                    log.add($"final result={sum.finalAggregator.reduce(finalAccumulator)}");
                    }
                });
            }
        }
    }