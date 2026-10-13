.PHONY: bundle release debug test device-test clean

bundle:
	./build-in-container.sh bundleRelease

release:
	./build-in-container.sh assembleRelease

debug:
	./build-in-container.sh assembleDebug

test:
	./build-in-container.sh testDebugUnitTest

# The tests that need a device, on an emulator in the build container
device-test:
	./build-in-container.sh emulatorDebugAndroidTest

clean:
	./build-in-container.sh clean
