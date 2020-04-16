package org.xvm.tool;


/**
 * The "launcher" commands:
 *
 * <ul><li> <code>xtc</code> <i>("ecstasy")</i> routes to {@link Compiler}
 * </li><li> <code>xec</code> <i>("exec")</i> routes to {@link Runner}
 * </li><li> <code>xam</code> <i>("exam")</i> routes to {@link Disassembler}
 * </li></ul>
 */
public class Launcher
    {
    public static void main(String[] asArg)
        {
        int argc = asArg.length;
        if (argc < 1)
            {
            System.err.println("Command name is missing");
            return;
            }

        String cmd = asArg[0];

        --argc;
        String[] argv = new String[argc];
        System.arraycopy(asArg, 1, argv, 0, argc);
        switch (cmd)
            {
            case "xtc":
                Compiler.main(argv);
                break;

            case "xec":
                Runner.main(argv);
                break;

            case "xam":
                Disassembler.main(argv);
                break;

            default:
                System.err.println("Command name \"" + cmd + "\" is not supported");
                break;
            }
        }
    }

