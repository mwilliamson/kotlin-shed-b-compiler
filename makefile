.PHONY: package test

package:
	mvn package

test:
	mvn test
	./shed stdlib stdlibTests.main --backend=javascript
	#./shed stdlib stdlibTests.main --backend=python
