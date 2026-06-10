
package exceptionTests {

    import ecstasy.TypeMismatch;
    import ecstasy.NotAssigned;
    import ecstasy.Closed;

    @Inject Console console;

    void run() {
        console.print(">>>> Running Exception Tests >>>>");

        testException();
        testExceptionWithMessage();
        testExceptionWithCause();

        testDeadlock();
        testDeadlockWithMessage();
        testDeadlockWithCause();

        testOutOfMemory();
        testOutOfMemoryWithMessage();
        testOutOfMemoryWithCause();

        testStackOverflow();
        testStackOverflowWithMessage();
        testStackOverflowWithCause();

        testReadOnly();
        testReadOnlyWithMessage();
        testReadOnlyWithCause();

        testTypeMismatch();
        testTypeMismatchWithMessage();
        testTypeMismatchWithCause();

        testOutOfBounds();
        testOutOfBoundsWithMessage();
        testOutOfBoundsWithCause();

        testConcurrentModification();
        testConcurrentModificationWithMessage();
        testConcurrentModificationWithCause();

        testNotAssigned();
        testNotAssignedWithMessage();
        testNotAssignedWithCause();

        testIllegalArgument();
        testIllegalArgumentWithMessage();
        testIllegalArgumentWithCause();

        testIllegalState();
        testIllegalStateWithMessage();
        testIllegalStateWithCause();

        testAssertion();
        testAssertionWithMessage();
        testAssertionWithCause();

        testUnsupported();
        testUnsupportedWithMessage();
        testUnsupportedWithCause();

        testNotImplemented();
        testNotImplementedWithMessage();
        testNotImplementedWithCause();

        testClosed();
        testClosedWithMessage();
        testClosedWithCause();

        testNotShareable();
        testNotShareableWithMessage();
        testNotShareableWithCause();

        testTimedOut();
        testTimedOutWithMessage();
        testTimedOutWithCause();

        console.print("<<<< Running Exception Tests <<<<");
    }

    void testException() {
        try {
            throwException();
            assert as "expected exception to be thrown";
        } catch (Exception e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testExceptionWithMessage() {
        try {
            throwException("foo");
            assert as "expected exception to be thrown";
        } catch (Exception e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testExceptionWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwException(cause=cause);
            assert as "expected exception to be thrown";
        } catch (Exception e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwException(String? text = Null, Exception? cause = Null) {
        throw new Exception(text, cause);
    }

    void testDeadlock() {
        try {
            throwDeadlock();
            assert as "expected Deadlock to be thrown";
        } catch (Deadlock e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testDeadlockWithMessage() {
        try {
            throwDeadlock("foo");
            assert as "expected Deadlock to be thrown";
        } catch (Deadlock e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testDeadlockWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwDeadlock(cause=cause);
            assert as "expected Deadlock to be thrown";
        } catch (Deadlock e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwDeadlock(String? text = Null, Exception? cause = Null) {
        throw new Deadlock(text, cause);
    }

    void testOutOfMemory() {
        try {
            throwOutOfMemory();
            assert as "expected OutOfMemory to be thrown";
        } catch (OutOfMemory e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testOutOfMemoryWithMessage() {
        try {
            throwOutOfMemory("foo");
            assert as "expected OutOfMemory to be thrown";
        } catch (OutOfMemory e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testOutOfMemoryWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwOutOfMemory(cause=cause);
            assert as "expected OutOfMemory to be thrown";
        } catch (OutOfMemory e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwOutOfMemory(String? text = Null, Exception? cause = Null) {
        throw new OutOfMemory(text, cause);
    }

    void testStackOverflow() {
        try {
            throwStackOverflow();
            assert as "expected StackOverflow to be thrown";
        } catch (StackOverflow e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testStackOverflowWithMessage() {
        try {
            throwStackOverflow("foo");
            assert as "expected StackOverflow to be thrown";
        } catch (StackOverflow e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testStackOverflowWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwStackOverflow(cause=cause);
            assert as "expected StackOverflow to be thrown";
        } catch (StackOverflow e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwStackOverflow(String? text = Null, Exception? cause = Null) {
        throw new StackOverflow(text, cause);
    }

    void testReadOnly() {
        try {
            throwReadOnly();
            assert as "expected ReadOnly to be thrown";
        } catch (ReadOnly e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testReadOnlyWithMessage() {
        try {
            throwReadOnly("foo");
            assert as "expected ReadOnly to be thrown";
        } catch (ReadOnly e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testReadOnlyWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwReadOnly(cause=cause);
            assert as "expected ReadOnly to be thrown";
        } catch (ReadOnly e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwReadOnly(String? text = Null, Exception? cause = Null) {
        throw new ReadOnly(text, cause);
    }

    void testTypeMismatch() {
        try {
            throwTypeMismatch();
            assert as "expected TypeMismatch to be thrown";
        } catch (TypeMismatch e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testTypeMismatchWithMessage() {
        try {
            throwTypeMismatch("foo");
            assert as "expected TypeMismatch to be thrown";
        } catch (TypeMismatch e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testTypeMismatchWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwTypeMismatch(cause=cause);
            assert as "expected TypeMismatch to be thrown";
        } catch (TypeMismatch e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwTypeMismatch(String? text = Null, Exception? cause = Null) {
        throw new TypeMismatch(text, cause);
    }

    void testOutOfBounds() {
        try {
            throwOutOfBounds();
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testOutOfBoundsWithMessage() {
        try {
            throwOutOfBounds("foo");
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testOutOfBoundsWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwOutOfBounds(cause=cause);
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwOutOfBounds(String? text = Null, Exception? cause = Null) {
        throw new OutOfBounds(text, cause);
    }

    void testConcurrentModification() {
        try {
            throwConcurrentModification();
            assert as "expected ConcurrentModification to be thrown";
        } catch (ConcurrentModification e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testConcurrentModificationWithMessage() {
        try {
            throwConcurrentModification("foo");
            assert as "expected ConcurrentModification to be thrown";
        } catch (ConcurrentModification e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testConcurrentModificationWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwConcurrentModification(cause=cause);
            assert as "expected ConcurrentModification to be thrown";
        } catch (ConcurrentModification e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwConcurrentModification(String? text = Null, Exception? cause = Null) {
        throw new ConcurrentModification(text, cause);
    }

    void testNotAssigned() {
        try {
            throwNotAssigned();
            assert as "expected NotAssigned to be thrown";
        } catch (NotAssigned e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testNotAssignedWithMessage() {
        try {
            throwNotAssigned("foo");
            assert as "expected NotAssigned to be thrown";
        } catch (NotAssigned e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testNotAssignedWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwNotAssigned(cause=cause);
            assert as "expected NotAssigned to be thrown";
        } catch (NotAssigned e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwNotAssigned(String? text = Null, Exception? cause = Null) {
        throw new NotAssigned(text, cause);
    }

    void testIllegalArgument() {
        try {
            throwIllegalArgument();
            assert as "expected IllegalArgument to be thrown";
        } catch (IllegalArgument e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testIllegalArgumentWithMessage() {
        try {
            throwIllegalArgument("foo");
            assert as "expected IllegalArgument to be thrown";
        } catch (IllegalArgument e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testIllegalArgumentWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwIllegalArgument(cause=cause);
            assert as "expected IllegalArgument to be thrown";
        } catch (IllegalArgument e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwIllegalArgument(String? text = Null, Exception? cause = Null) {
        throw new IllegalArgument(text, cause);
    }

    void testIllegalState() {
        try {
            throwIllegalState();
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testIllegalStateWithMessage() {
        try {
            throwIllegalState("foo");
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testIllegalStateWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwIllegalState(cause=cause);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwIllegalState(String? text = Null, Exception? cause = Null) {
        throw new IllegalState(text, cause);
    }

    void testAssertion() {
        try {
            throwAssertion();
            assert as "expected Assertion to be thrown";
        } catch (Assertion e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testAssertionWithMessage() {
        try {
            throwAssertion("foo");
            assert as "expected Assertion to be thrown";
        } catch (Assertion e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testAssertionWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwAssertion(cause=cause);
            assert as "expected Assertion to be thrown";
        } catch (Assertion e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwAssertion(String? text = Null, Exception? cause = Null) {
        throw new Assertion(text, cause);
    }

    void testUnsupported() {
        try {
            throwUnsupported();
            assert as "expected Unsupported to be thrown";
        } catch (Unsupported e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testUnsupportedWithMessage() {
        try {
            throwUnsupported("foo");
            assert as "expected Unsupported to be thrown";
        } catch (Unsupported e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testUnsupportedWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwUnsupported(cause=cause);
            assert as "expected Unsupported to be thrown";
        } catch (Unsupported e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwUnsupported(String? text = Null, Exception? cause = Null) {
        throw new Unsupported(text, cause);
    }

    void testNotImplemented() {
        try {
            throwNotImplemented();
            assert as "expected NotImplemented to be thrown";
        } catch (NotImplemented e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testNotImplementedWithMessage() {
        try {
            throwNotImplemented("foo");
            assert as "expected NotImplemented to be thrown";
        } catch (NotImplemented e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testNotImplementedWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwNotImplemented(cause=cause);
            assert as "expected NotImplemented to be thrown";
        } catch (NotImplemented e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwNotImplemented(String? text = Null, Exception? cause = Null) {
        throw new NotImplemented(text, cause);
    }

    void testClosed() {
        try {
            throwClosed();
            assert as "expected Closed to be thrown";
        } catch (Closed e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testClosedWithMessage() {
        try {
            throwClosed("foo");
            assert as "expected Closed to be thrown";
        } catch (Closed e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testClosedWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwClosed(cause=cause);
            assert as "expected Closed to be thrown";
        } catch (Closed e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwClosed(String? text = Null, Exception? cause = Null) {
        throw new Closed(text, cause);
    }

    void testNotShareable() {
        try {
            throwNotShareable();
            assert as "expected NotShareable to be thrown";
        } catch (NotShareable e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testNotShareableWithMessage() {
        try {
            throwNotShareable("foo");
            assert as "expected NotShareable to be thrown";
        } catch (NotShareable e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testNotShareableWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwNotShareable(cause=cause);
            assert as "expected NotShareable to be thrown";
        } catch (NotShareable e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwNotShareable(String? text = Null, Exception? cause = Null) {
        throw new NotShareable(text, cause);
    }

    void testTimedOut() {
        try {
            throwTimedOut();
            assert as "expected TimedOut to be thrown";
        } catch (TimedOut e) {
            assert e.text == Null;
            assert e.cause == Null;
        }
    }

    void testTimedOutWithMessage() {
        try {
            throwTimedOut("foo");
            assert as "expected TimedOut to be thrown";
        } catch (TimedOut e) {
            assert e.text == "foo";
            assert e.cause == Null;
        }
    }

    void testTimedOutWithCause() {
        Exception cause = new Exception("cause");
        try {
            throwTimedOut(cause=cause);
            assert as "expected TimedOut to be thrown";
        } catch (TimedOut e) {
            assert e.text == Null;
            assert e.cause == cause;
        }
    }

    void throwTimedOut(String? text = Null, Exception? cause = Null) {
        throw new TimedOut(new Timeout(Duration.ofMinutes(5)), text, cause);
    }
}