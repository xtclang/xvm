# Are your paths correct?

# This Makefile is deeply nested inside a larger project and uses internal
# relative paths to avoid disturbing the larger project.
#
#  cd $DESK/xvm/javatools; make -f src/main/java/org/xvm/cc_explore/Makefile test
#


SHELL := /bin/bash
.DELETE_ON_ERROR:

DATE=`date +%Y%m%d`

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


default_targets := test

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

test:	$(classes) $(test_classes) tags
	$(JVM) org.xvm.cc_explore.XEC "../manualTests/build/TestLiterals.xtc"

tags:	$(main_javas) $(test_javas)
	@rm -f ../TAGS
	@$(CTAGS) -o ../TAGS -e --recurse=yes --extra=+q --fields=+fksaiS $(SRC) $(TST)

.PHONY: clean
clean:
	rm -rf build
	rm -rf out
	rm -f TAGS
	(find . -name "*~" -exec rm {} \; 2>/dev/null; exit 0)
