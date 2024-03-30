## Media Performance Class CTS Tests
Current folder comprises of files necessary for testing media performance class.

The test vectors used by the test suite is available at [link](https://storage.googleapis.com/android_media/cts/tests/mediapc/CtsMediaPerformanceClassTestCases-1.4.zip) and is downloaded automatically while running tests. Manual installation of these can be done using copy_media.sh script in this directory.

### Commands
#### To run all tests in CtsMediaPerformanceClassTestCases
```sh
$ atest CtsMediaPerformanceClassTestCases
```
#### To run a subset of tests in CtsMediaPerformanceClassTestCases
```sh
$ atest CtsMediaPerformanceClassTestCases:android.mediapc.cts.FrameDropTest
```
#### To run all tests in CtsMediaPerformanceClassTestCases by overriding Build.VERSION.MEDIA_PERFORMANCE_CLASS
In some cases it might be useful to override Build.VERSION.MEDIA_PERFORMANCE_CLASS and run the tests.
For eg: when the device doesn't advertise Build.VERSION.MEDIA_PERFORMANCE_CLASS, running the tests by overriding
this will help in determining the which performance class requirements are met by the device.
Following runs the tests by overriding Build.VERSION.MEDIA_PERFORMANCE_CLASS as S.
```sh
$ atest CtsMediaPerformanceClassTestCases -- --module-arg CtsMediaPerformanceClassTestCases:instrumentation-arg:media-performance-class:=31
```
