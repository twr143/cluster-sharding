package sample.blog
import java.util.UUID
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.pattern._
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import sample.blog.AuthorListing.Posts
import scala.util.Random
object PostCreatorBot {
  case object Stop
  case object Stopped
  private case object Tick
  def props(authors: Map[Int, String]): Props =
    Props(new PostCreatorBot(authors))
}
class PostCreatorBot(authors: Map[Int, String]) extends Actor with ActorLogging {
  import PostCreatorBot._
  import context.dispatcher
  implicit val timeout = Timeout(3.seconds)

  val tickTask = context.system.scheduler.schedule(1.seconds, 100.millis, self, Tick)

  val postRegion = ClusterSharding(context.system).shardRegion(Post.shardName)

  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)

  val from = Cluster(context.system).selfAddress.hostPort

  var n = 0

  def currentAuthor = authors(n % authors.size)

  var title = ""

  def receive = create

  var stopped = false

  val create: Receive = stop orElse {
    case Tick =>
      if (!stopped) {
        val postId = UUID.randomUUID().toString
        n += 1
        title = s"Post $n from $from"
        postRegion ! Post.AddPost(postId, Post.PostContent(currentAuthor, title, "..."))
        context.become(edit(postId))
      } else {
        tickTask.cancel()
        self ! PoisonPill
      }
  }

  def edit(postId: String): Receive = stop orElse {
    case Tick =>
      postRegion ! Post.ChangeBody(postId, "Something very interesting ...")
      context.become(updateTitle(postId))
  }

  def updateTitle(postId: String): Receive = stop orElse {
    case Tick =>
      postRegion ! Post.UpdateTitle(postId, title + " new one")
      context.become(publish(postId))
  }

  def publish(postId: String): Receive = stop orElse {
    case Tick =>
      postRegion ! Post.Publish(postId)
      context.become(list(postId))
  }

  def list(postId: String): Receive = stop orElse {
    case Tick =>
      listingsRegion ! AuthorListing.GetPosts(currentAuthor)
    case AuthorListing.Posts(summaries) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      context.become(/*changeAuthor(postId)*/ create)
  }

  def changeAuthor(postId: String): Receive = stop orElse {
    case Tick =>
      (listingsRegion ? AuthorListing.GetPosts(currentAuthor)).mapTo[Posts].map {
        posts =>
          val newAuthor = authors(Random.nextInt(authors.size))
          val title = posts.list.last.title
          val author = posts.list.last.author
          postRegion ! Post.UpdateAuthor(postId, newAuthor)
          listingsRegion ! posts.list.last.copy(author = newAuthor)
          listingsRegion ! AuthorListing.RemovePost(currentAuthor, postId)
          log.info("reassigned {} from {} to {}", title, author, newAuthor)
          context.become(create)
      }
  }

  def stop: Receive = {
    case Stop =>
      stopped = true
      sender() ! Stopped
  }
}
