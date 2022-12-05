module ProducerConsumer
    {
    // -----------------------------------------------------------------------

    interface Pro<Element>
        {
        Element get();
        }

    interface Con<Element>
        {
        void set(Element value);
        }

    interface ProCon<Element>
            extends Pro<Element>
            extends Con<Element>;

    class ProConImpl<Element>(Element e)
            implements ProCon<Element>
        {
        @Override
        Element get()
            {
            return e;
            }

        @Override
        void set(Element value)
            {
            e = value;
            }
        }

    // -----------------------------------------------------------------------

    Boolean consumeObject(Con<Object> consumer)
        {
        try
            {
            consumer.set(3);
            return True;
            }
        catch (Exception e)
            {
            return False;
            }
        }

    Boolean consumeString(Con<String> consumer)
        {
        try
            {
            consumer.set("hello");
            return True;
            }
        catch (Exception e)
            {
            return False;
            }
        }

    Boolean produceObject(Pro<Object> producer)
        {
        try
            {
            Object o = producer.get();
            return True;
            }
        catch (Exception e)
            {
            return False;
            }
        }

    Boolean produceString(Pro<String> producer)
        {
        try
            {
            String s = producer.get();
            return True;
            }
        catch (Exception e)
            {
            return False;
            }
        }

    Boolean proconObject(ProCon<Object> procon)
        {
        return produceObject(procon) && consumeObject(procon);
        }

    Boolean proconString(ProCon<String> procon)
        {
        return produceString(procon) && consumeString(procon);
        }

    // -----------------------------------------------------------------------

    void run()
        {
        ProCon<Object> pco = new ProConImpl(1);
        ProCon<String> pcs = new ProConImpl("test");

        Pro<Object> po = pco;  // ok
        Pro<String> ps = pcs;  // ok

        Con<Object> co = pco;  // ok
        Con<String> cs = pcs;  // ok

        // invariant
        assert consumeObject(co);     // ok
        assert consumeString(cs);     // ok
        assert produceObject(po);     // ok
        assert produceString(ps);     // ok

        // invariant (but with pro/con)
        assert consumeObject(pco);    // ok
        assert consumeString(pcs);    // ok
        assert produceObject(pco);    // ok
        assert produceString(pcs);    // ok
        assert proconObject(pco);     // ok
        assert proconString(pcs);     // ok

        // covariant/contravariant

        assert consumeString(co);      // ok
        assert consumeString(pco);     // ok
        assert produceObject(ps);      // ok
        assert produceObject(pcs);     // ok

        // consumeObject(cs);   <-- compiler error:
        // Parameter 1 ("consumer") type mismatch for method "void consumeObject(Con<Object>)";
        //   required "Con<Object>", actual "Con<String>".

        // consumeObject(pcs);  <-- still a compiler error:


        // produceString(po);  <-- compiler error:
        // Parameter 1 ("producer") type mismatch for method "void produceString(Pro<String>)";
        //   required "Pro<String>", actual "Pro<Object>"

        // produceString(pco);  <-- still a compiler error:

        assert !proconObject(pcs);      // runtime TypeMismatch exception

        // proconString(pco);   <-- compiler error
        // Parameter 1 ("procon") type mismatch for method "void proconString(ProCon<String>)";
        //   required "ProCon<String>", actual "ProCon<Object>"

        // Con<Object>    co2  = cs; <-- compiler error
        Pro<Object>    po2  = ps;   // ok
        ProCon<Object> pco2 = pcs;  // ok
        Con<Object>    co2  = pco2; // ok (but "suspicious")

        consumeString(co2);         // still ok
        consumeString(pco2);        // still ok

        // produceString(po2);   <-- still a compiler error:
        // produceString(pco2);  <-- still a compiler error:

        assert !consumeObject(co2);   // runtime TypeMismatch exception
        assert !consumeObject(pco2);  // runtime TypeMismatch exception
        assert !proconObject(pco2);   // runtime TypeMismatch exception
        }
    }