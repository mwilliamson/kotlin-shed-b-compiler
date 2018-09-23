.PHONY: package run-stdlib-tests stdlib-tests test

package:
	mvn package -Dmaven.test.skip=true

stdlib-tests: package run-stdlib-tests

run-stdlib-tests:
	./shed stdlib stdlibTests.Main --backend=javascript
	./shed stdlib stdlibTests.Main --backend=python

test: stdlib-tests
	mvn test
