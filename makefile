.PHONY: package run-stdlib-tests stdlib-tests test

package:
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
	./shed stdlib StdlibTests.Main --backend=javascript
	./shed stdlib StdlibTests.Main --backend=python
	./shed stdlib StdlibTests.Main

test: stdlib-tests
	mvn test
