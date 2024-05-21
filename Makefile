#
# Makefile expects to run at the project root, and not nested inside anywhere
#
#  cd $DESK/xvm; make

# macOS specific notes:
#
# install necessary packages using brew:
#
#   brew install wget
#   brew install make
#   brew install bash
#
# as of April 2024:
#
#   ~$ wget --version
#   GNU Wget 1.24.5 built on darwin21.6.0.
#
#   xvm$ gmake -version
#   GNU Make 4.4.1
#   Built for aarch64-apple-darwin23.0.0
#   Copyright (C) 1988-2023 Free Software Foundation, Inc.
#   License GPLv3+: GNU GPL version 3 or later <https://gnu.org/licenses/gpl.html>
#   This is free software: you are free to change and redistribute it.
#   There is NO WARRANTY, to the extent permitted by law.
#
#   ~$ bash -version
#   GNU bash, version 3.2.57(1)-release (x86_64-apple-darwin21)
#   Copyright (C) 2007 Free Software Foundation, Inc.
#
# note that brew installs GNU make as the "gmake" command, to avoid conflicting with the ancient
# version of make that comes with macOS xcode.
#
# first time only - this is the only thing that hits the Interwebs
#
#   gmake init
#
# any time you want to nuke everything from space:
#
#   gmake clean
#
# run a test
# (do this twice, since the first run may have to compile any dependencies ... same thing goes for
# any timing examples)
#
#   time gmake doc/examples/OneHundredPrisoners.exe
#   time gmake doc/examples/OneHundredPrisoners.exe
#
# compare that to the interpreter (by replacing the pretend extension .exe with .com)
#
#   time gmake doc/examples/OneHundredPrisoners.com
#   time gmake doc/examples/OneHundredPrisoners.com
#
# run the tck (but only after commenting out the last 4 tests in tck.x -- known TODO for Cliff)
#
#   time gmake tck/src/main/x/tck.exe
#
# compare it to the interpreter (by replacing .exe with .com)
#
#   time gmake tck/src/main/x/tck.com

#######################################################
#
# Boilerplate because make syntax isn't the best
#

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

MAKE_MIN_VERSION = 4.4.0
ifneq "$(MAKE_MIN_VERSION)" "$(firstword $(sort $(MAKE_VERSION) $(MAKE_MIN_VERSION)))"
  $(error make version is $(MAKE_VERSION), but needs to be version $(MAKE_MIN_VERSION) or later)
endif


# Fun Args to javac.
JAVAC_ARGS = --release 21 -XDignore.symbol.file -Xlint:-deprecation

XEC := org/xvm

#######################################################
default:
	@echo "Need to pass some make options, here are some suggestions"
	@echo "  init - one-time init; requires a good internet connection"
	@echo "  build/xdk/javatools/javatools.jar"
	@echo "  xlib  - all lib_*.xtc libraries"
	@echo "  *.xtc - will build from the matching *.x"
	@echo "  *.exe - will execute new backend from the matching *.x"
	@echo "  *.com - will execute old backend from the matching *.x"
	@echo "  tck          - run the tcks with new backend"
	@echo "  examples_exe - run the doc/examples with new backend"
	@echo "  manuals_exe  - run the manualTests/src/main/x/new_backend"

#######################################################
# Download libs from maven
# One time, per installation, with a good internet connection
#
init:	lib

LIBS := build/lib
lib:	$(LIBS)/junit-jupiter-api-5.10.2.jar $(LIBS)/apiguardian-api-1.1.2.jar $(LIBS)/jline-3.25.1.jar

# Unit testing
$(LIBS)/junit-jupiter-api-5.10.2.jar:
	@[ -d $(LIBS) ] || mkdir -p $(LIBS)
	@(cd $(LIBS); wget https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar)

$(LIBS)/apiguardian-api-1.1.2.jar:
	@[ -d $(LIBS) ] || mkdir -p $(LIBS)
	@(cd $(LIBS); wget https://repo1.maven.org/maven2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar)

$(LIBS)/jline-3.25.1.jar:
	@[ -d $(LIBS) ] || mkdir -p $(LIBS)
	@(cd $(LIBS); wget https://repo1.maven.org/maven2/org/jline/jline/3.25.1/jline-3.25.1.jar)

libs = $(wildcard $(LIBS)/*jar)



#######################################################
# javatools_backend
SRCB := javatools_backend/src/main/java
CLZB:= build/classes/backend
javasB := $(wildcard $(SRCB)/$(XEC)/*java $(SRCB)/$(XEC)/*/*java $(SRCB)/$(XEC)/*/*/*java $(SRCB)/$(XEC)/xec/*/*/*java)
clzesB := $(patsubst $(SRCB)/%java,$(CLZB)/main/%class,$(javasB))

OODB :=
$(CLZB)/.tag: $(clzesB) $(javasB)
	@[ -d $(CLZB)/main ] || mkdir -p $(CLZB)/main
	$(file > .argsB.txt, $(OODB))
	@if [ ! -z "$(OODB)" ] ; then \
	  echo "compiling backend because " $< " and " `wc -w < .argsB.txt` " more files" ; \
	  javac $(JAVAC_ARGS) -cp "$(CLZB)/main$(SEP)$(LIBS)" -sourcepath $(SRCB) -d $(CLZB)/main $(OODB) ; \
	fi
	@touch $(CLZB)/.tag
	@rm -f .argsB.txt

# Collect just the out-of-date files
$(clzesB): $(CLZB)/main/%class: $(SRCB)/%java
	$(eval OODB += $$<)

#######################################################
# javatools_utils
SRCU := javatools_utils/src/main/java
CLZU := build/classes/utils
javasU := $(wildcard $(SRCU)/$(XEC)/*java $(SRCU)/$(XEC)/*/*java $(SRCU)/$(XEC)/*/*/*java)
clzesU := $(patsubst $(SRCU)/%java,$(CLZU)/main/%class,$(javasU))

OODU :=
$(CLZU)/.tag: $(clzesU) $(javasU)
	@[ -d $(CLZU)/main ] || mkdir -p $(CLZU)/main
	$(file > .argsU.txt, $(OODU))
	@if [ ! -z "$(OODU)" ] ; then \
	  echo "compiling javatools_utils because " $< " and " `wc -w < .argsU.txt` " more files" ; \
	  javac $(JAVAC_ARGS) -cp "$(CLZU)/main$(SEP)$(LIBS)" -sourcepath $(SRCU) -d $(CLZU)/main $(OODU) ; \
	fi
	@touch $(CLZU)/.tag
	@rm -f .argsU.txt

# Collect just the out-of-date files
$(clzesU): $(CLZU)/main/%class: $(SRCU)/%java
	$(eval OODU += $$<)

#######################################################
# javatools
SRCT := javatools/src/main/java
CLZT:= build/classes/javatools
javasT := $(wildcard $(SRCT)/$(XEC)/*java $(SRCT)/$(XEC)/*/*java $(SRCT)/$(XEC)/*/*/*java $(SRCT)/$(XEC)/*/*/*/*java)
clzesT := $(patsubst $(SRCT)/%java,$(CLZT)/main/%class,$(javasT))

OODT :=
$(CLZT)/.tag: $(clzesT)  $(javasT) $(CLZU)/.tag
	@[ -d $(CLZT)/main ] || mkdir -p $(CLZT)/main
	$(file > .argsT.txt, $(OODT))
	@if [ ! -z "$(OODT)" ] ; then \
	  echo "compiling javatools because " $< " and " `wc -w < .argsT.txt` " more files" ; \
	  javac $(JAVAC_ARGS) -cp "$(CLZT)/main$(SEP)$(CLZU)/main$(SEP)$(LIBS)/*" -sourcepath $(SRCT) -d $(CLZT)/main @.argsT.txt ; \
	fi
	@touch $(CLZT)/.tag
	@rm -f .argsT.txt

# Collect just the out-of-date files
$(clzesT): $(CLZT)/main/%class: $(SRCT)/%java
	$(eval OODT += $$<)


#######################################################
# Build a jar from all the class files in $(CLZT)/main and $(CLZU)/main

# XDK setup
# Gradle XDK
#XDK_DIR = xdk/build/install/xdk
# Make XDK
XDK_DIR = build/xdk
XDK_JAR = $(XDK_DIR)/javatools/javatools.jar
XDK_LIB = $(XDK_DIR)/lib

$(XDK_JAR): $(clzesT) $(clzesU) $(CLZT)/.tag $(CLZU)/.tag $(XDK_DIR)/MANIFEST.MF javatools/src/main/resources/errors.properties lib_ecstasy/src/main/resources/implicit.x
	@$(file > .args.txt, $? )
	@echo "  jarring " $@ " because " $< " and " `wc -w < .args.txt` " more files"
	@rm -f .args.txt
	@jar -cfm $@ $(XDK_DIR)/MANIFEST.MF -C $(CLZT)/main . -C $(CLZU)/main . -C javatools/src/main/resources errors.properties -C lib_ecstasy/src/main/resources implicit.x

# Build the manifest
$(XDK_DIR)/MANIFEST.MF: VERSION
	@[ -d $(XDK_DIR) ] || mkdir -p $(XDK_DIR)
	@echo Manifest-Version: 1.0 > $@
	@echo Xdk-Version: org.xtclang:javatools:`cat VERSION` >> $@
	@echo Sealed: true  >> $@
	@echo Main-Class: org.xvm.tool.Launcher  >> $@
	@echo Name: /org/xvm/  >> $@
	@echo Specification-Title: xvm  >> $@
	@echo Specification-Version: `cat VERSION` >> $@
	@echo Specification-Vendor: xtclang.org  >> $@
	@echo Implementation-Title: xvm-prototype >> $@
	@echo Implementation-Version: `cat VERSION`  >> $@
	@echo Implementation-Vendor: xtclang.org  >> $@


#######################################################
# Build the library XDK.

XCC = java -jar $(XDK_JAR) xcc -L $(XDK_DIR)/javatools -L $(XDK_LIB) --rebuild

# Build ecstasy.xtc.  This one is special, because it needs mack.x and makes a turtle.xtc.
# the make-depend .d file is next to the generated XTC instead of next to the sources.
SRCX = lib_ecstasy/src/main/x/ecstasy
MACK = javatools_turtle/src/main/resources/mack
TURTLE = $(XDK_DIR)/javatools/javatools_turtle.xtc
XDKX = $(XDK_LIB)/ecstasy
$(XDKX).xtc $(TURTLE):	$(SRCX).x $(XDKX).d $(MACK).x
	@echo "compiling " $@ " because " $?
	@[ -d $(XDK_LIB) ] || mkdir -p $(XDK_LIB)
	@javatools_backend/bin/makedepends.sh $(SRCX) $(XDKX)
	@java -jar $(XDK_JAR) xcc -L $(XDK_DIR)/javatools -L $(XDK_LIB) --rebuild $(SRCX).x $(MACK).x
	@mv $(MACK).xtc $(TURTLE)
	@mv lib_ecstasy/build/ecstasy.xtc $(XDKX).xtc

SRCCRY = lib_crypto/src/main/x/crypto
LIBCRY = $(XDK_LIB)/crypto
$(LIBCRY).xtc:	$(SRCCRY).x $(LIBCRY).d $(XDK_JAR) $(XDKX).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCCRY) $(LIBCRY)
	@$(XCC) $< -o $@

SRCNET = lib_net/src/main/x/net
LIBNET = $(XDK_LIB)/net
$(LIBNET).xtc:	$(SRCNET).x $(LIBNET).d $(XDK_JAR) $(LIBCRY).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCNET) $(LIBNET)
	@$(XCC) $< -o $@


SRCAGG = lib_aggregate/src/main/x/aggregate
LIBAGG = $(XDK_LIB)/aggregate
$(LIBAGG).xtc:	$(SRCAGG).x $(LIBAGG).d $(XDK_JAR) $(XDKX).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCAGG) $(LIBAGG)
	@$(XCC) $< -o $@

SRCCOL = lib_collections/src/main/x/collections
LIBCOL = $(XDK_LIB)/collections
$(LIBCOL).xtc:	$(SRCCOL).x $(LIBCOL).d $(XDK_JAR) $(XDKX).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCCOL) $(LIBCOL)
	@$(XCC) $< -o $@

SRCJSN = lib_json/src/main/x/json
LIBJSN = $(XDK_LIB)/json
$(LIBJSN).xtc:	$(SRCJSN).x $(LIBJSN).d $(XDK_JAR) $(XDKX).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCJSN) $(LIBJSN)
	@$(XCC) $< -o $@


SRCWEB = lib_web/src/main/x/web
LIBWEB = $(XDK_LIB)/web
$(LIBWEB).xtc:	$(SRCWEB).x $(LIBWEB).d $(XDK_JAR) $(XDKX).xtc $(LIBAGG).xtc $(LIBCOL).xtc $(LIBCRY).xtc $(LIBJSN).xtc $(LIBNET).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCWEB) $(LIBWEB)
	@$(XCC) $< -o $@

SRCNAT = javatools_bridge/src/main/x/_native
LIBNAT = $(XDK_DIR)/javatools/javatools_bridge
$(LIBNAT).xtc:	$(SRCNAT).x $(LIBNAT).d $(XDK_JAR) $(XDKX).xtc $(LIBCRY).xtc $(LIBNET).xtc $(LIBWEB).xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $(SRCNAT) $(LIBNAT)
	@$(XCC) $< -o $@


# All the core libs
XLIB = $(XDKX).xtc $(LIBCRY).xtc $(LIBNET).xtc $(LIBAGG).xtc $(LIBCOL).xtc $(LIBJSN).xtc $(LIBWEB).xtc $(LIBNAT).xtc
xlib:	$(XLIB)

include $(XLIB:.xtc=.d)


# General recipe for making an XTC from a X file
# Automatic X-file dependency generation; .d file is next to both the XTC and sources.
%.xtc:	%.x %.d $(XDK_JAR) $(XDK_LIB)/ecstasy.xtc
	@echo "compiling " $@ " because " $?
	@javatools_backend/bin/makedepends.sh $* $*
	@$(XCC) $< -o $@

# No complaints if these do not exist, just go make them
%.d:	;


#######################################################
# Running XTC files via either the old or new backends
#
# General recipe for executing an XTC, by making an "EXE" file from an XTC -
# since no "EXE" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.exe:	%.xtc $(clzesB) $(CLZB)/.tag $(XDKX).xtc
	@echo "  running " $@
	@java -ea -cp "$(CLZB)/main" org.xvm.XEC -L $(XDK_LIB) $< $(ARG)

# General recipe for executing an XTC with the existing interpreter-based backend.
# Since no "COM" is ever made, this just always runs the module.
# Additional arguments can be passed from the command line via "ARG=arg"
%.com:	%.xtc $(XDK_JAR) $(XDKX).xtc $(LIBNAT).xtc
	@echo "  running " $@
	@java -cp "$(XDK_JAR)$(SEP)$(LIBS)/jline-3.25.1.jar" org.xvm.tool.Launcher xec -L $(XDK_DIR)/javatools -L $(XDK_LIB) $< $(ARG)


#######################################################

# Pick up any make-depends files for each desired XTC file.
# Useful to pick up updates in top-level XTC modules from deep child X files.
ifeq (,$(filter clean tags,$(MAKECMDGOALS)))
MAKE_DEPS = $(filter %.d,$(sort $(MAKECMDGOALS:.xtc=.d) $(MAKECMDGOALS:.exe=.d) $(MAKECMDGOALS:.com=.d)))
include $(MAKE_DEPS)
endif


#######################################################
# Common build targets when testing the new backend
#
examples_x = $(wildcard doc/examples/*.x)

examples_xtc:	$(examples_x:x=xtc) $(XDK_JAR)

examples_exe:	$(examples_x:x=exe) $(clazesB)


# Build TCK
.PHONY:	tck
tck:	tck/src/main/x/tck.exe

# Manual tests use an explicit list
MANUAL_DIR = manualTests/src/main/x/new_backend
#MANUAL_TESTS = annos.x array.x collections.x defasn.x exceptions.x generics.x innerOuter.x files.x IO.x lambda.x loop.x nesting.x numbers.x prop.x maps.x queues.x services.x reflect.x regex.x tuple.x TestMisc.x TestModIFace.x
MANUAL_TESTS = TestMisc.x TestModIFace.x

manuals_x   = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MANUAL_TESTS))

manuals_xtc:	$(manuals_x:x=xtc) $(XDK_JAR)

manuals_exe:	$(manuals_x:x=exe) $(classesB)


#MULTI = multiModule/Lib.x multiModule/Main.x
#multi_x = $(patsubst %.x,$(MANUAL_DIR)/%.x,$(MULTI))
#$(multi_x:x=xtc): $(MANUAL_DIR)/%.xtc: $(MANUAL_DIR)/%.x $(XDK_JAR)
#	@echo "compiling " $@ " because " $?
#	@$(XCC) $(filter-out $(XDK_JAR),$^) -L $(MANUAL_DIR)/multiModule -o $(MANUAL_DIR)/multiModule
#
#
#multi_exe:	$(XDK_JAR) $(classesB) $(multi_x:x=xtc)
#	@echo "Running test" $?
#	@$(JVM) org.xvm.XEC -L $(XDK_LIB) -L $(MANUAL_DIR)/multiModule $(MANUAL_DIR)/multiModule/Main.xtc


# TAGS
tags:	TAGS

TAGS:	$(javasB) $(javasT) $(javasU)
	@rm -f TAGS
	@$(CTAGS) -o TAGS -e --recurse=yes --extra=+q --fields=+fksaiS $(SRCB) $(SRCT)

.PHONY: clean
clean:
	rm -rf build/classes
	rm -rf build/xdk
	rm -rf out
	rm -f TAGS
	rm -f tck/src/main/x/*.xtc
	rm -f doc/examples/*.xtc
	rm -f manualTests/src/main/x/new_backend/*.xtc manualTests/src/main/x/new_backend/*/*.xtc
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)
