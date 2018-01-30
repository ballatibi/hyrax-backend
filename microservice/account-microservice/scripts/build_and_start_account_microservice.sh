#!/usr/bin/env bash

sh build_account_microservice.sh

if [ $? -ne 0 ]; then
    exit 1
fi

sh start_account_microservice.sh

if [ $? -ne 0 ]; then
    exit 1
else
    exit 0
fi