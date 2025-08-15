# JavaTools JIT Bridge

The `javatools_jitbridge` module serves as a **bridge between the Java runtime and the Ecstasy type system for JIT compilation**.

## Purpose

- Acts as a "_native" module that connects Java runtime to Ecstasy's type system
- Provides Java implementations of core Ecstasy types and classes
- Enables XVM (Ecstasy Virtual Machine) to execute Ecstasy code by translating it to Java bytecode

## Structure

- Contains Java classes that mirror Ecstasy types (`org.xtclang.ecstasy.*`)
- Includes base classes like `xObj`, `Object`, `String`, `Array`, collections, etc.
- Has native system integrations (`_native` package) for IO and container management

## Build Integration

- Defined as an included build in `settings.gradle.kts`
- Creates a JAR artifact consumed by the XDK distribution
- Depends on the main `javatools` library
- Published as `org.xtclang:javatools-jitbridge` artifact

## Distribution

- Packaged into the XDK distribution alongside the main javatools JAR
- Used at runtime when XVM needs to execute JIT-compiled Ecstasy code
- Javac linting is disabled due to numerous warnings (likely from generated/bridge code)

The module essentially provides the runtime foundation for executing Ecstasy programs in a Java environment via JIT compilation.