#!/bin/bash
# usage: bash port/rabbit/build.sh  -> port/out/rabbit.jar
set -e
source "$(dirname "$0")/../../env.sh"
P=$W/port/rabbit; O=$W/port/work/rabbit; rm -rf $O; mkdir -p $O/classes
CP=$(paste -sd: $W/tools/base.cp):$W/lib/bbs27.jar:$(ls $W/tools/lib/asm*.jar | paste -sd:)
find $P/src -name "*.java" > $O/srcs.txt
$J/javac -nowarn -proc:none -encoding UTF-8 --release 17 -g -parameters -cp "$CP" -d $O/classes @$O/srcs.txt
cp -r $P/res/. $O/classes/
mkdir -p $W/port/out; rm -f $W/port/out/rabbit.jar
cd $O/classes && $J/jar --create --file $W/port/out/rabbit.jar .
echo "built $W/port/out/rabbit.jar"
