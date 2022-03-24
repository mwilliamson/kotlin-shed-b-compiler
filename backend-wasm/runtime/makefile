.PHONY: build

CFLAGS = --target=wasm32 -c -I src/polyfills -I src -I ../../c-runtime/src -Wall -Werror

build:
	mkdir -p build/modules
	mkdir -p build/polyfills
	clang $(CFLAGS) src/polyfills/string.c -o build/polyfills/string.o
	clang $(CFLAGS) ../../c-runtime/src/shed.c -o build/shed.o
	clang $(CFLAGS) ../../c-runtime/src/strings.c -o build/strings.o
	clang $(CFLAGS) ../../c-runtime/src/stringbuilder.c -o build/stringbuilder.o
	clang $(CFLAGS) ../../c-runtime/src/modules/Core.Cast.c -o build/modules/Core.Cast.o
	clang $(CFLAGS) ../../c-runtime/src/modules/Stdlib.Platform.StringBuilder.c -o build/modules/Stdlib.Platform.StringBuilder.o
	clang $(CFLAGS) ../../c-runtime/src/modules/Stdlib.Platform.Strings.c -o build/modules/Stdlib.Platform.Strings.o
	clang $(CFLAGS) src/shed_platform_effects.c -o build/shed_platform_effects.o
	clang $(CFLAGS) src/modules/Core.IntToString.c -o build/modules/Core.IntToString.o
	clang $(CFLAGS) src/modules/Stdlib.Platform.Process.c -o build/modules/Stdlib.Platform.Process.o
