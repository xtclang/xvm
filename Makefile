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

# literal space
space := $() $()

# jar-file seperator - I can make this more specific for e.g. mac vs linux
ifeq ($(OS),Windows_NT)
SEP = ;
else
SEP = :
endif

# Find a reasonable ctags.
CTAGS = $(shell which ctags)
# Hack for MacOS: /usr/bin/ctags is unfriendly, so look for ctags from brew
ifeq ($(UNAME),Darwin)
	CTAGS = $(shell brew list ctags 2> /dev/null | grep bin/ctags)
endif

# Fun Args to javac.
JAVAC_ARGS = -source 21 -target 21 -XDignore.symbol.file -Xlint:-deprecation

XEC := org/xvm

default:
	@echo "Need to pass some make options"
	@echo "  init - one-time init; requires a good internet connection"
	@echo "  build/classes/javatools/javatools.jar"
	@echo "  *.xtc - will build from the matching *.x"
	@echo "  *.exe - will execute new backend from the matching *.x"
	@echo "  *.com - will execute old backend from the matching *.x"

#######################################################
# Download libs from maven
# One time, per installation, with a good internet connection
#
init:	lib

LIBS := build/lib
lib:	$(LIBS)/junit-jupiter-api-5.10.2.jar $(LIBS)/apiguardian-api-1.1.2.jar

# Unit testing
$(LIBS)/junit-jupiter-api-5.10.2.jar:
	@[ -d $(LIBS) ] || mkdir -p $(LIBS)
	@(cd $(LIBS); wget https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar)

$(LIBS)/apiguardian-api-1.1.2.jar:
	@[ -d $(LIBS) ] || mkdir -p $(LIBS)
	@(cd $(LIBS); wget https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar)

libs = $(wildcard $(LIBS)/*jar)
jars = $(subst $(space),$(SEP),$(libs))



#######################################################
# New Backend
# javatools_backend
# Source code, relative paths
SRCB := javatools_backend/src/main/java
CLZDIRB:= build/classes/backend
main_javasB   := $(wildcard $(SRCB)/$(XEC)/*java $(SRCB)/$(XEC)/*/*java $(SRCB)/$(XEC)/*/*/*java $(SRCB)/$(XEC)/xec/*/*/*java)
main_classesB := $(patsubst $(SRCB)/%java,$(CLZDIRB)/main/%class,$(main_javasB))
classes = $(main_classesB)

# Compile just the out-of-date files
$(main_classesB): $(CLZDIRB)/main/%class: $(SRCB)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIRB)/main ] || mkdir -p $(CLZDIRB)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIRB)/main$(SEP)$(LIBS)" -sourcepath $(SRCB) -d $(CLZDIRB)/main $(main_javasB)


#######################################################
# javatools_utils
# Source code, relative paths
SRCU := javatools_utils/src/main/java
TSTU := javatools_utils/src/test/java
CLZDIRU:= build/classes/utils
main_javasU   := $(wildcard $(SRCU)/$(XEC)/*java $(SRCU)/$(XEC)/*/*java $(SRCU)/$(XEC)/*/*/*java)
test_javasU   := $(wildcard $(TSTU)/$(XEC)/*java $(TSTU)/$(XEC)/*/*java $(TSTU)/$(XEC)/*/*/*java)
main_classesU := $(patsubst $(SRCU)/%java,$(CLZDIRU)/main/%class,$(main_javasU))
test_classesU := $(patsubst $(TSTU)/%java,$(CLZDIRU)/test/%class,$(test_javasU))

# Compile just the out-of-date files
$(main_classesU): $(CLZDIRU)/main/%class: $(SRCU)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIRU)/main ] || mkdir -p $(CLZDIRU)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIRU)/main$(SEP)$(LIBS)" -sourcepath $(SRCU) -d $(CLZDIRU)/main $(main_javasU)

$(test_classesU): $(CLZDIRU)/test/%class: $(TSTU)/%java $(main_classesU)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIRU)/test ] || mkdir -p $(CLZDIRU)/test
	@javac $(JAVAC_ARGS) -cp "$(CLZDIRU)/test$(SEP)$(CLZDIRU)/main$(SEP)$(LIBS)" -sourcepath $(TSTU) -d $(CLZDIRU)/test $(test_javasU)



#######################################################
# javatools_launcher
# Source code, relative paths
SRCT := javatools/src/main/java
TSTT := javatools/src/test/java
CLZDIRT:= build/classes/javatools
main_javasT   := $(wildcard $(SRCT)/$(XEC)/*java $(SRCT)/$(XEC)/*/*java $(SRCT)/$(XEC)/*/*/*java)
test_javasT   := $(wildcard $(TSTT)/$(XEC)/*java $(TSTT)/$(XEC)/*/*java $(TSTT)/$(XEC)/*/*/*java)
main_classesT := $(patsubst $(SRCT)/%java,$(CLZDIRT)/main/%class,$(main_javasT))
test_classesT := $(patsubst $(TSTT)/%java,$(CLZDIRT)/test/%class,$(test_javasT))

# Compile just the out-of-date files
$(main_classesT): $(CLZDIRT)/main/%class: $(SRCT)/%java $(main_classesU)
	$(file > .args.txt, $(filter-out %class, $?))
	@echo -ne "compiling " $@ " because "; cat .args.txt
	@[ -d $(CLZDIRT)/main ] || mkdir -p $(CLZDIRT)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIRT)/main$(SEP)$(CLZDIRU)/main$(SEP)$(LIBS)" -sourcepath $(SRCT) -d $(CLZDIRT)/main @.args.txt
	@rm -f .args.txt

$(test_classesT): $(CLZDIRT)/test/%class: $(TSTT)/%java $(main_classesT)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIRT)/test ] || mkdir -p $(CLZDIRT)/test
	@javac $(JAVAC_ARGS) -cp "$(CLZDIRT)/test$(SEP)$(CLZDIRT)/main$(SEP)$(LIBS)" -sourcepath $(TSTT) -d $(CLZDIRT)/test $(test_javasT)

# Build a jar from all the class files in $(CLZDIRT)/main and $(CLZDIRU)/main
build/classes/javatools/javatools.jar: $(main_classesT) $(main_classesU) javatools_backend/MANIFEST.MF javatools_backend/errors.properties
	@$(file > .args.txt, $? )
	@echo -ne "  jarring " $@ " because "; cat .args.txt
	@rm -f .args.txt
	@jar -cfm $@ javatools_backend/MANIFEST.MF -C $(CLZDIRT)/main . -C $(CLZDIRU)/main . -C javatools_backend errors.properties



#######################################################
# Compiling XTC files from X files via XCC
JVM=java -ea -cp "$(CLZDIRB)/main"

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


#######################################################
# Running XTC files via either the old or new backends
#
# General recipe for executing an XTC, by making an "EXE" file from an XTC -
# since no "EXE" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.exe:	%.xtc $(main_classesB) $(XDK)
	@echo "running " $@
	@$(XEC) $< $(ARG)

# General recpie for executing an XTC with the existing interpreter-based backend.
# Since no "EXE" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.com:	%.xtc $(XDK)
	@echo "running " $@
	@$(COM) $< $(ARG)

#######################################################
# Common build targets when testing the new backend
#
examples_x = $(wildcard doc/examples/*.x)

examples_xtc:	$(examples_x:x=xtc) $(XDK)

examples_exe:	$(examples_x:x=exe) $(classesB)


# Build TCK
TCK = tck/src/main/x

tck:	$(TCK)/tck.exe

# Manual tests use an explicit list
MANUAL_DIR = manualTests/src/main/x/new_backend
#MANUAL_TESTS = annos.x array.x collections.x defasn.x exceptions.x generics.x innerOuter.x files.x IO.x lambda.x loop.x nesting.x numbers.x prop.x maps.x queues.x services.x reflect.x regex.x tuple.x TestMisc.x TestModIFace.x
MANUAL_TESTS = TestMisc.x TestModIFace.x

manuals_x   = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MANUAL_TESTS))

manuals_xtc:	$(manuals_x:x=xtc) $(XDK)

manuals_exe:	$(manuals_x:x=exe) $(classesB)


# General recipe for making a make-depend file from an XTC file
%.d:	%.xtc
	@rm -f $@
	@echo -ne $@ $*.xtc ":\t" > $@
	@([ $* ] && /usr/bin/find $* -name *.x | xargs echo) >> $@

## Pick up any make-depends files for each desired XTC file.
## Useful to pick up updates in top-level XTC modules from deep child X files.
#ifeq (,$(filter clean tags,$(MAKECMDGOALS)))
#qprint:
#	@echo $(filter-out clean tags,$(MAKECMDGOALS))
#ifeq (,$(MAKECMDGOALS:.xtc=.d) $(MAKECMDGOALS:.exe=.d))
#rprint:
#	@echo NOD $(MAKECMDGOALS:.xtc=.d) $(MAKECMDGOALS:.exe=.d)
#else
#sprint:
#	@echo HASD $(MAKECMDGOALS:.xtc=.d) $(MAKECMDGOALS:.exe=.d)
#include $(MAKECMDGOALS:.xtc=.d) $(MAKECMDGOALS:.exe=.d)
#endif
#else
#xprint:
#	@echo $(filter-out clean tags,$(MAKECMDGOALS))
#endif


#MULTI = multiModule/Lib.x multiModule/Main.x
#multi_x = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MULTI))
#$(multi_x:x=xtc): $(MANUAL_DIR)/%.xtc: $(MANUAL_DIR)/%.x $(XDK)
#	@echo "compiling " $@ " because " $?
#	@$(XTC) $(filter-out $(XDK),$^) -L $(MANUAL_DIR)/multiModule -o $(MANUAL_DIR)/multiModule
#
#
#multi_exe:	$(XDK) $(classesB) $(multi_x:x=xtc)
#	@echo "Running test" $?
#	@$(XEC) -L $(MANUAL_DIR)/multiModule $(MANUAL_DIR)/multiModule/Main.xtc


# TAGS
tags:	TAGS

TAGS:	$(main_javas) $(test_javas)
	@rm -f TAGS
	@$(CTAGS) -o TAGS -e --recurse=yes --extra=+q --fields=+fksaiS $(SRCB) $(SRCT)

.PHONY: clean
clean:
	rm -rf build/classes
	rm -rf out
	rm -f TAGS
	rm -f tck/src/main/x/*.xtc
	rm -f doc/examples/*.xtc
	rm -f manualTests/src/main/x/*.xtc $(XVM_ROOT)/manualTests/src/main/x/*/*.xtc
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)
