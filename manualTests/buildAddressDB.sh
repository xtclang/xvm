#!/bin/bash

gradle compileOne -PtestName=addressDB
gradle compileOne -PtestName=addressApp
gradle compileOne -PtestName=addressDB_json/module