#!/bin/bash
unameOut="$(uname -s)"
case "${unameOut}" in
	Linux*)     java -cp "./lib/*:./" Main $1;;
	#arwin*)    
	#YGWIN*)    
	MINGW*)     java -cp "./lib/*;./" Main $1;;
	*)          java -cp "./lib/*:./" Main $1;;
esac
