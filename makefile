.PHONY: package run-stdlib-tests stdlib-tests test clean-deps build-bdwgc build-utf8proc stdlib-llvm/build/libshed.a c-bindings

CFLAGS = -Wall -Werror

package: build-stdlib-llvm
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
#	./shed stdlib -m StdlibTests.Main --backend=javascript
	./shed stdlib -m StdlibTests.Main --backend=llvm
#	./shed stdlib -m StdlibTests.Main --backend=python
	./shed stdlib -m StdlibTests.Main

test: stdlib-tests
	mvn test

build-stdlib-llvm: build-deps stdlib-llvm/build/libshed.a

stdlib-llvm/build/libshed.a: stdlib-llvm/sizeof_jmp_buf.txt
	mkdir -p stdlib-llvm/build
	cd stdlib-llvm/build && cmake .. && make

build-deps: build-bdwgc build-utf8proc

build-bdwgc: stdlib-llvm/deps/gc-8.0.4

stdlib-llvm/deps/gc-8.0.4:
	#rm -rf stdlib-llvm/deps/gc-8.0.4
	curl -L https://github.com/ivmai/bdwgc/releases/download/v8.0.4/gc-8.0.4.tar.gz | tar xzf - -C stdlib-llvm/deps
	cd stdlib-llvm/deps/gc-8.0.4 && ./configure --enable-static --enable-threads=no && make

build-utf8proc: stdlib-llvm/deps/utf8proc-2.4.0

stdlib-llvm/deps/utf8proc-2.4.0:
	#rm -rf stdlib-llvm/deps/utf8proc-2.4.0
	curl -L https://github.com/JuliaStrings/utf8proc/archive/v2.4.0.tar.gz | tar xzf - -C stdlib-llvm/deps
	cd stdlib-llvm/deps/utf8proc-2.4.0 && make

stdlib-llvm/sizeof_jmp_buf.txt: stdlib-llvm/sizeof_jmp_buf.c
	gcc stdlib-llvm/sizeof_jmp_buf.c -o stdlib-llvm/sizeof_jmp_buf
	stdlib-llvm/sizeof_jmp_buf > stdlib-llvm/sizeof_jmp_buf.txt

c-bindings:
	java -cp cli/target/shed-compiler-cli-0.1.0-SNAPSHOT.jar org.shedlang.compiler.cli.ShedBindingsCli --backend=llvm --output-path=c-bindings
