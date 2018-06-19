package sample.blog
import java.util.UUID
import scala.concurrent.duration._
import akka.actor.Actor
import akka.pattern._
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import sample.blog.AuthorListing.Posts
import scala.util.Random
object Bot {
  private case object Tick
}
class Bot extends Actor with ActorLogging {
  import Bot._
  import context.dispatcher
  implicit val timeout = Timeout(3.seconds)
  val tickTask = context.system.scheduler.schedule(2.seconds, 1.seconds, self, Tick)
  val postRegion = ClusterSharding(context.system).shardRegion(Post.shardName)
  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)
  val from = Cluster(context.system).selfAddress.hostPort

  override def postStop(): Unit = {
    super.postStop()
    tickTask.cancel()
  }

  var n = 0
  val authors = Map(0 -> "Patrik", 1 -> "Martin", 2 -> "Roland", 3 -> "Björn", 4 -> "Endre")

  def currentAuthor = authors(n % authors.size)

  var title = ""

  def receive = create

  val create: Receive = {
    case Tick =>
      val postId = UUID.randomUUID().toString
      n += 1
      title = s"Post $n from $from"
      postRegion ! Post.AddPost(postId, Post.PostContent(currentAuthor, title, "..."))
      context.become(edit(postId))
  }

  def edit(postId: String): Receive = {
    case Tick =>
      postRegion ! Post.ChangeBody(postId, "Something very interesting ...")
      context.become(updateTitle(postId))
  }

  def updateTitle(postId: String): Receive = {
    case Tick =>
      postRegion ! Post.UpdateTitle(postId, title + " new one")
      context.become(publish(postId))
  }

  def publish(postId: String): Receive = {
    case Tick =>
      postRegion ! Post.Publish(postId)
      context.become(list(postId))
  }

  def list(postId: String): Receive = {
    case Tick =>
      listingsRegion ! AuthorListing.GetPosts(currentAuthor)
    case AuthorListing.Posts(summaries) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      context.become(changeAuthor(postId))
  }

  def changeAuthor(postId: String): Receive = {
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
}
