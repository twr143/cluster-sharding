Cluster-sharding "BlogApp" lab based on a Lightbend [tutorial](https://github.com/typesafehub/activator-akka-cluster-sharding-scala/blob/master/tutorial/index.html)
=====================================


This is the reworked version of the Akka sharded persistent actors example provided by Lightbend. 
- The chosen plugin for persistent storage is by [Dennis Vriend](https://github.com/dnvriend/akka-persistence-jdbc). 
- The database chosen is Postgres. 
- Post representation has been transformed from the persistent actor to FSM
- Kryo serialization is used for serializing commands and events. 
- The "Query" sql view is populated: **es_post** table.

## "Business logic"
There are the two bots generating events. The first one, **PostCreatorBot**, creates, edits and publishes the post for each author. The second one, **ChiefEditorBot**, removes 5 oldest posts for each author. Both bots act periodically in a self-scheduled manner.
## Installation and run

1.  Create a Postgres database schema and configure the datasource in **\src\main\resources\postgres-application.conf**
2.  Execute a run-once sql script located at **main\resources\schema\postgres\postgres-schema.sql**
3.  * Run the first cluster node: `sbt "run 2551"` 
    * Run the second cluster node: `sbt "run 2552"`
    * Run the managing node: `sbt "run 0"`
4.  Make sure that the **journal**, **snapshot** and all **es_** tables are correctly populated.
## License
This source code is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0)

