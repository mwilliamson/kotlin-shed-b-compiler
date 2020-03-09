.PHONY: package run-stdlib-tests stdlib-tests test build-stdlib-llvm

package: build-stdlib-llvm
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
	./shed stdlib StdlibTests.Main --backend=javascript
	./shed stdlib StdlibTests.Main --backend=python
	./shed stdlib StdlibTests.Main

test: stdlib-tests
	mvn test

build-stdlib-llvm: stdlib-llvm/Strings.o stdlib-llvm/utf8proc-2.4.0/libutf8proc.a stdlib-llvm/gc-8.0.4/.libs/libgc.a

stdlib-llvm/Strings.o: stdlib-llvm/Strings.c
	gcc stdlib-llvm/Strings.c -c -Wall -Werror -o stdlib-llvm/Strings.o

stdlib-llvm/gc-8.0.4/.libs/libgc.a: stdlib-llvm/gc-8.0.4
	cd stdlib-llvm/gc-8.0.4 && ./configure --disable-dynamic-loading --enable-static --enable-threads=no && make

stdlib-llvm/gc-8.0.4:
	curl -L https://github.com/ivmai/bdwgc/releases/download/v8.0.4/gc-8.0.4.tar.gz | tar xzf - -C stdlib-llvm

stdlib-llvm/utf8proc-2.4.0/libutf8proc.a: stdlib-llvm/utf8proc-2.4.0
	cd stdlib-llvm/utf8proc-2.4.0 && make

stdlib-llvm/utf8proc-2.4.0:
	curl -L https://github.com/JuliaStrings/utf8proc/archive/v2.4.0.tar.gz | tar xzf - -C stdlib-llvm/
