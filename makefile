.PHONY: package run-stdlib-tests stdlib-tests test clean-deps build-bdwgc build-utf8proc

CFLAGS = -Wall -Werror

package: build-stdlib-llvm
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
	./shed stdlib StdlibTests.Main --backend=javascript
	./shed stdlib StdlibTests.Main --backend=python
	./shed stdlib StdlibTests.Main

test: stdlib-tests
	mvn test

build-stdlib-llvm: build-deps compile-stdlib-llvm

compile-stdlib-llvm: stdlib-llvm/obj/shed.o stdlib-llvm/obj/Stdlib.Platform.StringBuilder.o stdlib-llvm/obj/Stdlib.Platform.Strings.o

stdlib-llvm/obj/shed.o: stdlib-llvm/src/shed.c stdlib-llvm/src/shed.h
	mkdir -p $$(dirname $@)
	gcc $< -c $(CFLAGS) -o $@

stdlib-llvm/obj/Stdlib.Platform.StringBuilder.o: stdlib-llvm/src/Stdlib.Platform.StringBuilder.c stdlib-llvm/src/shed.h
	mkdir -p $$(dirname $@)
	gcc $< -c $(CFLAGS) -o $@

stdlib-llvm/obj/Stdlib.Platform.Strings.o: stdlib-llvm/src/Stdlib.Platform.Strings.c stdlib-llvm/src/shed.h
	mkdir -p $$(dirname $@)
	gcc $< -c $(CFLAGS) -o $@

build-deps: build-bdwgc build-utf8proc

build-bdwgc:
	rm -r stdlib-llvm/deps/gc-8.0.4
	curl -L https://github.com/ivmai/bdwgc/releases/download/v8.0.4/gc-8.0.4.tar.gz | tar xzf - -C stdlib-llvm/deps
	cd stdlib-llvm/deps/gc-8.0.4 && ./configure --enable-static --enable-threads=no && make

build-utf8proc:
	rm -r stdlib-llvm/deps/utf8proc-2.4.0
	curl -L https://github.com/JuliaStrings/utf8proc/archive/v2.4.0.tar.gz | tar xzf - -C stdlib-llvm/deps
	cd stdlib-llvm/deps/utf8proc-2.4.0 && make

