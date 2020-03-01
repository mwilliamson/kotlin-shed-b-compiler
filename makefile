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

build-stdlib-llvm: stdlib-llvm/Strings.o

stdlib-llvm/Strings.o: stdlib-llvm/Strings.c
	gcc stdlib-llvm/Strings.c -c -Wall -Werror
