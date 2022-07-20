#!/bin/bash
script_path="$(realpath "$0")"
DIR_script="$(dirname "${script_path}")"
java -cp "${DIR_script}/lib/*:${DIR_script}/" Main $1
