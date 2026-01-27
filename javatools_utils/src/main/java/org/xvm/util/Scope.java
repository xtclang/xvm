package org.xvm.util;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A fluent Java utility that emulates Kotlin's scope functions: let, also, apply, run, and with.
 *
 * <p>Based on the semantics described in the
 * <a href="https://kotlinlang.org/docs/scope-functions.html">Kotlin documentation</a>.
 *
 * <h2>Design Goals</h2>
 * <ul>
 *   <li>Preserve Kotlin semantics: let/run return block result, also/apply return the original object</li>
 *   <li>Enable fluent chaining</li>
 *   <li>Support nullable values (Optional-style)</li>
 *   <li>Remain idiomatic, pure Java (no reflection, no bytecode tricks)</li>
 * </ul>
 *
 * <h2>Limitations (Java vs Kotlin)</h2>
 * <ul>
 *   <li>Lambdas cannot have implicit {@code this} or {@code it}</li>
 *   <li>No smart casts</li>
 *   <li>Null-safety must be explicit</li>
 * </ul>
 *
 * <h2>Quick Example</h2>
 * <pre>{@code
 * Person person = Scope.on(new Person())
 *     .apply(p -> {
 *         p.setName("Alice");
 *         p.setAge(30);
 *     })
 *     .also(p -> System.out.println("Created: " + p))
 *     .get();
 *
 * String label = Scope.on(person)
 *     .let(p -> p.getName() + " (" + p.getAge() + ")");
 * }</pre>
 */
public final class Scope<T> {

    private final T value;

    private Scope(T value) {
        this.value = value;
    }

    /**
     * <b>Factory Method:</b> Entry point for scope chaining.
     *
     * <p>Wraps a value in a Scope to enable fluent method chaining with scope functions.
     * Equivalent to Kotlin's {@code obj.run { ... }} or {@code obj.let { ... }}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * String result = Scope.on("hello")
     *     .let(String::toUpperCase);
     * // result = "HELLO"
     *
     * StringBuilder sb = Scope.on(new StringBuilder())
     *     .apply(b -> b.append("Hello"))
     *     .apply(b -> b.append(" World"))
     *     .get();
     * // sb.toString() = "Hello World"
     * }</pre>
     *
     * @param value the value to wrap (null is allowed)
     * @param <T> the type of the value
     * @return a new Scope wrapping the value
     */
    public static <T> Scope<T> on(T value) {
        return new Scope<>(value);
    }

    /**
     * <b>Factory Method:</b> Executes a block with an object and returns the result.
     *
     * <p>Equivalent to Kotlin's {@code with(obj) { ... }}.
     * Use when you need to call multiple methods on an object and return a computed result.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * String info = Scope.with(person, p ->
     *     p.getName() + " is " + p.getAge() + " years old");
     *
     * int totalLength = Scope.with(list, l ->
     *     l.stream().mapToInt(String::length).sum());
     * }</pre>
     *
     * @param value the context object
     * @param block the function to execute with the object
     * @param <T> the type of the context object
     * @param <R> the return type
     * @return the result of the block
     */
    public static <T, R> R with(T value, Function<? super T, ? extends R> block) {
        return block.apply(value);
    }

    /**
     * <b>Factory Method:</b> Executes a supplier block and returns its result.
     *
     * <p>Equivalent to Kotlin's {@code run { ... }} (without a receiver).
     * Use for computing a value from a block of code.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * String result = Scope.run(() -> {
     *     String first = getFirstName();
     *     String last = getLastName();
     *     return first + " " + last;
     * });
     * }</pre>
     *
     * @param block the supplier to execute
     * @param <R> the return type
     * @return the result of the block
     */
    public static <R> R run(Supplier<? extends R> block) {
        return block.get();
    }

    /**
     * <b>Scope Function:</b> Executes the block and returns its result.
     *
     * <p>Equivalent to Kotlin's {@code obj.let { it.transform() }}.
     * Use when you want to transform the object into something else.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * int length = Scope.on("hello").let(String::length);
     * // length = 5
     *
     * String upper = Scope.on("hello").let(s -> s.toUpperCase());
     * // upper = "HELLO"
     *
     * // Chain transformations
     * String result = Scope.on("  hello  ")
     *     .let(String::trim)
     *     .let(String::toUpperCase);
     * // result = "HELLO"
     * }</pre>
     *
     * @param block the transformation function
     * @param <R> the return type
     * @return the result of applying the block to the value
     */
    public <R> R let(Function<? super T, ? extends R> block) {
        return block.apply(value);
    }

    /**
     * <b>Scope Function:</b> Executes a side effect block and returns the Scope for chaining.
     *
     * <p>Equivalent to Kotlin's {@code obj.also { println(it) }}.
     * Use for side effects like logging, validation, or debugging without breaking the chain.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Person person = Scope.on(new Person())
     *     .apply(p -> p.setName("Alice"))
     *     .also(p -> System.out.println("Created: " + p))
     *     .also(p -> log.debug("Person id: {}", p.getId()))
     *     .get();
     *
     * // Validate and continue
     * String result = Scope.on(input)
     *     .also(s -> Objects.requireNonNull(s, "input required"))
     *     .also(s -> { if (s.isEmpty()) throw new IllegalArgumentException(); })
     *     .let(String::trim);
     * }</pre>
     *
     * @param block the side effect consumer
     * @return this Scope for chaining
     */
    public Scope<T> also(Consumer<? super T> block) {
        block.accept(value);
        return this;
    }

    /**
     * <b>Scope Function:</b> Executes a configuration block and returns the Scope for chaining.
     *
     * <p>Equivalent to Kotlin's {@code Obj().apply { x = 1; y = 2 }}.
     * Use for configuring or initializing an object. Semantically identical to {@link #also},
     * but communicates intent: apply is for configuration, also is for side effects.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * StringBuilder sb = Scope.on(new StringBuilder())
     *     .apply(b -> b.append("Hello"))
     *     .apply(b -> b.append(" "))
     *     .apply(b -> b.append("World"))
     *     .get();
     * // sb.toString() = "Hello World"
     *
     * HttpRequest request = Scope.on(new HttpRequest())
     *     .apply(r -> r.setMethod("POST"))
     *     .apply(r -> r.setUrl("/api/users"))
     *     .apply(r -> r.addHeader("Content-Type", "application/json"))
     *     .get();
     * }</pre>
     *
     * @param block the configuration consumer
     * @return this Scope for chaining
     */
    public Scope<T> apply(Consumer<? super T> block) {
        block.accept(value);
        return this;
    }

    /**
     * <b>Scope Function:</b> Executes a block and returns its result.
     *
     * <p>Equivalent to Kotlin's {@code obj.run { transform() }}.
     * Semantically identical to {@link #let}, included for Kotlin API parity.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * String greeting = Scope.on(person).run(p -> "Hello, " + p.getName());
     * }</pre>
     *
     * @param block the transformation function
     * @param <R> the return type
     * @return the result of applying the block to the value
     */
    public <R> R run(Function<? super T, ? extends R> block) {
        return block.apply(value);
    }

    /**
     * <b>Null-Safe Variant:</b> Executes the block only if the value is non-null.
     *
     * <p>Equivalent to Kotlin's {@code obj?.let { ... }}.
     * Returns {@code Optional.empty()} if the value is null.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Optional<Integer> length = Scope.on(nullableString)
     *     .letIfPresent(String::length);
     *
     * // Safe navigation
     * Optional<String> city = Scope.on(person)
     *     .letIfPresent(Person::getAddress)
     *     .flatMap(a -> Scope.on(a).letIfPresent(Address::getCity));
     * }</pre>
     *
     * @param block the transformation function
     * @param <R> the return type
     * @return Optional containing the result, or empty if value is null
     */
    public <R> Optional<R> letIfPresent(Function<? super T, ? extends R> block) {
        return Optional.ofNullable(value).map(block);
    }

    /**
     * <b>Null-Safe Variant:</b> Executes the side effect block only if the value is non-null.
     *
     * <p>Equivalent to Kotlin's {@code obj?.also { ... }}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Scope.on(nullableUser)
     *     .alsoIfPresent(u -> log.info("Processing user: {}", u.getName()))
     *     .alsoIfPresent(u -> metrics.increment("users.processed"));
     * }</pre>
     *
     * @param block the side effect consumer (only called if value is non-null)
     * @return this Scope for chaining
     */
    public Scope<T> alsoIfPresent(Consumer<? super T> block) {
        if (value != null) {
            block.accept(value);
        }
        return this;
    }

    /**
     * <b>Null-Safe Variant:</b> Executes the configuration block only if the value is non-null.
     *
     * <p>Equivalent to Kotlin's {@code obj?.apply { ... }}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Scope.on(nullableBuilder)
     *     .applyIfPresent(b -> b.setOption(true))
     *     .applyIfPresent(b -> b.configure());
     * }</pre>
     *
     * @param block the configuration consumer (only called if value is non-null)
     * @return this Scope for chaining
     */
    public Scope<T> applyIfPresent(Consumer<? super T> block) {
        if (value != null) {
            block.accept(value);
        }
        return this;
    }

    /**
     * <b>Terminal Operation:</b> Returns the wrapped value.
     *
     * <p>Use at the end of a chain to extract the final value.
     * The value may be null if null was passed to {@link #on(Object)}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Person person = Scope.on(new Person())
     *     .apply(p -> p.setName("Alice"))
     *     .get();
     * }</pre>
     *
     * @return the wrapped value (null is allowed)
     */
    public T get() {
        return value;
    }

    /**
     * <b>Terminal Operation:</b> Returns the value wrapped in an Optional.
     *
     * <p>Use when you want to continue with Optional-style processing.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * String name = Scope.on(nullablePerson)
     *     .toOptional()
     *     .map(Person::getName)
     *     .orElse("Unknown");
     * }</pre>
     *
     * @return Optional containing the value, or empty if null
     */
    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }

    /**
     * <b>Terminal Operation:</b> Returns the value or throws if null.
     *
     * <p>Use when null is not expected and should be treated as an error.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Person person = Scope.on(findPerson(id))
     *     .requireNonNull();  // throws if not found
     * }</pre>
     *
     * @return the non-null value
     * @throws NullPointerException if the value is null
     */
    public T requireNonNull() {
        return Objects.requireNonNull(value, "Scoped value is null");
    }

    @Override
    public String toString() {
        return "Scope[" + value + "]";
    }
}