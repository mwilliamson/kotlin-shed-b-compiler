.PHONY: package stdlib-tests test

package:
	mvn package

stdlib-tests: package
	./shed stdlib stdlibTests.main --backend=javascript
	./shed stdlib stdlibTests.main --backend=python

test: stdlib-tests
	mvn test
