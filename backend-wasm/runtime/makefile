.PHONY: build

CFLAGS = --target=wasm32 -c -I src -I ../../c-runtime/src

build:
	mkdir -p build/modules
	clang $(CFLAGS) src/modules/Core.IntToString.c -o build/modules/Core.IntToString.o
	clang $(CFLAGS) ../../c-runtime/src/strings.c -o build/strings.o
