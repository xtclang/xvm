This directory is for testing purposes only. The native launchers in the
`../exe` directory reference the `javatools.jar` file in this relative
directory. For purposes of testing, the "main class" in the `javatools.jar`
is:

    class Echo
        {
        public static void main(String[] args)
            {
            if (args == null)
                {
                System.out.println("no args");
                }
            else
                {
                int cArgs = args.length;
                System.out.println("" + cArgs + " args:");
                for (int i = 0; i < cArgs; ++i)
                    {
                    System.out.println("[" + i + "]=\"" + args[i] + '"');
                    }
                }
            }
        }

And the JAR's manifest contains only:

    Main-Class: Echo
