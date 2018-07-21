Cluster-sharding "BlogApp" lab based on a Lightbend [tutorial](https://github.com/typesafehub/activator-akka-cluster-sharding-scala/blob/master/tutorial/index.html)
=====================================

This is an elaborated example of Akka sharded persistent actors. The main code is contained in branches corresponding the data store and the chosen persistence plugin. Currently the only storage supported is **Postgres**. The plugin chosen is [akka-persistence-jdbc](https://github.com/dnvriend/akka-persistence-jdbc) in the branch **persistence-dnvriend**. 