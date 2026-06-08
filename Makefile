.PHONY: bundle debug clean

bundle:
	./build-in-container.sh bundleRelease

debug:
	./build-in-container.sh assembleDebug

clean:
	./build-in-container.sh clean
