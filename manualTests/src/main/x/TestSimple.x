module TestSimple.test.org
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    void run()
        {
        log.add("\n** assert expression");
        try
            {
            Int n = assert;
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-false expression");
        try
            {
            Int n = assert False;
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-as expression");
        try
            {
            Int n = assert as "Hello world!";
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-false-as expression");
        try
            {
            Int n = assert False as $"log type={&log.actualType}";
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert statement");
        try
            {
            assert;
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-false statement");
        try
            {
            assert False;
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-as statement");
        try
            {
            assert as "Hello world!";
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-false-as statement");
        try
            {
            assert False as $"log type={&log.actualType}";
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-cond statement");
        try
            {
            Int x = -5;
            assert x >= 0;
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        log.add("\n** assert-cond-as statement");
        try
            {
            Int x = -5;
            assert x >= 0 as "Goodbye, cruel world!";
            }
        catch (Exception e)
            {
            log.add(e.toString());
            }

        TODO Terminat hora diem, terminat auctor opus.
        }
    }