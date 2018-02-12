.PHONY: package runstdlib-tests stdlib-tests test

package:
	mvn package

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
	./shed stdlib stdlibTests.main --backend=javascript
	./shed stdlib stdlibTests.main --backend=python

test: stdlib-tests
	mvn test
