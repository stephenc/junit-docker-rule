# changelog #

## 0.6.0 (2021-02-09) ##

Changes:
- Switch to [`org.mandas` fork of docker client](https://github.com/dmandalidis/docker-client) version 5.0.0
- Updated test images to avoid breakage with dockerhub expiration policy
- Java 8 required

## 0.5.2 (2019-04-11) ##

Changes:
- Spotify docker client updated to version 8.16.0

## 0.5.1 (2019-04-11) ##

Changes:
- Additional tidy-up on failed wait condition fixes

## 0.5.0 (2019-04-11) ##

Changes:
- Spotify docker client updated to version 8.11.1.0
- Added support for adding ulimits to HostConfig
- Tidy up containers if wait condition fails
- Fix tests on Mac/Windows

## 0.4.4 (2018-03-29) ##

Changes:
- Allow tests to run on OS-X
- Add ability to control the memory configuration of container

## 0.4.3 (never released) ##

## 0.4.2 (2018-02-28) ##

Changes:
- Fixed parent pom

## 0.4.1 (2018-02-28) ##

Changes:
- Forked to `io.github.stephenc.docker` groupId
- Switched to shaded version of docket client

## 0.4 (2018-02-15) ##

Changes:
- Spotify docker client updated to version 8.9.2
- ([#41](../../../issues/41)) Bugfix: Log message order can be lost

## 0.3 (2016-12-09) ##

Changes:
- ([#39](../../../issues/39)) Accept not only file and paths but also volume names in _mountFrom_
- ([#38](../../../issues/38)) Allow definition of restart policy on container start
- ([#36](../../../issues/36)) Change docker client dependency to non-shaded
- ([#34](../../../issues/34)) Expose underlying Docker API ContainerInfo to rule clients
- ([#35](../../../issues/35)) Allow defining custom container startup conditions.
  Conditions are now specified with builder `waitFor(StartCondition condition)` and *old* builder
  methods (`waitFor...(...)`) are marked as deprecated.
  See [custom condition example](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleWaitForCustomTest.java).

Fixes:

- ([#33](../../../issues/33)) bug: rule does not allow publishing same internal port twice

## 0.2 (2016-09-13) ##

New features:

- static ([#23](../../../issues/23)) and dynamic ([#27](../../../issues/27)) container linking
  ([static](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleLinkTest.java) and
  [dynamic](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleLinkDynamicTest.java) example)
- ([#24](../../../issues/24)) wait for specific sequence of messages
  (instead single message) in output at container start
  ([example](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleWaitForLogMessageSequenceAtStartTest.java))
- ([#2](../../../issues/2)) wait for http url and tcp socket open on container start
  (examples: [tcp socket wait](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleWaitForTcpPortTest.java)
  and [http wait](../src/test/java/pl/domzal/junit/docker/rule/examples/ExampleWaitForHttpPingTest.java)).
  See [notes](tcp_wait_notes.md)
- ([#29](../../../issues/29)) expose specified container port to random host port
  ([example](../src/test/java/pl/domzal/junit/docker/rule/examples/ExamplePortExposeDynamicTest.java))

## 0.1.1 ##

Released 2016-09-08

Fixes:

-  ([#32](../../../issues/32)) NullPointerException if an image don't have a repoTags

## 0.1 ##

First stable version.
Features:

- use it as JUnit @Rule or @ClassRule
- specify image name/tag
- specify container name (equivalent of command line `--name`)
- pass environment variables (`--env` or `-e`)
- publish all exposed port to dynamically allocated host ports (`--publish-all` or `-P`)
- publish specified container ports to specified host ports (`-p` - tcp or udp, no port
  ranges support yet)
- mount host directory as a data volume (`--volume` or `-v` - also works for workstation
  dirs to boot2docker container with restriction that dir must be under user homedir)
- specify extra /etc/hosts entries (`--add-host`)
- access container stderr and stdout (forwarded to java System.err and System.out by
  default)
- wait for message in output at container start

