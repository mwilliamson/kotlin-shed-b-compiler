.PHONY: build

build:
	mkdir -p build/modules
	clang --target=wasm32 -c src/modules/Core.IntToString.c -o build/modules/Core.IntToString.o
	clang --target=wasm32 -c src/strings.c -o build/strings.o
