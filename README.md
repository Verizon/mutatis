# Xenomorph

[![Build Status](https://travis.oncue.verizon.net/iptv/xenomorph.svg?token=sG4K5rLKPjKXqXjsTKAM&branch=master)](https://travis.oncue.verizon.net/iptv/xenomorph)
[![codecov](https://codecov.oncue.verizon.net/ghe/iptv/xenomorph/branch/master/graph/badge.svg)](https://codecov.oncue.verizon.net/ghe/iptv/xenomorph)

Xenomorph provides Scalaz Streams wrapper for Kafka Producer and Consumer

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
   sbt "test:run-main xenomorph.ExampleProducer"
```
* Start consumer - notice one message takes 250 milliseconds to process, one consumer is not enough:
```
   sbt "test:run-main xenomorph.ExampleConsumer"
```

See `src/test/scala/xenomorph/example.scala` for a complete example

## Running the tests

`sbt test`

## Built With

* [Kafka](https://kafka.apache.org/082/documentation.html) - A distributed streaming platform
* [Scalaz](https://github.com/scalaz/scalaz) - An extension to the core Scala library for functional programming.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/your/project/tags).

## Authors

See the list of [contributors](https://github.oncue.verizon.net/iptv/xenomorph/graphs/contributors) who participated in this project.

## Acknowledgments

* https://github.oncue.verizon.net/d
* https://github.oncue.verizon.net/MURTHHA
* https://github.oncue.verizon.net/rolando
* https://github.oncue.verizon.net/prateek
