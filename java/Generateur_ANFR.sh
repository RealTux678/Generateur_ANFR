#!/bin/bash
script_path="$(realpath "$0")"
DIR_script="$(dirname "${script_path}")"
java -cp "${DIR_script}/lib/sqlite-jdbc-3.36.0.1.jar:${DIR_script}/" Main $1
