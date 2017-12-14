# mutatis
[![Build Status](https://travis-ci.org/Verizon/mutatis.svg?branch=master)](https://travis-ci.org/Verizon/mutatis)

mutatis provides Scalaz Streams wrapper for Kafka Producer and Consumer

## Getting Started

These instructions will get a copy of the project up and running on your local machine for development and testing purposes. 

### Prerequisites

Set up kafka server:


Download and start zk and kafka per https://kafka.apache.org/082/documentation.html#quickstart

### Installing

* create a topic called "test8" with 8 partitions:
```
   ./bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 8 --topic test8
```
* Start producer - notice one message is produced every 100 milliseconds:
```
   sbt "test:run-main mutatis.ExampleProducer"
```
* Start consumer - notice one message takes 250 milliseconds to process, one consumer is not enough:
```
   sbt "test:run-main mutatis.ExampleConsumer"
```

See `src/test/scala/mutatis/example.scala` for a complete example

## Running the tests

`sbt test`

## Built With

* [Kafka](https://kafka.apache.org/082/documentation.html) - A distributed streaming platform
* [Scalaz](https://github.com/scalaz/scalaz) - An extension to the core Scala library for functional programming.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](./tags).

## Authors

See the list of [contributors](./graphs/contributors) who participated in this project.

## Acknowledgments

* https://github.com/dougkang
* https://github.com/haripriyamurthy
* https://github.com/rolandomanrique
* https://github.com/kothari-pk

*****************************************
Copyright 2017  Verizon.
               
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
****************************************************************
