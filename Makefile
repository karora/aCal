.PHONY: bundle release debug test clean

bundle:
	./build-in-container.sh bundleRelease

release:
	./build-in-container.sh assembleRelease

debug:
	./build-in-container.sh assembleDebug

test:
	./build-in-container.sh testDebugUnitTest

clean:
	./build-in-container.sh clean
