# Namespace Clone

Tools to clone (some or all) functions and arguments in a namespace and invoke them using Pedestal Interceptor chains.

The **clone** namespace can then be used instead of the **cloned** namespace in the *require* section of other namespaces that use the **cloned** namespace.

This provides two benefits:

1. the clone namespace can support a subset of fns available in the cloned namespace i.e. reduce the api size to fns that you want exposed.
2. the interceptor chain allows custom cross-cutting/middleware to be added to all function calls.
This opens up many useful behaviours at the namespace/API layer e.g.
    * API call logging/timing
    * Error handling / translation e.g. using [Phrase](https://github.com/alexanderkiel/phrase) to convert some spec errors to user facing error messages
    * Application context specific concerns e.g. access controls
    * Your ideas here!

The clone api uses multi-methods to allow open extension of any application context shape and interceptor chains.

## The original use-case

A wrapper for the Datomic API functions available in all libs i.e. the lowest common denominator set of functions

Allows application code to be built against an API that will work with Client AND Peer libraries.

This is useful when want local dev/test against a local "mem" database but want to run your code in production against the Cloud/Client API.

The **peer** sub-project has an implementation of the delegate interceptors for use with the Peer API.

TODO **client** sub-project

## Usage

Look at the Datomic example for how to clone a namespace. Then in your own *clone* namespace:

1. Implement the *defmethods* for your application context and interceptor chains
2. Optionally implement a spec for your app context data

Then replace the `(:require [datomic.api :as d])` with `(:require [datomic-interceptors.api :as d])`

The datomic-clone-test namespace provides a simple mocked version of this.

## Assumptions

This lib works by passing around a wrapper (map) in place of one of the args to all cloned functions.
The wrapped value (the original arg) is passed in the :UNSAFE! map entry. This makes it available for the *delegate* interceptor to use it to call the underlying function.

If the wrapped arg is translated into another value, for use in another cloned function, then it should transfer all the map data and assoc the new value in the :UNSAFE! key.

## Disadvantages

Stacktraces : will be bigger because each interceptor is now a part of the stack.
Documentation : when you use your IDE to show doc for a clone fn, you will see the clone and not the underlying (cloned) functions doc.

## Ideas / Future

This general pattern could be used to add middleware any namespace.
Need a macro to generate the clone fns and queue factory fns i.e. less boilerplate to use this lib.

Enhanced the logger interceptor to record the invoking function i.e. one above in the call stack.
This will be useful when wanting to local where a cloned fn was called.

## Acknowledgements

The pattern of cloning the Datomic API was inspired by the [Bridge](https://github.com/robert-stuttaford/bridge)
Thanks Rob for sharing!

## License

Copyright © 2018 Steve Buikhuizen

TODO which license?