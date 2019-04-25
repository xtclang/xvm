module TestArray.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        testSimple();
        // testStrBuf();
        testConstElement();
        testConstSlice();

        testArrayList();
        testArrayListAdd();
        testFixedArray();

        testAssignSideEffects();
        testNew();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Int[] nums = [1,7];
        console.println("array=" + nums);
        console.println("size=" + nums.size);
        console.println("[0]=" + nums[0]);
        console.println("[1]=" + nums[1]);
        }

    void testStrBuf()
        {
        console.println("\n** testStrBuf()");

        StringBuffer sb = new StringBuffer();
        sb.append("hello")
          .append(' ')
          .append("world")
          .append('!');

        console.println("sb=" + sb);
        }

    void testConstElement()
        {
        console.println("\n** testConstElement()");

        String cruel = ["hello", "cruel", "world", "!"] [1];
        console.println("array[1]=" + cruel);
        }

    void testConstSlice()
        {
        console.println("\n** testConstSlice()");

        String[] cruel = ["hello", "cruel", "world", "!"] [1..2];
        console.println("array[1..2]=" + cruel);

        String[] cruel2 = ["hello", "cruel", "world", "!"] [2..1];
        console.println("array[2..1]=" + cruel2);
        }

    void testArrayList()
        {
        console.println("\n** testArrayList()");

        String[] list = new String[];
        list[0] = "one";
        list[1] = "two";
        list[2] = "three";

        console.println("list=" + list);

        list = new Array<String>(Mutability.Mutable, list);
        String one = list.getElement(0);
        list.setElement(0, "uno");
        console.println("list=" + list);

        list = new Array<String>(list, 1..2);
        console.println("list=" + list);

        list = new Array<String>(Mutability.Fixed, list[0..1]);
        list.setElement(0, "один");
        list.setElement(1, "два");
        console.println("list=" + list);
        }

    void testArrayListAdd()
        {
        console.println("\n** testArrayListAdd()");

        String[] list = new String[];
//        list = list + "one";
//        list = list + ["two", "three"];
        list += "one";
        list += ["two", "three"];

        console.println("list=" + list);
        }

    void testFixedArray()
        {
        console.println("\n** testFixedArray()");

        String[] list = new Array<String>(3, (i) -> ["one","two","three"][i]);
        console.println("list=" + list);
        }

    void testAssignSideEffects()
        {
        console.println("\n** testAssignSideEffects()");

        Int n = 5;
        n += 7;
        console.println("n=" + n);

        Int[] nums = new Int[];
        Int   i    = 0;
        nums[i++] = 5;
        console.println("nums=" + nums + ", i=" + i);

        nums[--i] += 7;
        console.println("nums=" + nums + ", i=" + i);
        }

    void testNew()
        {
        console.println("\n** testNew()");

        String[] array = new Array<String>(10);
        console.println("array=" + array + ", size=" + array.size);

        for (Int i : 1..10)
            {
            array += "#" + i;
            }
        console.println("array=" + array + ", size=" + array.size);
        }
    }