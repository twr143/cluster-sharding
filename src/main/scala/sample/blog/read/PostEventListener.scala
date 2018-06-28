package sample.blog.read
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import sample.blog.Post._
import slick.jdbc.PostgresProfile.backend.Database
import scala.concurrent.duration._

/**
  * Created by Ilya Volynin on 28.06.2018 at 13:44.
  */
class PostEventListener(db: Database) extends Actor with ActorLogging {
  import PostEventListener._
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec = context.dispatcher

  val readJournal: JdbcReadJournal = PersistenceQuery(context.system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("postTag", 0L)
    val completed = willNotCompleteTheStream.runWith(Sink.actorRef(self, StreamCompleted))
//  val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("postTag", 0L)
    val flushTask = context.system.scheduler.schedule(1.second, 1.second, self, Flush)

  override def receive: Receive = {
    case StreamCompleted =>
      log.warning("view shutting down")
      self ! PoisonPill
    case EventEnvelope(offset, persistenceId, sequenceNr, event) =>
      event match {
        case pa: PostAdded =>
          log.info("post added in event listener {}", pa.content)
        case BodyChanged(b) =>
        case PostPublished =>
        case TitleUpdated(o, n) =>
        case AuthorUpdated(n) =>
      }
  }
  private case object Flush
}
object PostEventListener {
  case object StreamCompleted
  def props(db: Database) = Props(new PostEventListener(db))
}
