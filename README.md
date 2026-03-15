# mill-interceptor

A tool for intercepting other build tools using mill.
The idea is to use mill to "impersonate" other build tools.

## What does it do?

This tool serves as a replacement* for:

- mvn
- gradle
- sbt 

## Usage

Invoke the interceptor through the explicit parent command:

- `mill-interceptor intercept mvn test`
- `mill-interceptor intercept maven clean install`
- `mill-interceptor intercept sbt compile`
- `mill-interceptor intercept gradle build`

`mvn` is accepted as an alias for `maven`.

## Should I use this?

Sure, if you find it useful.
I had a very particular use-case involving integration with certain build pipelines that were
setup to work with common tools like maven, gradle, and sbt, but with no native support for mill.

## Native releases

Native executables are published through GitHub Releases as platform-specific archives named for `mise`'s GitHub backend. Stable releases use tags like `v1.2.3`; prereleases use semantic prerelease tags like `v1.2.3-rc.1`. Maintainer details live in `docs/contributing/releasing.md`.
