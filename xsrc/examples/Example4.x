
module x // module or package ..
  {
  // short form alias
  import module spring.vmware.com;    
  // medium form alias
  import module spring.vmware.com as spring;        // alias the module any time i refer to "spring"
  // long form alias
  import module spring.vmware.com as  spring; // alias the module any time i refer to "spring"
  // long form embed
  embed [module] spring.vmware.com as spring;   // embed the entire module into package "spring"



  import rootpackage.secondpackage.ClassName
  import [package|class|interface|value|enum|mixin|trait|service] rootpackage.secondpackage.ClassName [as Name];
  
  import [module|package|class|interface|value|enum|mixin|trait|service] name.name.name [as name];
  ...
  }


// -- ignore below for now

interface Bag<Val>
  {
  // Q1: How to indicate (here in the declarative interface) that it is "get only"?
  @ro int size;
  @ro boolean empty;
  
  }
  
class ArrayBag<Val>
    implements Bag<Val>
  {
  public/private Int size;
  private void size.set(Int x) {assert x >= 0; super(x);}

  // or
  Int size
    {
    private void set(int x) {assert x >= 0; super(x);}
    }
  
  Int size
    {
    public Int get(); // implied
    public Int get() {return super();} // implied

    protected set(Int n);
    protected set(Int n) {super(n);} // implied
    }
  
  Boolean empty.get()
    {
    return size == 0;
    }
    
  }