#!/bin/bash

echo "[$0] Building project"
mvn clean package -DskipTests
DEPS="$PWD/agent"
echo "[$0] Creating [$DEPS] folder. Create a symbolic link to this directory when running (same level as script)."
if [ ! -d "$DEPS" ]; then
  mkdir $DEPS
else
  rm -rf $DEPS/*
fi
cd $DEPS
ln -s ../target/deps/*.jar .
ln -s ../target/*.jar .
ln -s ../target/conf/* .
ln -s ../spoon/*.jar .
cd ..
echo "[$0] Done..."