
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

// modules


module MyApp
    {
    package Spring import springframework.spring.org;
    package Spring import:embedded springframework.spring.org;
    package Spring import:required springframework.spring.org;               // required is default
    package Spring import:desired springframework.spring.org;
    package Spring import:optional springframework.spring.org;               // a.k.a. "supported"

    // avoid something like this
    package Spring import:if(...) springframework.spring.org;

    package Util
        {
        interface Map {...}
        class HashMap {...}
        }

    // module version is <=> some other version (with two versions potentially being none of <=>)
    // some name (module, package, class, method, property, ...) is present vs. not present
    //
    if (Module:"springframework.spring.org".version == Version:"1.2.3.4.5")
        {
        package Spring import springframework.spring.org;
        }
    else if (Module:"springframework.spring.org".version > Version:"1.2.3")
        {
        package Spring import springframework.spring.org;
        }
    else
        {
        package Spring import springframework.spring.org Version:"1.2.0";
        }

    // need to somehow simplify that though so that it's obvious FIRST that i need spring:
    package Spring import springframework.spring.org 1.2.0 prefer 1.2.3.4.5, 1.2.3 avoid 1.2.3.4.17


    package Spring import springframework.spring.org
        {
        case 1.2.3.4.5: true
        case 1.2.3.4.17: false
        case 1.2.3: true
        case 1.2.2:
            switch
                {
                case 1.2.3.4.17: AWFUL BAD NO DON'T USE IT
                case 1.2.3: PREFERRED
                }
        case 1.2.0: true
        }
    }

    1.4
    1.5.-4    1.5a
    1.5.-3    1.5b
    1.5.-3.1  1.5b2
    1.5.-2    1.5pr1
    1.5.-2.1  1.5pr2
    1.5.-1    1.5rc1
    1.5.-1.1  1.5rc2
    1.5

    version     ok  human-readable
    1.4         x
    1.5.ci1
    1.5.ci2
    1.5.ci(etc...)
    1.5.ci2985712
    1.5.alpha1
    1.5.beta1
    1.5.beta2
    1.5.rc1
    1.5.rc2
    1.5         x

    package Spring import springframework.spring.org 2.1 prefer ? avoid 2.1.0.0, 2.1.3.1

    package Spring import springframework.spring.org 2.1 -2.1.2 +2.1.3.1 -2.3.1

ModuleImport
    package Name import QualifiedName ModuleVersionReq-opt

ModuleVersionReq
    Version VersionOverrides-opt

VersionOverrides
    VersionOverride
    VersionOverrides VersionOverride

VersionOverride
    OverrideVerb Versions

OverrideVerb
    allow
    avoid
    prefer

Versions
    Version
    Versions, Version

Version
    TrailingVersionSegment
    Version . TrailingVersionSegment

TrailingVersionSegment:
    DigitsNoUnderscores
    NonGAVersionSegment

NonGAVersionSegment:
    NonGAVersionSpecifier DigitsNoUnderscores

NonGAVersionSpecifier:
    dev
    ci
    alpha
    beta
    rc

VersionSegment:
    Digit

    prerelease
    rc
    DigitsNoUnderscores Digit

package
    {
    if (Spring.present && Spring.version == 1.2.3)
        {
        PotentialSpring = Spring.IO.Externalizable
        class Employee
            implements Spring.IO.Externalizable;
        }
    }

class Employee implements PotentialSpring
    {
    if (Spring.present && Spring.version == 1.2.3)
        {
        Void superSpringRead(Spring.IO.InStream) {...}
        Void superSpringWrite(Spring.IO.OutStream) {...}
        }
    }

if there is a module that you can get for me called "springframework.spring.org", i could use it,
***but*** only if its version is 1.2.3 or a derivative thereof, and i'd really prefer to use 1.2.3.4.5
and if you can find it, i'm going to refer to it within my module as "Spring"

how do you say:
* i require spring
* i require spring 1.2 "or more recent on the 1.2 line"
* i require spring 1.2 "and explicitly 1.2 not something more recent!"
* i desire ...
*
--

module.x
  A
  B
  C

package.x
  P
  D
  Q

MyClass.x
  X
  Y
  Z

same as:
module.x
  A
  B
  C
    {
    P
    D
    Q
      {
      X
      Y
      Z
      }
    }


// what if?

// we can say:
Version ver = Version:"1.2.3";
Module spring = Module:"springframework.spring.org";
// so why can't we say:
Condition SPRING = Module:"springframework.spring.org".exists;
Condition NOSPRING = !Module:"springframework.spring.org".exists;

class Employee
        if (COND) delegates y
        if (!COND) delegates z

    {
    if (SPRING) {
        Void ...
        }

    }
