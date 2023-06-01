/**
 * A Label represents a source code label used to identify a "do", "while", or "for" statement.
 *
 * For example:
 *
 *   Person[] people = [new Person("Bob"), new Person("Sue"), new Person("Amit")];
 *   eachPerson: for (Person person : people)
 *       {
 *       if (!eachPerson.first)
 *           {
 *           console.print(", ", suppressNewline=True);
 *           }
 *       console.print($"{eachPerson.count}={person.name}", suppressNewline=True);
 *       }
 *
 * Produces the output:
 *
 *   0=Bob, 1=Sue, 2=Amit
 *
 * @param Key    the [Map.Key] type, available on a label for a "for" loop of the for-each variety
 *               iterating over a Map
 * @param Value  the [Map.Value] type, available on a label for a "for" loop of the for-each variety
 *               iterating over a Map
 */
interface Label<Key, Value> {
    /**
     * True on the first iteration of the loop (do, while, or for).
     *
     * For a Label that identifies a loop, the value of this property is True until the completion
     * of the first iteration.
     *
     * For a non-loop Label, an attempt to access this property is illegal.
     */
    @RO Boolean first;

    /**
     * The number of loop iterations that have completed.
     *
     * For a Label that identifies a loop, the value of this property is equal to the number of
     * iterations that have completed.
     *
     * For a non-loop Label, an attempt to access this property is illegal.
     */
    @RO Int count;

    /**
     * True on the last iteration of the loop. This value is only available on a label for a "for"
     * loop of the for-each variety iterating over an Interval or List.
     *
     * For a Label that identifies a loop within which the last iteration is detectable, this value
     * will be True after the last iteration has begun.
     *
     * For a non-loop Label, or a Label that identifies a loop in which the last iteration is
     * **not** detectable, an attempt to access this property is illegal.
     */
    @RO Boolean last;

    /**
     * The current Entry. This value is only available on a label for a "for" loop of the for-each
     * variety iterating over a Map.
     *
     * For a Label that identifies a for-each style loop over the contents of a Map, this value
     * will hold the Map Entry for the current iteration.
     *
     * For a non-loop Label, or a Label that identifies a loop in which there is no current Entry,
     * an attempt to access this property is illegal.
     */
    @RO Map<Key, Value>.Entry entry;

    /**
     * In the "finally" block of a "try" statement, this property provides the Exception that
     * occurred within the "try" statement, or Null if no Exception occurred.
     */
    @RO Exception? exception;
}
