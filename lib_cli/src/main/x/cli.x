/**
 * Command Line Interface support.
 *
 * To use the CLI library, the application code needs to do the following:
 *     - annotate the module as a `TerminalApp`, for example:
 *          @TerminalApp("My commands")
 *          module MyCommands {...}
 *
 *     - annotate any methods to be executed as a command with the `Command` annotation, for example:
 *          @Command("time", "Show current time")
 *          Time showTime() {
 *             @Inject Clock clock;
 *             return clock.now;
 *          }
 *
 * Note: all stateless API can be placed at the main module level. Any stateful API needs to placed
 *       inside of a class or service with a default constructor.
 *
 * In addition to all introspected commands, the TerminalApp mixin provides two built-in commands:
 *      - help [command-opt]
 *      - quit
 */
module cli.xtclang.org {

    @Inject Console console;

    mixin TerminalApp(String description   = "",
                      String commandPrompt = "> ",
                      String messagePrefix = "# ",
                      )
            into module {

        typedef Map<String, CmdInfo> as Catalog;

        /**
         * The entry point.
         */
        void run(String[] args) {
            Catalog catalog = buildCatalog(this);
            if (args.size == 0) {
                runLoop(catalog);
            } else {
                runOnce(catalog, args);
            }
        }

        /**
         * Run a single command.
         */
        void runOnce(Catalog catalog, String[] args) {
            runCommand(args, catalog);
        }

        /**
         * Read commands from the console and run them.
         */
        void runLoop(Catalog catalog) {
            while (True) {
                console.print(commandPrompt, suppressNewline=True);

                String command = console.readLine();

                if (!runCommand(command.split(' ', trim=True), catalog)) {
                    return;
                }
            }
        }

        /**
         * Find the specified command in the catalog.
         */
        conditional CmdInfo findCommand(String command, Catalog catalog) {
            for ((String name, CmdInfo info) : catalog) {
                if (command.startsWith(name)) {
                    return True, info;
                }
            }
            return False;
        }

        /**
         * Run the specified command.
         *
         * @return False if the command is "quit"; True otherwise
         */
        Boolean runCommand(String[] command, Catalog catalog) {
            Int parts = command.size;
            if (parts == 0) {
                return True;
            }

            String head = command[0];
            switch (head) {
            case "":
                return True;
            case "quit":
                return False;
            case "help":
                printHelp(parts == 1 ? "" : command[1], catalog);
                return True;
            }

            if (CmdInfo info := findCommand(head, catalog)) {
                try {
                    Method      method = info.method;
                    Parameter[] params = method.params;
                    if (method.requiredParamCount <= parts-1 <= params.size) {
                        Tuple args   = Tuple:();
                        for (Int i : 1 ..< parts) {
                            String    argStr    = command[i];
                            Parameter param     = params[i-1];
                            Type      paramType = param.ParamType;
                            if (paramType.is(Type<Destringable>)) {
                                paramType.DataType argValue = new paramType.DataType(argStr);
                                args = args.add(argValue);
                            } else {
                                console.print($|  Unsupported type "{paramType}" for parameter \
                                               |"{param}"
                                             );
                                return True;
                            }
                        }

                        Tuple result = method.invoke(info.target, args);

                        switch (result.size) {
                        case 0:
                            console.print();
                            break;
                        case 1:
                            console.print(result[0]);
                            break;
                        default:
                            for (Int i : 0 ..< result.size) {
                                console.print($"[{i}]={result[i]}");
                            }
                            break;
                        }
                    } else {
                        if (method.defaultParamCount == 0) {
                            console.print($"  Required {params.size} arguments");

                        } else {
                            console.print($|  Number of arguments should be between \
                                           |{method.requiredParamCount} and {params.size}
                                         );
                        }
                    }
                } catch (Exception e) {
                    console.print($"  Error: {e.message}");
                }
            } else {
                console.print($"  Unknown command: {head.quoted()}");
            }
            return True;
        }

        /**
         * Print the instructions for the specified command or all the commands.
         */
        void printHelp(String command, Catalog catalog) {
            if (command == "") {
                console.print($|{description == "" ? &this.actualClass.toString() : description}
                               |
                               |Commands are:
                              );
                Int maxName = catalog.keys.map(s -> s.size)
                                          .reduce(0, (s1, s2) -> s1.maxOf(s2));
                for ((String name, CmdInfo info) : catalog) {
                    console.print($"  {name.leftJustify(maxName+1)} {info.method.descr}");
                }
            } else if (CmdInfo info := findCommand(command, catalog)) {
                Command method = info.method;
                console.print($|  {method.descr == "" ? info.method.name : method.descr}
                              );

                Parameter[] params     = method.params;
                Int         paramCount = params.size;
                if (paramCount > 0) {
                    console.print("Parameters:");

                    String[] names = params.map(p -> {
                        assert String name := p.hasName();
                        return p.defaultValue() ? $"{name} (opt)" : name;
                    }).toArray();

                    Int maxName = names.map(n -> n.size)
                                       .reduce(0, (s1, s2) -> s1.maxOf(s2));
                    for (Int i : 0 ..< paramCount) {
                        Parameter param = params[i];
                        console.print($|  {names[i].leftJustify(maxName)}  \
                                       |{param.is(Desc) ? param.text : ""}
                                       );
                    }
                }
            } else {
                console.print($"  Unknown command: {command.quoted()}");
            }
        }

        void printResult(Tuple result) {
            Int count = result.size;
            switch (count) {
            case 0:
                break;

            case 1:
                console.print($"  {result[0]}");
                break;

            default:
                for (Int i : 0 ..< count) {
                    console.print($"  [i]={result[i]}");
                }
                break;
            }
        }

        /**
         * This method is meant to be used by the CLI classes to differentiate the output of the
         * framework itself and of its users.
         */
        void print(String s) {
            console.print($"{messagePrefix} {s}");
        }
    }

    mixin Command(String cmd = "", String descr = "")
            into Method<Object>;

    mixin Desc(String? text = Null)
            into Parameter<Object>;

    static Map<String, CmdInfo> buildCatalog(TerminalApp app) {
        Map<String, CmdInfo> cmdInfos = new ListMap();

        scanCommands(() -> app, &app.actualClass, cmdInfos);
        scanClasses(app.classes, cmdInfos);
        return cmdInfos;
    }

    static void scanCommands(function Object() instance, Class clz, Map<String, CmdInfo> catalog) {
        Type type = clz.PublicType;

        for (Method method : type.methods) {
            if (method.is(Command)) {
                String cmd = method.cmd == "" ? method.name : method.cmd;
                if (catalog.contains(cmd)) {
                    throw new IllegalState($|A duplicate command "{cmd}" by the method "{method}"
                                            );
                }
                catalog.put(cmd, new CmdInfo(instance(), method));
            }
        }
    }

    static void scanClasses(Class[] classes, Map<String, CmdInfo> catalog) {

        static class Instance(Class clz)  {
            @Lazy Object get.calc() {
                if (Object single := clz.isSingleton()) {
                    return single;
                }
                Type type = clz.PublicType;
                if (function Object () constructor := type.defaultConstructor()) {
                    return constructor();
                }
                throw new IllegalState($|default constructor is missing for "{clz}"
                                      );
                }
            }

        for (Class clz : classes) {
            if (clz.annotatedBy(Abstract)) {
                continue;
            }

            Instance instance = new Instance(clz);

            scanCommands(() -> instance.get, clz, catalog);
        }
    }

    class CmdInfo(Object target, Command method) {
        @Override
        String toString() {
            return method.toString();
        }
    }
}