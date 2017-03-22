
// arrays

Int[] vals;             // what type is that?   Array<Int>
vals = new Int[];       // what type is that?   MutableArray<Int>
vals = new Int[5];      // what type is that?   FixedSizeArray<Int>
vals = new Int[5](0);
vals = new Int?[5];
vals = vals.ensurePersistent();              //  PersistentArray
vals = {1,2,3};
vals = Int:{1,2,3};                         //  ConstArray

s = "hello";
s = String:"hello";

i = 5;
i = (IntegerLiteral:"5").to<Int>();

// bad -  but you get the idea
map = {{x:5
        y:8
        z:9}}


// let's mock up a new Collection implementation
// I have a few methods already written in BaseColl

class MyColl
        extends BaseColl
//      implements Collection
        delegates Collection(throw new UnsupportedOperationException("I haven't yet implemented Collection");)
    {

    Collection foo.get()
        {
        TODO
        }
    }