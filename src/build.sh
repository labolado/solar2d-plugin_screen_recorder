#!/bin/bash
cd $(dirname "$0")

ios/build.sh ../plugins/2020.3620

cd android
./gradlew clean
./gradlew deployPluginToDirectory
