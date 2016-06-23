#!/bin/sh

mkdir /tmp/ambry/ && cd /tmp/ambry/

#wget --no-check-certificate https://github.com/linkedin/ambry/archive/master.tar.gz

curl -L https://api.github.com/repos/linkedin/ambry/tarball > /tmp/ambry.tar.gz
tar -xvf /tmp/ambry.tar.gz -C /tmp/
