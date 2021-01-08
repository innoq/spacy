# spacy

An application for moderating open space events that is built from the ground up to make sure that it is accessible.

## Development Setup

Start the application with the following commands

* To start the Clojure application

      lein repl

  and then in the repl:

      (go)

* To compile the assets (this will copy them into `/resources/public`)

      npm start

## Production Setup

### Configuring a Postgres Database

We use [aero](https://github.com/juxt/aero) to configure our application, and
we use [crux](https://github.com/juxt/crux) as a Database. By default, we
use sqlite as a backend for Crux, but for production databases we also have
support for Postgres. To configure the application to use Postgres, you can
modify the `:crux` entry in the `resources/config.edn` to the following:

```clojure
{:crux {:db-spec #profile {:dev {:dbtype "sqlite"
                                 :dbname "dev.db"}
                           :prod {:dbtype "postgres"
                                  :dbname #env DB_NAME
                                  :host #env DB_HOST
                                  :user #env DB_USER
                                  :password #env DB_PASSWORD}}}}
```

Please _**NEVER**_ actually check your database credentials into a git
repostitory!

*NOTE*: The authors of aero recommend [against](https://github.com/juxt/aero#env)
using environment variables for configuring credentials. For this reason,
they offer a [different method](https://github.com/juxt/aero#hide-passwords-in-local-private-files)
for managing secrets so you can check that out and see if it would be
preferable for your deployment environment.


## License

Copyright © 2020 innoQ Deutschland GmbH

Released under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html)
