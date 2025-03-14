/**
 * Command Line Interface support.
 *
 * To use the CLI library, the application code needs to do the following:
 *     - annotate the module as a `TerminalApp`, for example:
 *
 *          @TerminalApp("My commands")
 *          module MyCommands {
 *              package cli import cli.xtclang.org;
 *
 *              import cli.*;
 *
 *              ...
 *          }
 *
 *     - annotate any methods to be executed as a command with the `Command` annotation, for example:
 *
 *          @Command("time", "Show current time")
 *          Time showTime() {
 *             @Inject Clock clock;
 *             return clock.now;
 *          }
 *
 * Note: all stateless API can be placed at the main module level. Any stateful API needs to placed
 *       inside of a class or service with a default constructor.
 *
 * In addition to all introspected commands, the TerminalApp provides two built-in commands:
 *      - help [command-opt]
 *      - quit
 */
module cli.xtclang.org {

    @Inject Console console;

    /**
     * The module annotation.
     */
    annotation TerminalApp
            into module {
        /**
         * The annotation constructor.
         *
         * @param description    the CLI tool description
         * @param commandPrompt  the command prompt
         * @param messagePrefix  the prefix for messages printed by the CLI tool itself via [print]
         *                       method
         */
        construct(String description   = "",
                  String commandPrompt = "> ",
                  String messagePrefix = "# "
                 ) {
            this.messagePrefix = messagePrefix;
            Runner.initialize(description, commandPrompt);
        }

        protected String messagePrefix;

        /**
         * The entry point.
         */
        void run(String[] args) = Runner.run(this, args);

        /**
         * This method is meant to be used by the CLI app classes to differentiate the output of
         * the framework itself from the output by the user code.
         */
        void print(Object o) = console.print($"{messagePrefix} {o}");

        /**
         * This mixin is useful when a CLI application needs to override the [run()] method, in
         * which case the application code would have to start with the following:
         *
         *     module MyCommands
         *             incorporates TerminalApp.Mixin("My commands") {
         *
         *         @Override
         *         void run(String[] args) {
         *             // custom implementation
         *         }
         *         ...
         *     }
         *
         */
        @TerminalApp
        static mixin Mixin
                into module {
            construct(String description   = "",
                      String commandPrompt = "> ",
                      String messagePrefix = "# "
                     ) {
                construct TerminalApp(description, commandPrompt, messagePrefix);
            }
        }
    }

    /**
     * The mixin into a command method.
     *
     * @param cmd    the command name
     * @param descr  the command description
     */
    annotation Command(String cmd = "", String descr = "")
            into Method<Object>;

    /**
     * The "command description" mixin into a command method parameter.
     *
     * @param text  the parameter description
     */
    annotation Desc(String? text = Null)
            into Parameter<Object>;

    /**
     * The "suppress echo" mixin into a command method parameter.
     */
    annotation NoEcho()
            into Parameter<Object>;
}