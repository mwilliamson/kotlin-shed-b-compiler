.PHONY: build

build:
	mkdir -p build/modules
	clang --target=wasm32 -c src/modules/Core.IntToString.c -o build/modules/Core.IntToString.o -I src -I ../../c-runtime/src
	clang --target=wasm32 -c ../../c-runtime/src/strings.c -o build/strings.o -I src -I ../../c-runtime/src
