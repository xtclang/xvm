# Are your paths correct?
#
# Makefile expects to run at the project root, and not nested inside anywhere
#
#  cd $DESK/xvm; make

# Use bash for recipes
SHELL := /bin/bash

# Keep partial builds but not partial recipes
.NOTINTERMEDIATE:	

# for printing variable values
# usage: make print-VARIABLE
#        > VARIABLE = value_of_variable
print-%  : ; @echo $* = $($*)

# jar-file seperator - I can make this more specific for e.g. mac vs linux
ifeq ($(OS),Windows_NT)
SEP = ;
else
SEP = :
endif

# Handy for Cliff, $DESK/xvm
# Forward slash instead of backslash for 
DESK2 = $(subst \,/,$(DESK))

# XVM project root
XVM_ROOT = $(DESK2)/xvm

# Find a reasonable ctags.
CTAGS = $(shell which ctags)
# Hack for MacOS: /usr/bin/ctags is unfriendly, so look for ctags from brew
ifeq ($(UNAME),Darwin)
	CTAGS = $(shell brew list ctags 2> /dev/null | grep bin/ctags)
endif

# Fun Args to javac.
JAVAC_ARGS = -source 21 -target 21 -XDignore.symbol.file -Xlint:all -Xlint:-this-escape -Xlint:-unchecked -Xlint:-preview -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes

# Source code
# Paths relative to $(XVM_ROOT)
XEC := org/xvm
SRC := javatools_backend/src/main/java
TST := javatools_backend/src/test/java
CLZDIR:= build/classes/java
main_javas   := $(wildcard $(SRC)/$(XEC)/*java $(SRC)/$(XEC)/*/*java $(SRC)/$(XEC)/*/*/*java $(SRC)/$(XEC)/xec/*/*/*java)
test_javas   := $(wildcard $(TST)/$(XEC)/*java $(TST)/$(XEC)/*/*java $(TST)/$(XEC)/*/*/*java)
main_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_javas))
test_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_javas))
classes = $(main_classes) $(test_classes)


default:	$(classes)

# Compile just the out-of-date files
$(main_classes): $(CLZDIR)/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main $(main_javas)

$(test_classes): $(CLZDIR)/test/%class: $(TST)/%java $(main_classes)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test $(test_javas)

JVM=java -ea -cp "$(CLZDIR)/main"

# XDK setup
XDK_DIR = xdk/build/install/xdk
XDK = $(XDK_DIR)/javatools/javatools.jar
XTC = java -jar $(XDK) xcc -L $(XDK_DIR)/javatools -L $(XDK_DIR)/lib --rebuild
XEC = $(JVM) org.xvm.XEC -L $(XDK_DIR)/lib
COM = java -jar $(XDK) xec -L $(XDK_DIR)/javatools -L $(XDK_DIR)/lib


# General recipe for making an XTC from a X file
%.xtc:	%.x $(XDK)
	@echo "compiling " $@ " because " $?
	@$(XTC) $< -o $@

# General recipe for executing an XTC, by making an "EXE" file from an XTC -
# since no "EXE" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.exe:	%.xtc $(classes) $(XDK)
	@echo "running " $@
	@$(XEC) $< $(ARG)

# General recpie for executing an XTC with the existing interpreter-based backend.
# Since no "EXE" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.com:	%.xtc $(XDK)
	@echo "running " $@
	@$(COM) $< $(ARG)

# Build examples, .xtc from .x
# Assuming a minor version change in XTC, rebuild XTC examples
examples_x = $(wildcard $(XVM_ROOT)/doc/examples/*.x)

examples_xtc:	$(examples_x:x=xtc) $(XDK)

examples_exe:	$(examples_x:x=exe) $(classes) 


# Build TCK
TCK = tck/src/main/x

tck:	$(TCK)/tck.exe 

# Manual tests use an explicit list
MANUAL_DIR = manualTests/src/main/x
#MANUAL_TESTS = annos.x array.x collections.x defasn.x exceptions.x generics.x innerOuter.x files.x IO.x lambda.x loop.x nesting.x numbers.x prop.x maps.x queues.x services.x reflect.x regex.x tuple.x TestMisc.x TestModIFace.x
MANUAL_TESTS = TestMisc.x TestModIFace.x

manuals_x   = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MANUAL_TESTS))

manuals_xtc:	$(manuals_x:x=xtc) $(XDK)

manuals_exe:	$(manuals_x:x=exe) $(classes)


# General recipe for making a make-depend file from an XTC file
%.d:	%.xtc
	@rm -f $@
	@echo -ne $@ $*.xtc ":\t" > $@
	@([ $* ] && /usr/bin/find $* -name *.x | xargs echo) >> $@

# Pick up any make-depends files for each desired XTC file.
# Useful to pick up updates in top-level XTC modules from deep child X files.
ifeq (,$(filter clean tags,$(MAKECMDGOALS)))
include $(MAKECMDGOALS:.xtc=.d)
endif


#MULTI = multiModule/Lib.x multiModule/Main.x
#multi_x = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MULTI))
#$(multi_x:x=xtc): $(MANUAL_DIR)/%.xtc: $(MANUAL_DIR)/%.x $(XDK)
#	@echo "compiling " $@ " because " $?
#	@$(XTC) $(filter-out $(XDK),$^) -L $(MANUAL_DIR)/multiModule -o $(MANUAL_DIR)/multiModule
#
#
#multi_exe:	$(XDK) $(classes) $(multi_x:x=xtc)
#	@echo "Running test" $?
#	@$(XEC) -L $(MANUAL_DIR)/multiModule $(MANUAL_DIR)/multiModule/Main.xtc


# TAGS
tags:	TAGS

TAGS:	$(main_javas) $(test_javas)
	@rm -f TAGS
	@$(CTAGS) -o TAGS -e --recurse=yes --extra=+q --fields=+fksaiS $(SRC) $(TST)

.PHONY: clean
clean:
	rm -rf build
	rm -rf out
	rm -f TAGS
	rm -f $(XVM_ROOT)/tck/src/main/x/*.xtc
	rm -f $(XVM_ROOT)/doc/examples/*.xtc
	rm -f $(XVM_ROOT)/manualTests/src/main/x/*.xtc $(XVM_ROOT)/manualTests/src/main/x/*/*.xtc
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)
