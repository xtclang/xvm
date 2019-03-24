module TestFiles.xqiz.it
    {
    import X.fs.Path;

    @Inject Console console;

    void run()
        {
        console.println("*** file tests ***\n");

        testPaths();
        }

    void testPaths()
        {
        Path path = new Path(null, "test");
        console.println("path=" + path);

        path = new Path(path, "sub");
        console.println("path=" + path);

        path = new Path(path, "more");
        console.println("path=" + path);

        for (Int i : 0..2)
            {
            // TODO GG
            // 2019-03-22 14:15:07.749 Service "TestFiles.xqiz.it" (id=0), fiber 0: Unhandled exception at Frame: testPaths(); line=28
            // java.lang.ClassCastException: org.xvm.runtime.template.xConst cannot be cast to org.xvm.runtime.template.IndexSupport
            // 	at org.xvm.asm.op.I_Get.complete(I_Get.java:57)
            // 	at org.xvm.asm.OpIndex.processArgs(OpIndex.java:140)
            // 	at org.xvm.asm.OpIndex.process(OpIndex.java:125)
            // 	at org.xvm.runtime.ServiceContext.execute(ServiceContext.java:245)
            // 	at org.xvm.runtime.DaemonPool.run(DaemonPool.java:123)
            // 	at java.lang.Thread.run(Thread.java:748)
            //
            // console.println("path[" + i + "]=" + path[i]);

            console.println("path[" + i + "]=" + path.getElement(i));
            }

        // TODO GG
        // 2019-03-22 14:14:12.73 Service "TestFiles.xqiz.it" (id=0), fiber 0: Unhandled exception at Frame: Range.iterator().RangeIterator:2.next(); line=86
        // java.lang.IllegalStateException: unexpected defining constant: Property{property=ElementType}
        // 	at org.xvm.asm.constants.TerminalTypeConstant.getOpSupport(TerminalTypeConstant.java:1507)
        // 	at org.xvm.runtime.TemplateRegistry.resolveClass(TemplateRegistry.java:277)
        // 	at org.xvm.runtime.Frame.ensureClass(Frame.java:1094)
        // 	at org.xvm.asm.constants.TypeConstant.callEquals(TypeConstant.java:4800)
        // 	at org.xvm.asm.op.JumpNotEq.completeBinaryOp(JumpNotEq.java:66)
        // 	at org.xvm.asm.OpCondJump.processBinaryOp(OpCondJump.java:168)
        // 	at org.xvm.asm.OpCondJump.process(OpCondJump.java:118)
        // 	at org.xvm.runtime.ServiceContext.execute(ServiceContext.java:245)
        //
        // console.println("path[1..2]=" + path[1..2]);
        // console.println("path[0..1]=" + path[0..1]);
        // console.println("path[2..0]=" + path[2..0]);

        // TODO GG
        // Suspicious assignment from: Ecstasy:fs.Path to: Ecstasy:String
        path = ROOT + path;
        console.println("path=" + path);
        }
    }