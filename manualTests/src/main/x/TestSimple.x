module TestSimple
    {
    @Inject Console console;

    void run(String[] args=[])
        {
        String s = "1K";
        IntLiteral n = 1K;
        val show = () ->
            {
            console.println($"{s} === {new IntLiteral(s)} === {new IntLiteral(s).toInt128()} == {n}");
            };
        show();

        s = "1K";   n = 1K;   show();
        s = "1KB";  n = 1KB;  show();
        s = "1KI";  n = 1KI;  show();
        s = "1kB";  n = 1kB;  show();
        s = "1kI";  n = 1kI;  show();
        s = "1Kb";  n = 1Kb;  show();
        s = "1Ki";  n = 1Ki;  show();
        s = "1Kib"; n = 1Kib; show();
        s = "1M";   n = 1M;   show();
        s = "1MB";  n = 1MB;  show();
        s = "1Mi";  n = 1Mi;  show();
        s = "1G";   n = 1G;   show();
        s = "1Gi";  n = 1Gi;  show();
        s = "1GiB"; n = 1GiB; show();
        s = "1T";   n = 1T;   show();
        s = "1Ti";  n = 1Ti;  show();
        s = "1P";   n = 1P;   show();
        s = "1PB";  n = 1PB;  show();
        s = "1Pi";  n = 1Pi;  show();
        s = "1PiB"; n = 1PiB; show();
        s = "1E";   n = 1E;   show();
        s = "1Ei";  n = 1Ei;  show();

        console.println(n);
        }
    }