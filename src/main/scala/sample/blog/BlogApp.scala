package sample.blog
import akka.Done
import akka.actor.CoordinatedShutdown.ClusterLeavingReason
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern._
import akka.util.Timeout
import sample.blog.read.PostEventListener
import sample.blog.read.PostEventListener._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
object BlogApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())
      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)
      val authorListingRegion = ClusterSharding(system).start(
        typeName = AuthorListing.shardName,
        entityProps = AuthorListing.props(),
        settings = ClusterShardingSettings(system),
        extractEntityId = AuthorListing.idExtractor,
        extractShardId = AuthorListing.shardResolver)
      ClusterSharding(system).start(
        typeName = Post.shardName,
        entityProps = Post.props(authorListingRegion),
        settings = ClusterShardingSettings(system),
        extractEntityId = Post.idExtractor,
        extractShardId = Post.shardResolver)
      if (port != "2551" && port != "2552") {
        val authors = Map(0 -> "Patrik", 1 -> "Martin", 2 -> "Roland", 3 -> "Björn", 4 -> "Endre")
        val postCreator = system.actorOf(PostCreatorBot.props(authors = authors), "pcbot")
        val chiefEditor = system.actorOf(ChiefEditorBot.props(authors = authors), "cebot")
        val database = Database.forConfig("slick.db", config)
        val eventListener = system.actorOf(PostEventListener.props(database))
        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseBeforeServiceUnbind, "taskBeforeLeavingTheCluster") { () =>
          import akka.pattern.ask
          import system.dispatcher
          implicit val timeout = Timeout(5.seconds)
          for {
            //            _ <- postCreator ? PostCreatorBot.Stop
            //            _ <- chiefEditor ? ChiefEditorBot.Stop
            _ <- eventListener ? PostEventListener.Stop
          } yield Done
        }
        System.in.read()
        println("shutdown in progress....")
        import system.dispatcher
        implicit val timeout = Timeout(5.seconds)
        val f = for {
          _ <- postCreator ? PostCreatorBot.Stop
          _ <- chiefEditor ? ChiefEditorBot.Stop
          _ <- after(500.millis, system.scheduler)(Future.successful(()))
          done <- CoordinatedShutdown(system).run(reason = ClusterLeavingReason)
        } yield done
        Await.ready(f, 10.seconds)
      }
    }
  }
}

