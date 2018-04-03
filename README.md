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

A wrapper for the Datomic API functions available in all libs
i.e. the lowest common denominator set of functions from the Client AND Peer libraries.

This is useful when want local dev/test against a local "mem" database but want to run your code in production against the Cloud/Client API.

The **datomic-peer-clone** sub-project has an implementation of the delegate interceptors for use with the Peer API.

TODO **datomic-client-clone** sub-project

**On Datomic API Open-ness**

The Datomic peer api not open (i.e. functions only) but the client api is a protocol so much better.
This lib should provide a consistent api for both.

## Usage

Look at the Datomic example for how to clone a namespace. Then in your own *clone* namespace:

1. Implement the *defmethods* for your application context and interceptor chains
2. Optionally implement a spec for your app context data
3. Replace the `(:require [datomic.api :as d])` with your clone namespace.

The initial wrapped value can be produced any way you want e.g. your own function.
Look at the datomic-clone-test to see that the initial *conn* value being created.

The datomic-clone-test namespace provides a simple mocked version of this.

Then look at the **datomic-peer-clone** sub-project to see how to implement a clone with a real delegate interceptor instead of the mock delegates in the tests.
The **datomic-peer-clone** sub-project is intended for use as a dependency in Datomic projects which need to run using both the local (peer) or Cloud (client) APIs.

Despite the Datomic slant, the **ns-clone** project has no Datomic dependencies and is designed to be used to clone any namespace, without Datomic.
That is why **datomic-peer-clone** is a sub-project, to avoid unwanted dependencies when used elsewhere.

## Installation

To run tests:

1. `lein test`
2. `lein sub test`

To install from source:

1. `lein install` installs the ns-clone lib
2. `lein sub install` installs the datomic-peer-clone lib

## Assumptions

This lib works by passing around a wrapper (map) in place of one of the args to all cloned functions.
The wrapped value (the original arg) is passed in the :UNSAFE! map entry. This makes it available for the *delegate* interceptor to use it to call the underlying function.
Of course any function can access the :UNSAFE! value but that will appear in source and discourage any developer from going around the interceptor chain.

If the wrapped arg is translated into another value, for use in another cloned function, then it should transfer all the map data and assoc the new value in the :UNSAFE! key.

## Status

This lib is being used in a Datomic webapp, which is not yet running in Production.

Not yet deployed to Clojars, will do so after initial feedback.

## Disadvantages

* Stacktraces : will be bigger because, at least the delegate interceptor is included in the stack.
* Error messages : arity errors in api calls are reported by the clone namespace. This is not too bad since the same error would occur using the cloned namespace.
* Documentation : when you use your IDE to show doc for a clone fn, you will see the clone function doc and not the underlying (cloned) functions doc.

## Ideas / Future

Add a macro to generate the clone fns and queue factory fns i.e. less boilerplate to use this lib. It could even copy over the doc from the cloned functions.

The Datomic example clone needs a transact-async fn. My future foo is not strong enough to do this.
If you know how to wrap a future in a future (without adding a dependency), I'd appreciate the help.

Enhance the logger interceptor to record the invoking function i.e. one above in the call stack.
This will be useful when wanting to local where a cloned fn was called.

## Acknowledgements

The pattern of cloning the Datomic API was inspired by the [Bridge](https://github.com/robert-stuttaford/bridge)
Thanks Rob for sharing!

## License

Copyright © 2018 Steve Buikhuizen

TODO which license?