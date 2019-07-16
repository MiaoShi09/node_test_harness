### Steps to start rust kernel and run test with testrig:
1. copy aionr package into `Tests/aionrproxy`
2. double check the configuration in `Tests/test_resources/proxy_rust_custom`
3. before run test, make sure blockade is up
4. In node_test_harness directory, run `./gradlew Tests:test -PtestNodes=proxyr -i` to start a rust node and the tests

### Steps to start Java kernel and run test with testrig:
1. copy aion package into `Tests/aionproxy`
2. double check the configuration in `Tests/test_resources/proxy_java_custom/config`
3. before run test, make sure blockade is up
4. In node_test_harness directory, run `./gradlew Tests:test -PtestNodes=proxy -i` to start a java node and the tests