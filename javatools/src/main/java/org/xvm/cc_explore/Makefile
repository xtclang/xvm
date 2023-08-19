# Are your paths correct?

# This Makefile is deeply nested inside a larger project and uses internal
# relative paths to avoid disturbing the larger project.
#
#  cd $DESK/xvm/javatools; make -f src/main/java/org/xvm/cc_explore/Makefile test
#


SHELL := /bin/bash
.DELETE_ON_ERROR:

# for printing variable values
# usage: make print-VARIABLE
#        > VARIABLE = value_of_variable
print-%  : ; @echo $* = $($*)

# literal space
space := $() $()

# Decide OS-specific questions
# jar-file seperator
ifeq ($(OS),Windows_NT)
	SEP = ;
else
# linux
	UNAME = $(shell uname)
	ifeq ($(UNAME),Darwin)
		SEP = :
	endif
	ifeq ($(UNAME),Linux)
		SEP = :
	endif
endif
# Forward slash instead of backslash for 
DESK2 = $(subst \,/,$(DESK))
XVM_ROOT = $(DESK2)/xvm
# Find a reasonable ctags.
CTAGS = $(shell which ctags)
# Hack for MacOS: /usr/bin/ctags is unfriendly, so look for ctags from brew
ifeq ($(UNAME),Darwin)
	CTAGS = $(shell brew list ctags 2> /dev/null | grep bin/ctags)
endif

# Fun Args to javac.  Mostly limit to java8 source definitions, and fairly
# aggressive lint warnings.
JAVAC_ARGS = -g --enable-preview -source 17 -target 17 -XDignore.symbol.file -Xlint:all -Xlint:-unchecked -Xlint:-preview -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes

# Source code
# Note that BuildVersion is not forced to be rebuilt here - so incremental
# makes in this directory will endlessly use the same BuildVersion.
# Paths relative to .../xvm/javatools
XEC := org/xvm/cc_explore
SRC := src/main/java
TST := src/test/java
CLZDIR:= build/classes/java
main_javas   := $(wildcard $(SRC)/$(XEC)/*java $(SRC)/$(XEC)/*/*java)
test_javas   := $(wildcard $(TST)/$(XEC)/*java $(TST)/$(XEC)/*/*java)
main_classes := $(patsubst $(SRC)/%java,$(CLZDIR)/main/%class,$(main_javas))
test_classes := $(patsubst $(TST)/%java,$(CLZDIR)/test/%class,$(test_javas))
classes = $(main_classes) $(test_classes)


default:	classes tags

# Just the classes, no jarring step
classes: $(classes)

# Build the test classes
tests:	$(test_classes)

# Compile just the out-of-date files
$(main_classes): $(CLZDIR)/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/main ] || mkdir -p $(CLZDIR)/main
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(SRC) -d $(CLZDIR)/main $(main_javas)

$(test_classes): $(CLZDIR)/test/%class: $(TST)/%java $(main_classes)
	@echo "compiling " $@ " because " $?
	@[ -d $(CLZDIR)/test ] || mkdir -p $(CLZDIR)/test
	@javac $(JAVAC_ARGS) -cp "$(CLZDIR)/test$(SEP)$(CLZDIR)/main$(SEP)$(jars)" -sourcepath $(TST) -d $(CLZDIR)/test $(test_javas)

# Other Resources in aa.jar:
JARBITS =
JARBITS += -C $(CLZDIR)/main .    # The java class files

JVM=nice java -Xms1g -Xms1g -ea -cp "$(CLZDIR)/main"

test:	$(classes) $(test_classes)
	$(JVM) --enable-preview org.xvm.cc_explore.XEC -L ../xdk/build/xdk/lib -L ../xdk/build/xdk/javatools/ ../doc/examples/FizzBuzz.xtc 123


# Assuming a minor version change in XTC, install a new XTC on windows
XTC = $(XVM_ROOT)/xdk/build/xdk/bin/xtc.exe

install_xtc:	$(XTC)

$(XTC):	$(XVM_ROOT)/xdk/build/xdk/bin/windows_launcher.exe
	cp $(XVM_ROOT)/xdk/build/xdk/bin/windows_launcher.exe $(XTC)


# Build examples, .xtc from .x
# Assuming a minor version change in XTC, rebuild XTC examples
EXM = $(XVM_ROOT)/doc/examples
examples_x = $(wildcard $(EXM)/*.x)

examples:	$(examples_x:x=xtc) $(XTC)

$(examples_x:x=xtc): $(EXM)/%.xtc: $(EXM)/%.x
	@echo "compiling " $@ " because " $?
	@$(XTC) $? -o $(EXM)


tags:	../TAGS

../TAGS:	$(main_javas) $(test_javas)
	@rm -f ../TAGS
	@$(CTAGS) -o ../TAGS -e --recurse=yes --extra=+q --fields=+fksaiS $(SRC) $(TST)

.PHONY: clean
clean:
	rm -rf build
	rm -rf out
	rm -f TAGS
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)
