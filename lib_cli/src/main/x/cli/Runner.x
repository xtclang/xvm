/**
 * The Runner singleton.
 */
import Scanner.CmdInfo;

static service Runner {

    typedef Map<String, CmdInfo> as Catalog;

    @Inject Console console;

    String description   = "";
    String commandPrompt = "> ";
    String messagePrefix = "# ";

    /**
     * Initialization.
     */
    void initialize(String description, String commandPrompt) {
        // avoid double initialization if the cli module gets imported
        if (this.description == "") {
            this.description   = description;
            this.commandPrompt = commandPrompt;
        }
    }

    /**
     * The entry point.
     *
     * @param app              the app that contains classes with commands.
     * @param args             (optional) the arguments passed by the user via the command line
     * @param suppressWelcome  (optional) pass `True` to avoid printing the default welcome message
     * @param extras           (optional) extra objects that contain executable commands
     * @param init             (optional) function to call at the end of initialization
     */
    void run(TerminalApp      app,
             String[]         args            = [],
             Boolean          suppressWelcome = False,
             Object[]         extras          = [],
             function void()? init            = Null,
             function void()? shutdown        = Null,
            ) {
        Catalog catalog = Scanner.buildCatalog(app, extras);

        if (args.size == 0) {
            if (description.empty) {
                description = &app.actualClass.name;
            }

            if (!suppressWelcome) {
                app.print(description);
            }

            init?();
            runLoop(catalog);
            shutdown?();
        } else {
            init?();
            runOnce(catalog, args);
            shutdown?();
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
            String command = console.readLine(commandPrompt);

            (Boolean ok, String[] args) = parseCommand(command);
            if (ok) {
                if (!runCommand(args, catalog)) {
                    return;
                }
            } else {
                console.print($"Error in command: {args[0]}");
            }
        }
    }

    /**
     * Parse the command string into a series of pieces.
     *
     * @return True iff the parsing encountered no issues
     * @return an array of parsed arguments, otherwise a description of the parsing error
     */
    (Boolean, String[]) parseCommand(String command) {
        Int          offset   = 0;
        Int          length   = command.size;
        String[]     args     = new String[];
        StringBuffer buf      = new StringBuffer();
        Boolean      quoted   = False;
        Boolean      escaped  = False;
        while (offset < length) {
            switch (Char ch = command[offset++]) {
            case ' ':
                if (quoted) {
                    if (escaped) {
                        buf.add('\\');      // it was not an escape
                        escaped = False;
                    }
                    buf.add(' ');
                } else if (buf.size > 0) {
                    args.add(buf.toString());
                    buf = new StringBuffer();
                }
                break;

            case '"':
                if (escaped) {
                    buf.add('\"');
                    escaped = False;
                } else if (quoted) {
                    // this needs to be the end of the command or there needs to be a space
                    if (offset < length && command[offset] != ' ') {
                        return False, ["A space is required after a quoted argument"];
                    }

                    args.add(buf.toString());
                    buf    = new StringBuffer();
                    quoted = False;
                } else if (buf.size == 0) {
                    quoted = True;
                } else {
                    return False, ["Unexpected quote character"];
                }
                break;

            case '\\':
                if (escaped) {
                    buf.add('\\');
                    escaped = False;
                } else if (quoted) {
                    escaped = True;
                } else {
                    return False, ["Unexpected escape outside of quoted text"];
                }
                break;

            case '0':
            case 'b':
            case 'd':
            case 'e':
            case 'f':
            case 'n':
            case 'r':
            case 't':
            case 'v':
            case 'z':
            case '\'':
                if (escaped) {
                    buf.append(switch (ch) {
                        case '0':  '\0';
                        case 'b':  '\b';
                        case 'd':  '\d';
                        case 'e':  '\e';
                        case 'f':  '\f';
                        case 'n':  '\n';
                        case 'r':  '\r';
                        case 't':  '\t';
                        case 'v':  '\v';
                        case 'z':  '\z';
                        case '\'': '\'';
                        default: assert;
                    });
                    escaped = False;
                } else {
                    buf.add(ch);
                }
                break;

            default:
                if (escaped) {
                    // it wasn't an escape
                    buf.add('\\');
                    escaped = False;
                }
                buf.add(ch);
                break;
            }
        }

        if (quoted) {
            return False, ["Missing a closing quote"];
        }

        if (buf.size > 0) {
            args.add(buf.toString());
        }

        return True, args;
    }

    /**
     * Find the specified command in the catalog.
     */
    conditional CmdInfo findCommand(String command, Catalog catalog) {
        String? bestMatch = Null;
        Boolean ambiguous = False;
        for ((String name, CmdInfo info) : catalog) {
            if (command == name) {
                return True, info;
            }
            // check if it's a matching prefix and there are no ambiguities
            if (name.startsWith(command)) {
                if (bestMatch == Null) {
                    bestMatch = name;
                } else {
                    ambiguous = True;
                }
            }
        }
        if (bestMatch != Null && !ambiguous) {
            return catalog.get(bestMatch);
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

        conditional Tuple addArg(Tuple args, Parameter param, String argStr) {
            Type paramType = param.ParamType;

            if (paramType.form == Union,
                    (Type leftType, Type rightType) := paramType.relational(),
                    leftType == Nullable) {
                if (argStr.empty || argStr == "-" || argStr.toLowercase() == "null") {
                    return True, args.add(Null);
                }
                paramType = rightType;
            }

            if (paramType.is(Type<Destringable>)) {
                paramType.DataType argValue = new paramType.DataType(argStr);
                return True, args.add(argValue);
            } else if (paramType.is(Type<Boolean>)) {
                import ecstasy.collections.CaseInsensitive;
                paramType.DataType argValue = CaseInsensitive.areEqual(argStr, "True");
                return True, args.add(argValue);
            } else {
                console.print($|  Unsupported type "{paramType}" for parameter "{param}"
                             );
                return False;
            }
        }

        if (CmdInfo info := findCommand(head, catalog)) {
            try {
                Method      method   = info.method;
                Parameter[] params   = method.params;
                Int         reqCount = method.requiredParamCount;
                Int         allCount = params.size;
                Int         argCount = parts-1;
                if (argCount <= allCount) {
                    Tuple args = Tuple:();

                    // collect the specified values
                    for (Int i : 1 ..< parts) {
                        Parameter param = params[i-1];
                        if (!(args := addArg(args, param, command[i]))) {
                            return True;
                        }
                    }
                    // if not all values were specified, prompt for the rest
                    if (argCount < reqCount) {
                        AddArgs:
                        for (Int i : argCount ..< allCount) {
                            Parameter param  = params[i];
                            String    prompt = param.is(Desc)?.text? : param.hasName()? : assert;
                            Boolean   noEcho = param.is(NoEcho);
                            Boolean   opt    = param.defaultValue();

                            String arg;
                            do {
                                arg = console.readLine($"  {prompt}{opt ? "(opt) ":""}> ", noEcho);
                                if (arg.empty && opt) {
                                    args = args.add(param.defaultValue()?) : assert;
                                    continue AddArgs;
                                }
                            } while (arg.empty);

                            if (!(args := addArg(args, param, arg))) {
                                return True;
                            }
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
                    console.print($"  Number of arguments should be between {reqCount} and {allCount}");
                }
            } catch (Exception e) {
                console.print($"  Error: {e.message.empty ? &e.actualType : e.message}");
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
            console.print($|{description}
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
}

