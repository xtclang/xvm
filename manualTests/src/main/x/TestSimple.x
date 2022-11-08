module TestSimple
    {
    @Inject Console console;
    void run()
        {
        // TODO GG "Index value 1 out-of-range; must be between 1 and {2} (inclusive). ("Int n1, Int n2")"
//        val tests = Array<Tuple<Int,Int>>:[(0,7), (1,5)];
//        for ((Int n1, Int n2) : tests)
//            {
//            }

        // TODO GG
//        Failed to generate code for Compiler (Module=TestSimple, Stage=Emitting)
//        java.lang.IllegalArgumentException: type already has parameters specified
//            at org.xvm.asm.ConstantPool.ensureParameterizedTypeConstant(ConstantPool.java:1572)
//            at org.xvm.compiler.ast.ListExpression.getImplicitType(ListExpression.java:92)
//            at org.xvm.compiler.ast.Expression.testFit(Expression.java:248)
//            at org.xvm.compiler.ast.ListExpression.testFit(ListExpression.java:184)
//            at org.xvm.compiler.ast.ForEachStatement.validateImpl(ForEachStatement.java:430)
//            at org.xvm.compiler.ast.Statement.validate(Statement.java:138)
//            at org.xvm.compiler.ast.StatementBlock.validateImpl(StatementBlock.java:419)
//            ...
        // for ((Int n1, Int n2) : Array<Tuple<Int,Int>>:[(0,7), (1,5)])
        for (Tuple<Int, Int>  t : Array<Tuple<Int,Int>>:[(0,7), (1,5)]) // this used to blow up the compiler
            {

            }
        }
    }