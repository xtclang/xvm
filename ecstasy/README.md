# Directory: ./xsrc/ #

This directory contains natural (Ecstasy) code.

## Runtime Library

Folder: `./system/`

Status: In Progress

* This is the core runtime library (module `Ecstasy.xtclang.org`).
* The XVM is tightly coupled with this module.
* It compiles, but there's a lot of stuff not done, and a lot of stuff missing.
* Major reorganization still occurs periodically; the organization will not be required to be stable until we approach the 1.0 release.
* There's a separate, related directory (`./_native/`) that is related to the proof-of-concept runtime.

Excellent examples to look at:
* `ecstasy.x` - the module definition (the root of the runtime library); contains many small class definitions
* `Object.x` - the root object
* `Service.x` - key to understanding the concurrency model
* `Enum.x` and `Enumeration.x` - the implementations for enum types
* `Boolean.x` - to be or not to be
* `String.x` - the name says it all
* `Array.x` - the base array implementation
* `Iterable.x` and `Iterator.x` - pretty obvious the 10th time around
* `fs/*.x` - basic filing system APIs
* `annotations/*.x` - mixins that apply to various things (like variables, methods, classes)

## Other stuff

Folder: `./examples/`

Status: Scratch-pad

This is just stuff that we're keeping around because sometimes we need something to remind us of who we are and why we're here, and there's not always enough coffee to pull that off.
