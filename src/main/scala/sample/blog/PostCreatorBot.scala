package sample.blog
import java.util.UUID
import akka.actor.FSM.Event
import scala.concurrent.duration._
import akka.actor.{AbstractLoggingFSM, Actor, ActorLogging, ActorRef, FSM, PoisonPill, Props}
import akka.pattern._
import akka.cluster.Cluster
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import sample.blog.AuthorListing.Posts
import sample.blog.PostCreatorBot._
import scala.util.Random
object PostCreatorBot {
  case object Stop
  case object Stopped
  private case object Tick
  def props(authors: Map[Int, String]): Props =
    Props(new PostCreatorBot(authors))
  sealed trait State
  case object Creating extends State
  case object EditingBody extends State
  case object UpdatingTitle extends State
  case object Publishing extends State
  case object GettingListOfArticles extends State
  final case class StateData(postId: String, title: String, authorIndex: Integer, stopped: Boolean, postsQueried: Boolean = false)
  val defaultStateData = StateData("", "", 0, stopped = false)

  val stopEvent = Stop
}
class PostCreatorBot(authors: Map[Int, String]) extends FSM[State, StateData] {
  import PostCreatorBot._
  import context.dispatcher
  implicit val timeout = Timeout(3.seconds)

  val tickTask = context.system.scheduler.schedule(1.seconds, 20.millis, self, Tick)

  val postRegion = ClusterSharding(context.system).shardRegion(Post.shardName)

  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)

  val from = Cluster(context.system).selfAddress.hostPort

  def currentAuthor = authors(stateData.authorIndex % authors.size)

  startWith(Creating, defaultStateData)

  when(Creating) {
    case Event(Tick, _) =>
      if (!stateData.stopped)
        goto(EditingBody) using stateData.copy(
          postId = UUID.randomUUID().toString,
          title = s"Post ${stateData.authorIndex + 1} from $from",
          authorIndex = stateData.authorIndex + 1)
      else {
        tickTask.cancel()
        self ! PoisonPill
        stay
      }
    case Event(Stop, _) =>
      tickTask.cancel()
      self ! PoisonPill
      stay replying Stopped
  }

  when(EditingBody)(stopEventHandler orElse {
    case Event(Tick, _) =>
      goto(UpdatingTitle)
  })

  when(UpdatingTitle)(stopEventHandler orElse {
    case Event(Tick, _) =>
      goto(Publishing)
  })

  when(Publishing)(stopEventHandler orElse {
    case Event(Tick, _) =>
      goto(GettingListOfArticles)
  })

  when(GettingListOfArticles)(stopEventHandler orElse {
    case Event(Tick, stateData) =>
      if (!stateData.postsQueried) // otherwise queried several times in the first seconds of app run
        listingsRegion ! AuthorListing.GetPosts(currentAuthor)
      stay using stateData.copy(postsQueried = true)
    case Event(AuthorListing.Posts(summaries), _) =>
      log.info("Posts by {}: {}", currentAuthor, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      goto(Creating) using stateData.copy(postsQueried = false)
  })

  def stopEventHandler: StateFunction = {
    case Event(Stop, _) =>
      stay() using stateData.copy(stopped = true) replying Stopped
  }

  onTransition {
    case Creating -> EditingBody =>
      postRegion ! Post.AddPost(nextStateData.postId, Post.PostContent(authors(nextStateData.authorIndex % authors.size), nextStateData.title, "..."))
    case EditingBody -> UpdatingTitle =>
      postRegion ! Post.ChangeBody(nextStateData.postId, "Something very interesting ...")
    case UpdatingTitle -> Publishing =>
      postRegion ! Post.UpdateTitle(nextStateData.postId, nextStateData.title + " new one")
    case Publishing -> GettingListOfArticles =>
      postRegion ! Post.Publish(nextStateData.postId)
  }

  whenUnhandled {
    case Event(evt, stateData) =>
      log.warning("unhandled: {} {} {} {}", evt, stateName, stateData, currentAuthor)
      stay
  }
}
