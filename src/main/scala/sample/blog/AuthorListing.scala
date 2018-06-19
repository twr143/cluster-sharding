package sample.blog

import scala.collection.immutable
import scala.concurrent.duration._
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.PoisonPill
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.PersistentActor

object AuthorListing {

  def props(): Props = Props(new AuthorListing)

  sealed trait Command {
    def author: String
  }
  case class PostSummary(author: String, postId: String, title: String) extends Command
  case class GetPosts(author: String) extends Command
  case class Posts(list: List[PostSummary])
  case class RemovePost(author: String, postId: String) extends Command

  val idExtractor: ShardRegion.ExtractEntityId = {
    case c: Command => (c.author, c)
  }

  val shardResolver: ShardRegion.ExtractShardId = msg => msg match {
    case c: Command   => (math.abs(c.author.hashCode) % 100).toString
  }

  val shardName: String = "AuthorListing"
}

class AuthorListing extends PersistentActor with ActorLogging {
  import AuthorListing._

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  var posts = List.empty[PostSummary]

  def receiveCommand = {
    case s: PostSummary =>
      persist(s) { evt =>
        posts :+= evt
        log.info("Post added to {}'s list: {}", s.author, s.title)
      }
    case r: RemovePost =>
      persist(r) { evt =>
        val post = posts.filter(_.postId == r.postId).head
        posts = posts.filter(ps => ps.postId != r.postId)
        log.info("Post removed {} from {}'s list", r.postId, post.author)
      }

    case GetPosts(_) =>
      sender() ! Posts(posts)
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
  }

  override def receiveRecover: Receive = {
    case evt: PostSummary => posts :+= evt
    case r: RemovePost =>
      posts = posts.filter(ps => ps.postId != r.postId)

  }

}