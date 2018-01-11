catchpocket is a tool to automatically generate
[lacinia](https://github.com/walmartlabs/lacinia) schema information
from a [datomic](http://www.datomic.com/) database.

## Setup

catchpocket depends on the datomic libraries being installed locally. To do so:

- Download datomic from [my.datomic.com](https://my.datomic.com/downloads)
- Unzip it somewhere
- From the root of that folder, run `bin/maven-install` to install the
  client libraries in your local repository.

## Running

```
lein run generate datomic:dev://localhost:4334/my-db-name
```

## How does it work?

catchpocket inspects a datomic database and gets metadata about its attributes
and inspects its data to do a bit of inference about how to map entity data to
lacinia schema information.

Because it takes a naive approach, catchpocket needs some additional information
in order to generate a more useful schema. You can provide this information

it then generates a lacinia schema configuration.

catchpocket works by making a bunch of assumptions about the way your data
is set up that may not be true (and are conventions datomic doesn't enforce):

- It assumes that every entity has a distinct namespace, so that a `:user`
  entity `:user/id` and `:user/name` attributes, but not an `:address/postal-code`
  attribute.
- It

##

## Similar projects

The [umlaut](https://github.com/workco/umlaut) project works off of a graphql
schema as its primary input, and is then capable of generating a datomic schema
from the schema, along with specs, graphviz diagrams, and a bunch of other
cool stuff.
