# supported values are: 'linux', 'macos' and 'windows'
OS_NAME ?= macos
#OS_NAME ?= linux
#OS_NAME ?= windows

# compile settings
ifeq ($(OS_NAME),macos)
	CMP_EXEC ?= cc
else
	CMP_EXEC ?= gcc
endif
CMP_OPTS ?= -O0

# extra source file for shared linux/unix code
ifneq ($(OS_NAME),windows)
    SHARED_IMPL = os_unux.c
endif

# result executable name
EXE_DIR  ?= ../../../build/exe/
EXE_NAME ?= $(EXE_DIR)$(OS_NAME)_launcher
EXE_X86   = $(EXE_DIR)$(OS_NAME)_x86
EXE_ARM   = $(EXE_DIR)$(OS_NAME)_arm

# windows resource linking
ifeq ($(OS_NAME),windows)
	RC_FILE  ?= resources.rc
	RES_FILE ?= $(EXE_DIR)resources.o
	RESOURCES_TARGET = resfile
endif

all: compile

compile: $(RESOURCES_TARGET)
ifeq ($(OS_NAME),macos)
	$(CMP_EXEC) \
		launcher.c      \
		os_$(OS_NAME).c \
		$(SHARED_IMPL)  \
		-o $(EXE_ARM)   \
		-target arm64-apple-macos11

	$(CMP_EXEC) \
		launcher.c      \
		os_$(OS_NAME).c \
		$(SHARED_IMPL)  \
		-o $(EXE_X86)   \
		-target x86_64-apple-macos10.12

	lipo -create -output $(EXE_NAME) $(EXE_ARM) $(EXE_X86)
else
	$(CMP_EXEC) -g -Wall -std=gnu11 -D$(OS_NAME)Launcher $(CMP_OPTS) \
		launcher.c      \
		os_$(OS_NAME).c \
		$(SHARED_IMPL)  \
		$(RES_FILE)     \
		-o $(EXE_NAME)
endif

clean:
	rm $(EXE_NAME) 2>/dev/null || true
ifeq ($(OS_NAME),macos)
	rm $(EXE_X86) 2>/dev/null || true
	rm $(EXE_ARM) 2>/dev/null || true
endif
ifeq ($(OS_NAME),windows)
	rm $(RES_FILE) 2>/dev/null || true
endif

# vg:
# 	valgrind --track-origins=yes \
# 		--leak-check=full \
# 		--leak-check=full \
# 		--show-reachable=yes \
# 		$(EXE_NAME)
#
# run:
# 	./$(EXE_NAME)

# windows resource linking
resfile:
	windres $(RC_FILE) --output-format coff --output $(RES_FILE)
