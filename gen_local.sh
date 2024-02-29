#!/usr/bin/env bash
set -e

f_clear_file () {
    if [[ -f ${1} ]]
    then
        rm ${1}
    fi
}

f_copy_file () {
    cd ${current}/plugins/2020.3620/${1}
    f_clear_file 2020.3620-${1}.tgz
    tar -czvf "2020.3620-${1}.tgz" --exclude=".*" *
    dst=../../../../local_server_4_solar2d_plugins/plugins/${1}/plugin.screenRecorder
    if [[ ! -d ${dst} ]]
    then
        mkdir ../../../../local_server_4_solar2d_plugins/plugins/${1}/plugin.screenRecorder
    fi
    cp 2020.3620-${1}.tgz ../../../../local_server_4_solar2d_plugins/plugins/${1}/plugin.screenRecorder/
    echo Copy ${1} to local server complete!
}

cd "$(dirname "$0")"
current=$(pwd)
./src/build.sh

f_copy_file iphone
# f_copy_file iphone-sim
