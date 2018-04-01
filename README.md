# Namespace Clone



## The original use-case

A wrapper for the Datomic API functions available in all libs i.e. the lower common denominator set of functions

This provides two benefits:

1. allows application code to be built against and API that will work with Client AND Peer libraries.
This is useful when want local dev/test against a local "mem" database but want to run your code in production against the Cloud/Client API.
2. the interceptor chain allows custom cross-cutting/middleware to be added to all Datomic API calls.
This opens up many useful behaviours at the Data layer e.g.
    * API call logging/timing
    * Error handling / translation e.g. using [Phrase](https://github.com/alexanderkiel/phrase) to convert some spec errors to user facing error messages
    * Application context specific concerns e.g. access controls, multi-tenancy
    * Distributed tracing integration (a la Open Tracing)
    * Your ideas here!

The api is built using multi-methods to allow open extension of the application context shape and interceptor chains.

## Usage

Implement the *defmethods* for your application context and interceptor chains.
Then replace the `(:require [datomic.api :as d])` with `(:require [datomic-interceptors.api :as d])`

The api-test namespace provides a simple mocked version of this.

The **peer** sub-project has an implementation of the delegate interceptors for use with the Peer API.

TODO **client** sub-project

## Assumptions

One of the args to every fn is *wrapped* in a map so that it can carry context.
If no context is required by the interceptor chain then this is not required.

## Disadvantages

Stacktraces : will be bigger because each interceptor is now a part of the stack.

## Ideas / Future

This general pattern could be used to add middleware any namespace.
The steps to build this seem to be:

1. A macro to generate the clone fns and queue factory fns

## Acknowledgements

The pattern of cloning the Datomic API was inspired by the [Bridge](https://github.com/robert-stuttaford/bridge)
Thanks Rob for sharing!

## License

Copyright © 2018 Steve Buikhuizen

TODO which license?