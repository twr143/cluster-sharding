package sample.blog
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import akka.pattern._
import sample.blog.AuthorListing.RemoveFirstN

import scala.concurrent.duration._
/**
  * Created by Ilya Volynin on 05.07.2018 at 10:53.
  */
class ChiefEditorBot(authors: Map[Int, String]) extends Actor with ActorLogging {
  import ChiefEditorBot._
  import context.dispatcher
  implicit val timeout = Timeout(3.seconds)

  val tickTask = context.system.scheduler.schedule(5.seconds, 10000.millis, self, Tick)

  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)

  val from = Cluster(context.system).selfAddress.hostPort

  override def postStop(): Unit = {
    super.postStop()
    tickTask.cancel()
  }

  val receive: Receive = stop orElse {
    case Tick =>
      for (i <- 0 until authors.size)
        listingsRegion ! RemoveFirstN(authors(i), 3)
  }

  def stop: Receive = {
    case Stop =>
      tickTask.cancel()
      self ! PoisonPill
      sender() ! Stopped
  }
}
object ChiefEditorBot {
  private case object Tick
  case object Stop
  case object Stopped
  def props(authors: Map[Int, String]): Props =
    Props(new ChiefEditorBot(authors))
}

