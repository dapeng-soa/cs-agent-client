#!/usr/bin/env bash
version=2.2.1
pwd=`pwd`

sbt dist

cp ./target/agentClient ./dist

tar -czvpf $pwd/target/agent_client_$version.tar.gz dist

echo "release package at $pwd/target/agent_client_$version.tar.gz"
