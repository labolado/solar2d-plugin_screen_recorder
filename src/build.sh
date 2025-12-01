#!/bin/bash
cd $(dirname "$0")

ios/build.sh ../plugins/2020.3620

cd android
# 自动拉取子模块的远程最新代码
git submodule update --init --recursive
git submodule update --remote --merge

./gradlew clean
# 显式编译 hbrecorder 模块，确保它是最新的
./gradlew :hbrecorder:assembleRelease
./gradlew deployPluginToDirectory
