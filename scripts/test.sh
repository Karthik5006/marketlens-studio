#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
market_sdk="${MARKET_ANDROID_SDK:-${STILL_ANDROID_SDK:-/workspace/android/toolchain/sdk}}"
market_ecj="${MARKET_ECJ_JAR:-${STILL_ECJ_JAR:-/workspace/android/toolchain/ecj.jar}}"
market_json="${MARKET_TEST_JSON_JAR:-/workspace/android/toolchain/json-20250517.jar}"
mkdir -p build/tests
java -jar "$market_ecj" -source 1.8 -target 1.8 -proc:none -nowarn -bootclasspath "$market_sdk/platforms/android-35/android.jar" -classpath "$market_json:$market_sdk/build-tools/35.0.0/core-lambda-stubs.jar" -d build/tests app/src/main/java/app/marketlens/{Data,Net,Parse,Xlsx,Repository,Research,ResearchRepository,Comparisons,InvestorState}.java app/src/test/java/app/marketlens/CoreTests.java app/src/test/java/app/marketlens/InvestorTests.java
java -cp "build/tests:$market_json:$market_sdk/platforms/android-35/android.jar" app.marketlens.CoreTests app/src/test/fixtures

java -cp "build/tests:$market_json:$market_sdk/platforms/android-35/android.jar" app.marketlens.InvestorTests app/src/test/fixtures/v11
