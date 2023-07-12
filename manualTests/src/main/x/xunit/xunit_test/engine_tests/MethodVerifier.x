/**
 * A utility to log method calls and then perform various assertions on them.
 */
class MethodVerifier {
    /**
     * The methods logged by this `MethodLogger`.
     */
    private Array<CallInfo> methods = new Array();

    Object[] targets = new Array();

    void reset() {
        methods.clear();
        targets.clear();
    }

    /**
     * Log a method call.
     *
     * @param m  the `Method` that was called
     */
    <Target> void called(Target target, Method<Target, Tuple<>, Tuple<>> m) {
        try {
            assert;
        } catch (Exception e) {
            String[] stack    = e.toString().split('\n');
            String   location = stack.size >= 4 ? stack[3].toString() : "at unknown location";
            MethodCallInfo<Target> info = new MethodCallInfo(target, m, location);
            methods.add(info);
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
    }

    <Target> void called(Type<Target> type, Function fn) {
        try {
            assert;
        } catch (Exception e) {
            String[] stack    = e.toString().split('\n');
            String   location = stack.size >= 4 ? stack[3].toString() : "at unknown location";
            FunctionCallInfo<Target, <>, <>> info = new FunctionCallInfo(type, fn, location);
            methods.add(info);
        }
    }

    <Target> Target assertTarget(Type<Target> type) {
         Target[] targets = findTargets(type);
         assert:test targets.size == 1;
         return targets[0];
    }

    <Target> conditional Target[] verifyAllTargets(Type<Target> type) {
         Target[] targets = findTargets(type);
         if (targets.size > 0) {
            return True, targets;
         }
         return False;
    }

    private <Target> Target[] findTargets(Type<Target> type) {
         Target[] found = new Array();
         for (Object o : targets) {
            if (o.is(Target)) {
                found.add(o.as(Target));
            }
         }
         return found;
    }

    /**
     * Return an `InOrder` method verifier to assert methods were called in
     * a specific order.
     */
    InOrder inOrder() {
        return new InOrderImpl(this);
    }

    /**
     * Verify that a function was called.
     *
     * @param method  the method to verify
     * @param times   the number of times the method should have been called (defaults to one)
     */
//    <Target> void verify(Type<Target> type, Function fn, Int times = 1) {
//        FunctionCallInfo<Target, <>, <>> info = new FunctionCallInfo(target, method);
//        assertCalled(info, times);
//    }

    /**
     * Verify that a method was called.
     *
     * @param method  the method to verify
     * @param times   the number of times the method should have been called (defaults to one)
     */
    <Target> void verify(Target target, Method<Target, Tuple<>, Tuple<>> method, Int times = 1) {
        MethodCallInfo<Target> methodInfo = new MethodCallInfo(target, method);
        assertCalled(methodInfo, times);
    }

    /**
     * Verify zero methods were logged.
     */
    void verifyNoMethodsCalled() {
        if (methods.size > 0) {
            throw new Assertion($"Expected zero methods to be called but found {methods.size}");
        }
    }

    /**
     * Create a String that lists the call locations for the given `MethodCallInfo` instances.
     */
    private String createCalledLocations(MethodCallInfo[] called) {
        StringBuffer buf = new StringBuffer();
        for (MethodCallInfo info : called) {
            buf.append(info.location).append('\n');
        }
        return buf.append('\n');
    }

    /**
     * Create a String that lists the call locations for the given `MethodCallInfo` instances.
     */
    private String createCalledLocations(FunctionCallInfo[] called) {
        StringBuffer buf = new StringBuffer();
        for (FunctionCallInfo info : called) {
            buf.append(info.location).append('\n');
        }
        return buf.append('\n');
    }

    private <Target> (MethodCallInfo<Target>[], Int, Int) assertCalled(MethodCallInfo<Target> info, Int times) {
        (MethodCallInfo<Target>[] called, Int firstCall, Int lastCall) = findMethodCalls(info);
        if (called.size != times) {
            String msg = $"Verification failure\nMethod\n{info.method}\nWanted {pluralizeTimes(times)} but was called {pluralizeTimes(called.size)}\n{createCalledLocations(called)}";
            throw new Assertion(msg);
        }
        return called, firstCall, lastCall;
    }

    private String pluralizeTimes(Int count) {
        if (count == 1) {
            return "1 time";
        }
        return $"{count} times";
    }

    private <Target> (MethodCallInfo<Target>[], Int, Int) findMethodCalls(MethodCallInfo<Target> searchFor, Int index = 0, Int count = Int.MaxValue) {
        MethodCallInfo<Target>[] called = new Array();
        Int firstCall = -1;
        Int lastCall  = -1;
        if (index < methods.size) {
            for (Int i : index ..< methods.size) {
                CallInfo info = methods[i];
                if (info.is(MethodCallInfo)) {
                    if (info == searchFor) {
                        called.add(info.as(MethodCallInfo<Target>));
                        lastCall = i;
                        if (firstCall < 0) {
                            firstCall = i;
                        }
                        if (called.size == count) {
                            break;
                        }
                    }
                }
            }
        }
        return called, firstCall, lastCall;
    }

    private <Target> (FunctionCallInfo<Target, <>, <>>[], Int, Int) findFunctionCalls(FunctionCallInfo<Target, <>, <>> searchFor, Int index = 0, Int count = Int.MaxValue) {
        FunctionCallInfo<Target, <>, <>>[] called = new Array();
        Int firstCall = -1;
        Int lastCall  = -1;
        if (index < methods.size) {
            for (Int i : index ..< methods.size) {
                CallInfo info = methods[i];
                if (info.is(FunctionCallInfo)) {
                    if (info == searchFor) {
                        called.add(info.as(FunctionCallInfo<Target, <>, <>>));
                        lastCall = i;
                        if (firstCall < 0) {
                            firstCall = i;
                        }
                        if (called.size == count) {
                            break;
                        }
                    }
                }
            }
        }
        return called, firstCall, lastCall;
    }

    /**
     * A class that verifies methods were called in order.
     */
    static interface InOrder {
        <Target> InOrder verify(Target target, Method<Target, Tuple<>, Tuple<>> expected, Int times = 1);

        <Target> InOrder verify(Type<Target> type, Function expected, Int times = 1);
    }

    private static class InOrderImpl(MethodVerifier verifier)
            implements InOrder {

        Int index = 0;

        CallInfo? previous = Null;

        @Override
        <Target> InOrder verify(Target target, Method<Target, Tuple<>, Tuple<>> expected, Int times = 1) {
            MethodCallInfo<Target> expectedInfo = new MethodCallInfo(target, expected);
            (MethodCallInfo<Target>[] called, Int firstCall, Int lastCall) = verifier.findMethodCalls(expectedInfo, index);
            if (called.size != times) {
                String msg;
                if (previous == Null) {
                    msg = $"Verification failure\nMethod\n  {expectedInfo}\nWanted {verifier.pluralizeTimes(times)} but was called {verifier.pluralizeTimes(called.size)}\n{verifier.createCalledLocations(called)}";
                } else {
                    msg = $"Verification failure\nMethod:\n  {expectedInfo}\nWanted {verifier.pluralizeTimes(times)} AFTER method\n  {previous}\nBut was called {verifier.pluralizeTimes(called.size)}\n{verifier.createCalledLocations(called)}";
                }
                throw new Assertion($"{msg}");
            }
            if (MethodCallInfo last := called.last()) {
                previous = last;
            }
            index = lastCall;
            return this;
        }

        @Override
        <Target> InOrder verify(Type<Target> type, Function expected, Int times = 1) {
            FunctionCallInfo<Target, <>, <>> expectedInfo = new FunctionCallInfo(type, expected);
            (FunctionCallInfo<Target, <>, <>> [] called, Int firstCall, Int lastCall) = verifier.findFunctionCalls(expectedInfo, index);
            if (called.size != times) {
                String msg;
                if (previous == Null) {
                    msg = $"Verification failure\nFunction\n  {expectedInfo}\nWanted {verifier.pluralizeTimes(times)} but was called {verifier.pluralizeTimes(called.size)}\n{verifier.createCalledLocations(called)}";
                } else {
                    msg = $"Verification failure\nFunction:\n  {expectedInfo}\nWanted {verifier.pluralizeTimes(times)} AFTER method\n  {previous}\nBut was called {verifier.pluralizeTimes(called.size)}\n{verifier.createCalledLocations(called)}";
                }
                throw new Assertion($"{msg}");
            }
            if (FunctionCallInfo last := called.last()) {
                previous = last;
            }
            index = lastCall;
            return this;
        }

    }

    interface CallInfo {
    }

    /**
     * A holder for method call information.
     */
    static class MethodCallInfo<Target>(Target target, Method<Target, Tuple<>, Tuple<>> method, String location = "")
            implements CallInfo
            implements ecstasy.Comparable
            implements Stringable {

        @Override
        static <CompileType extends MethodCallInfo> Boolean equals(CompileType value1, CompileType value2) {
            return value1.target == value2.target
                && value1.Target == value2.Target
                && value1.method.toString() == value2.method.toString();
        }

        @Override
        Int estimateStringLength() {
            return Target.estimateStringLength() + method.toString().size;
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            method.appendTo(buf);
            " in type ".appendTo(buf);
            Target.appendTo(buf);
            return buf;
        }
    }

    /**
     * A holder for method call information.
     */
    static class FunctionCallInfo<Target, ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
    (Type<Target> type, Function<ParamTypes, ReturnTypes> fn, String location = "")
            implements CallInfo
            implements ecstasy.Comparable
            implements Stringable {

        @Override
        static <CompileType extends FunctionCallInfo> Boolean equals(CompileType value1, CompileType value2) {
            return value1.type == value2.type
                && value1.fn == value2.fn;
        }

        @Override
        Int estimateStringLength() {
            return type.estimateStringLength() + fn.toString().size;
        }

        @Override
        Appender<Char> appendTo(Appender<Char> buf) {
            fn.appendTo(buf);
            " in type ".appendTo(buf);
            type.appendTo(buf);
            return buf;
        }
    }
}