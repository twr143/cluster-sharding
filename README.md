cluster-sharding "BlogApp" lab based on Lightbend [tutorial](https://github.com/typesafehub/activator-akka-cluster-sharding-scala/blob/master/tutorial/index.html)
=====================================

This is an elaborated example of Akka sharded persistent actors with Postgres-based storage. Kryo serialization is used for serializing commands and events. Also "query" sql views are populated (**es_** tables).

# Installation and run

1.  Create a Postgres database schema and configure the datasource in **\src\main\resources\postgres-application.conf**
2.  Execute a run-once sql script located at **main\resources\schema\postgres\postgres-schema.sql**
3.  * Run the first cluster node: `sbt "run 2551"` 
     * Run the second cluster node: `sbt "run 2552"`
     * Run the managing node: `sbt "run 0"`
4.  Make sure that the **journal**, **snapshot** and all **es_** tables are correctly populated.
