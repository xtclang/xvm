# supported values are: 'linux', 'macos' and 'windows'
OS_NAME ?= macos
#OS_NAME ?= linux
#OS_NAME ?= windows

# compile settings
CMP_EXEC ?= gcc
CMP_OPTS ?= -O0

# result executable name
EXE_NAME ?= $(OS_NAME)_launcher

# windows resource linking
RC_FILE  ?= resources/resources.rc
RES_FILE ?= resources.res
ifeq ($(OS_NAME),windows)
	RESOURCES_TARGET = resfile
endif

all: compile

compile: $(RESOURCES_TARGET)
	$(CMP_EXEC) -g -Wall -std=gnu11 -D$(OS_NAME)Launcher $(CMP_OPTS) \
		launcher.c \
		os_$(OS_NAME).c \
		-o $(EXE_NAME)

clean:
	rm $(EXE_NAME) 2>/dev/null || true
ifeq ($(OS_NAME),windows)
	rm $(RES_FILE) 2>/dev/null || true
endif

vg:
	valgrind --track-origins=yes \
		--leak-check=full \
		--leak-check=full \
		--show-reachable=yes \
		$(EXE_NAME)

run:
	./$(EXE_NAME)

# windows resource linking
resfile:
	windres $(RC_FILE) --output-format coff --output $(RES_FILE)
