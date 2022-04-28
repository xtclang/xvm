# Directory: ./javatools_bridge/ #

This directory contains the "javatools_bridge" aka "_native"
project, which is a set of Ecstasy classes and interfaces that
are tightly bound to the runtime implementation found in the
"javatools" project.

This code is often referred to as "the native code", or "the
native bridge", because it is the code that represents the
capabilities outside of "Ecstasy land". In the Java
proof-of-concept, the Java code takes on the responsibility
of being the "native" code, in lieu of having an actual native
code compiler.

The License is the Apache License, Version 2.0. 
