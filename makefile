.PHONY: package stdlib-tests run-stdlib-tests test c-bindings build-runtime-llvm build-runtime-wasm

package: build-runtime-llvm build-runtime-wasm
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
#	./shed stdlib -m StdlibTests.Main --backend=javascript
	./shed stdlib -m StdlibTests.Main --backend=llvm
#	./shed stdlib -m StdlibTests.Main --backend=python
	./shed stdlib -m StdlibTests.Main

test: stdlib-tests
	mvn test

c-bindings:
	java -cp cli/target/shed-compiler-cli-0.1.0-SNAPSHOT.jar org.shedlang.compiler.cli.ShedBindingsCli --backend=llvm --output-path=c-bindings

build-runtime-llvm:
	cd backend-llvm/runtime && make

build-runtime-wasm:
	cd backend-wasm/runtime && make
