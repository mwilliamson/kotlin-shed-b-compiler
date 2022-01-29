.PHONY: build

CFLAGS = --target=wasm32 -c -I src -I ../../c-runtime/src

build:
	mkdir -p build/modules
	clang $(CFLAGS) ../../c-runtime/src/strings.c -o build/strings.o
	clang $(CFLAGS) ../../c-runtime/src/modules/Core.Cast.c -o build/modules/Core.Cast.o
	clang $(CFLAGS) src/shed_platform_effects.c -o build/shed_platform_effects.o
	clang $(CFLAGS) src/modules/Core.IntToString.c -o build/modules/Core.IntToString.o
	clang $(CFLAGS) src/modules/Stdlib.Platform.Process.c -o build/modules/Stdlib.Platform.Process.o
