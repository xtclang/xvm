package org.xvm;

/**
 * Skeleton class for common build logic, tasks and plugins
 * written in Java.
 *
 * We could declare any task with any name like:
 *
 * task("something") {
 *     doLast {
 *         BuilderHelpers.sayHello()
 *     }
 * }
 *
 * (Or register it, if it is to be attached to part of the pre defined
 *  life cycle, an run automatically somehwere in the build. The example
 *  above is just callable through .e.g "gradlew something"...
 *
 */
public class BuildHelpers {
    public static void sayHello() {
        System.out.println("Hello from buildSrc!");
    }
}
