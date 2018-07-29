package sample.blog
import java.time.{OffsetDateTime, ZonedDateTime}
import scala.concurrent.duration._
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.dispatch.sysmsg.Create
import akka.persistence.PersistentActor
import akka.persistence.fsm.{LoggingPersistentFSM, PersistentFSM}
import akka.persistence.fsm.PersistentFSM.FSMState
import sample.blog.Post.{Event, StateData, Status}
import sample.blog.util.FSMLoggingSupport
import scala.reflect.{ClassTag, classTag}
object Post {
  def props(authorListing: ActorRef): Props =
    Props(new Post(authorListing))
  object PostContent {
    val empty = PostContent("", "", "")
  }
  case class PostContent(author: String, title: String, body: String)
  sealed trait Command {
    def postId: String
  }
  case class AddPost(postId: String, content: PostContent) extends Command
  case class GetContent(postId: String) extends Command
  case class ChangeBody(postId: String, body: String) extends Command
  case class Publish(postId: String) extends Command
  case class UpdateTitle(postId: String, newTitle: String) extends Command
  case class UpdateAuthor(postId: String, newAuthor: String) extends Command
  case class Remove(postId: String) extends Command
  sealed trait Event
  object Event {
    //    implicit val format: Format[Event] = Json.format[Event]
  }
  case class PostAdded(postId: String, content: PostContent, time: OffsetDateTime) extends Event
  case class BodyChanged(postId: String, body: String) extends Event
  case class PostPublished(postId: String) extends Event
  case class TitleUpdated(postId: String, oldTitle: String, newTitle: String) extends Event
  case class AuthorUpdated(newAuthor: String) extends Event
  case class Removed(postId: String) extends Event
  val idExtractor: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.postId, cmd)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.postId.hashCode) % 100).toString
  }

  val shardName: String = "Post"
  case class StateData(postId: String, content: PostContent, published: Boolean, added: OffsetDateTime) {
    def updated(evt: Event): StateData = evt match {
      case PostAdded(id, c, time) => copy(postId = id, content = c, added = time)
      case BodyChanged(_, b) => copy(content = content.copy(body = b))
      case PostPublished(_) => copy(published = true)
      case TitleUpdated(_, o, n) => copy(content = content.copy(title = n))
      case AuthorUpdated(n) => copy(content = content.copy(author = n))
    }
  }
  sealed trait Status extends FSMState
  case object Initial extends Status {
    override def identifier: String = "Initial"
  }
  case object Created extends Status {
    override def identifier: String = "Created"
  }
  case object Published extends Status {
    override def identifier: String = "Published"
  }
  case object Removed extends Status {
    override def identifier: String = "Removed"
  }
}
class Post(authorListing: ActorRef) extends PersistentFSM[Status, StateData, Event]
  with ActorLogging
  with LoggingPersistentFSM[Status, StateData, Event]
  with FSMLoggingSupport[Status, StateData, Event] {
  import Post._
  // self.path.parent.name is the type name (utf-8 URL-encoded)
  // self.path.name is the entry identifier (utf-8 URL-encoded)
  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def logDepth: Int = 8

  override def domainEventClassTag: ClassTag[Post.Event] = classTag[Post.Event]

  // passivate the entity when no activity
  context.setReceiveTimeout(30.seconds)

  startWith(Initial, StateData("", PostContent.empty, published = false, OffsetDateTime.now()))

  override def applyEvent(domainEvent: Post.Event, stateData: StateData): StateData =
    domainEvent match {
      case _: Removed => stateData
      case evt: Post.Event =>
        stateData.updated(evt)
    }

  when(Initial) {
    case Event(AddPost(postId, content), stateData) =>
      log.info("persistence id: {}", persistenceId)
      if (content.author != "" && content.title != "") {
        log.debug("New post saved: {}", stateData.content.title)
        goto(Created) applying PostAdded(postId, content, OffsetDateTime.now())
      } else stay()
  }

  when(Created)(getContent orElse {
    case Event(ChangeBody(id, body), stateData) =>
      log.debug("Post changed: {}", stateData.content.title)
      stay() applying BodyChanged(id, body)
    case Event(UpdateTitle(id, newTitle), stateData) =>
      log.debug("Title changed: {}", stateData.content.title)
      stay() applying TitleUpdated(id, stateData.content.title, newTitle)
    case Event(Publish(postId), stateData) =>
      goto(Published) applying PostPublished(postId)
  })

  when(Published)(getContent orElse remove orElse {
    case Event(UpdateAuthor(_, newAuthor), stateData) =>
      log.info("Author changed: {}", stateData.content.author)
      stay() applying AuthorUpdated(newAuthor)
  })

  when(Removed) {
    case Event(ReceiveTimeout, stateData) =>
      context.parent ! Passivate(stopMessage = PoisonPill)
      stay()
    case Event(event, stateData) =>
      log.info("event {} has come into removed in FSM for the author: {}", event, stateData.content.author)
      stay()
  }

  whenUnhandled {
    case Event(command, stateData) =>
      log.warning(s"Unhandled event: $command\n${prettyPrint(getLog)}")
      stay()
  }

  onTransition {
    case Created -> Published =>
      val c = stateData.content
      log.info("Post published: {}", c.title)
      val ps = AuthorListing.PostSummary(c.author, stateData.postId, c.title, OffsetDateTime.now())
      authorListing ! ps
  }

  def remove: StateFunction = {
    case Event(Remove(postId), stateData) =>
      log.debug("Post removed: {}", stateData.content.title)
      goto(Removed) applying Removed(postId)
  }

  def getContent: StateFunction = {
    case Event(GetContent(_), stateData) => stay() replying stateData.content
  }
}
