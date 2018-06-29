package sample.blog.read
import java.util.UUID
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import sample.blog.Post._
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.PostgresProfile.backend.Database
import scala.concurrent.duration._
import sample.blog.util.MyPostgresProfile.api._
import slick.jdbc.JdbcProfile
import slick.sql.FixedSqlAction
import scala.concurrent.Future
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
  val flushTask = context.system.scheduler.schedule(1.second, 2.second, self, Flush)

  val postList = TableQuery[Posts]

  val setupAction: DBIO[Unit] = DBIO.seq(postList.schema.truncate /*, postList.schema.create*/)

  db.run(setupAction)

  var currentList = List.empty[postList.shaped.shape.Unpacked]

  val filterPostAndGetBody = Compiled { id: Rep[UUID] =>
    postList.filter(_.id === id).map(_.body)
  }

  val filterPostAndGetTitle = Compiled { id: Rep[UUID] =>
    postList.filter(_.id === id).map(_.title)
  }

  var updateAction: DBIOAction[Int, NoStream, Effect.Write] = DBIOAction.from(Future(0))

  override def receive: Receive = {
    case StreamCompleted =>
      log.warning("view shutting down")
      self ! PoisonPill
    case EventEnvelope(offset, persistenceId, sequenceNr, event) =>
      event match {
        case pa: PostAdded =>
          currentList :+= (UUID.fromString(pa.postId), pa.content.author, pa.content.title, pa.content.body, pa.time)
          log.debug("post added in event listener {} {}", pa.postId, pa.content)
        case BodyChanged(id, b) =>
          updateAction = updateAction.andThen(filterPostAndGetBody(UUID.fromString(id)).update(b))
          log.debug("post body changed {} {}", id, b)
        case PostPublished =>
        case TitleUpdated(id, o, n) =>
          updateAction = updateAction.andThen(filterPostAndGetTitle(UUID.fromString(id)).update(n))
          log.debug("post title changed {} {}", id, n)
        case AuthorUpdated(n) =>
      }
    case Flush =>
      log.info("flush task works, size {} ", currentList.size)
      db.run(DBIO.seq(postList ++= currentList).andThen(updateAction))
      currentList = List.empty[postList.shaped.shape.Unpacked]
      updateAction = DBIOAction.from(Future(0))
  }
  private case object Flush
}
object PostEventListener {
  case object StreamCompleted
  def props(db: Database) = Props(new PostEventListener(db))
}
