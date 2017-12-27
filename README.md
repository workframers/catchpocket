harvester is a tool to automatically generate
[lacinia](https://github.com/walmartlabs/lacinia) schema information
from a [datomic](http://www.datomic.com/) database.

## Setup

harvester depends on the datomic libraries being installed locally. To do so:

- Download datomic from [my.datomic.com](https://my.datomic.com/downloads)
- Unzip it somewhere
- From the root of that folder, run `bin/maven-install` to install the
  client libraries in your local repository.
