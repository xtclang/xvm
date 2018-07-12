/**
 * Represents a loop label at runtime:
 *
 * For example:
 *
 *   Person[] people = [new Person("Bob"), new Person("Sue"), new Person("Amit")];
 *   eachPerson: for (Person person : people)
 *       {
 *       if (!eachPerson.first)
 *           {
 *           console.print(", ");
 *           }
 *       console.print($"{eachPerson.counter}={person.name}");
 *       }
 *
 * Produces the output:
 *
 *   0=Bob, 1=Sue, 2=Amit
 */
interface Label
    {
    /**
     * For a Label that identifies a loop, the value of this property is true until the completion
     * of the first iteration.
     *
     * For a non-loop Label, an attempt to access this property may result in an exception.
     */
    @RO Boolean first;

    /**
     * For a Label that identifies a loop, the value of this property is equal to the number of
     * iterations that have completed.
     *
     * For a non-loop Label, an attempt to access this property may result in an exception.
     */
    @RO Int counter;

    /**
     * For a Label that identifies a loop within which the last iteration is detectable, this value
     * will be true after the last iteration has begun.
     *
     * For a non-loop Label, or a Label that identifies a loop in which the last iteration is *not*
     * detectable, an attempt to access this property may result in an exception.
     */
    @RO Boolean last;
    }
