# Database

AnyStream uses a single [SQLite](https://www.sqlite.org) database for application data, media file references, and
metadata.

## SQLite

SQLite is the ideal choice for application portability and to maintain a simple developer experience.

**Portability**

- End users are not required to install additional software to run AnyStream (besides a JRE).
- Usage in a container (i.e. Docker) does not require multiple containers or multiple internal applications.
- SQLite has broad OS/architecture increasing the number of targets AnyStream can support.
- The database is a single file that can be backed up or transferred to different hosts without modification or errors.

**Simplicity**

- Reduced datatype complexity and limited niche SQL functionality support
- Built in full-text search ([FTS5](https://www.sqlite.org/fts5.html))
  and [JSON storage](https://www.sqlite.org/json1.html)
- Limited developer configuration required to build and run the project

Using SQLite is not a perfect experience, for example the lack of `RETURNING` support for `DELETE` statements results in
more queries and server code to verify and return result data.

With that said, it remains the best option for reducing the support overhead and maintaining a simple experience for
users.

There are no plans to change or support multiple databases.

## Libraries

There are 3 parts to consider for providing a stable developer and end user experience for the database:

**Migrations**

To maintain a stable database schema and content over time,
[Flyway](https://github.com/flyway/flyway?tab=readme-ov-file) is used to preform schema and data migrations.

Flyway is a JVM library that is bundled with AnyStream to track and perform migrations automatically.

**Database Driver**

[xerial/sqlite-jdbc](https://github.com/xerial/sqlite-jdbc?tab=readme-ov-file)
provides both the SQLite library and [JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity) driver.

**Query DSL**

Interacting with the database is done via [jOOQ](https://github.com/jOOQ/jOOQ?tab=readme-ov-file).
It provides a familiar typesafe SQL DSL for Kotlin making SQL easy and safe with tight coupling to related server code.

[etiennestuder/gradle-jooq-plugin](https://github.com/etiennestuder/gradle-jooq-plugin?tab=readme-ov-file)
is used to integrate [jOOQ's codegen](https://blog.jooq.org/why-you-should-use-jooq-with-code-generation/) capabilities.

## Schema Design

[Single Table Inheritance](https://en.wikipedia.org/wiki/Single_Table_Inheritance) is applied when possible to simplify
building media kind agnostic application APIs.
For example: Metadata for movies, tv shows/episodes, and music artists/albums/songs are all stored in the `metadata`
table.

## Migrations

See [Flyway > Migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)

Migrations are written in standard SQL and stored in: `server/db-models/src/main/resources/db/migration`

*todo: fill this out when migration testing infrastructure exists and v1 schema is final*

## Using jOOQ

### Coroutines Support

### Data Models


