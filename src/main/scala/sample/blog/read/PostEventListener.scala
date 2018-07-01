package sample.blog.read
import java.sql.BatchUpdateException
import java.util.UUID
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.event.Logging
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
import scala.util.{Failure, Success}
/**
  * Created by Ilya Volynin on 28.06.2018 at 13:44.
  *
  * truncates es_posts table at the start
  * "unwinds" all post events at the start, refilling es_posts table
  * handles current post events updating es_posts table
  */
class PostEventListener(db: Database) extends Actor with ActorLogging {
  import PostEventListener._
  implicit val mat: Materializer = ActorMaterializer()

  implicit val ec = context.dispatcher

  val readJournal: JdbcReadJournal = PersistenceQuery(context.system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("postTag", 0L)

  private val completed = willNotCompleteTheStream.runWith(Sink.actorRef(self, StreamCompleted))

  private val flushTask = context.system.scheduler.schedule(1.second, 2.second, self, Flush)

  val postList = TableQuery[Posts]

  val setupAction: DBIO[Unit] = DBIO.seq(postList.schema.truncate /*, postList.schema.create*/)

  db.run(setupAction.asTry).map {
    case Failure(ex) => log.error("error {} {}", ex.getMessage, ex.getCause)
    case Success(x) => x
  }

  var currentList = List.empty[postList.shaped.shape.Unpacked]

  val filterPostAndGetBody = Compiled { id: Rep[UUID] =>
    postList.filter(_.id === id).map(_.body)
  }

  val filterPostAndGetTitle = Compiled { id: Rep[UUID] =>
    postList.filter(_.id === id).map(_.title)
  }

  var updateAction: DBIOAction[Int, NoStream, Effect.Write] = DBIOAction.successful(0)

  override def receive: Receive = {
    case StreamCompleted =>
      log.warning("view shutting down")
      self ! PoisonPill
    case EventEnvelope(offset, persistenceId, sequenceNr, event) =>
      event match {
        case pa: PostAdded =>
          currentList :+= (UUID.fromString(pa.postId), pa.content.author, pa.content.title, pa.content.body, pa.time)
          log.info("post added in event listener {} {}", pa.postId, pa.content)
        case BodyChanged(id, b) =>
          updateAction = updateAction.andThen(filterPostAndGetBody(UUID.fromString(id)).update(b))
          log.info("post body changed {} {}", id, b)
        case PostPublished =>
        case TitleUpdated(id, o, n) =>
          updateAction = updateAction.andThen(filterPostAndGetTitle(UUID.fromString(id)).update(n))
          log.info("post title changed {} {}", id, n)
        case AuthorUpdated(n) =>
      }
    case Flush =>
      log.info("flush task works, size {} ", currentList.size)
      db.run(DBIO.seq(postList ++= currentList).andThen(updateAction).asTry).map {
        case Failure(ex) => log.error("error {} {}", ex.getMessage, ex.getCause)
        case Success(x) => x
      }
      currentList = List.empty[postList.shaped.shape.Unpacked]
      updateAction = DBIOAction.successful(0)
  }
  private case object Flush
}
object PostEventListener {
  case object StreamCompleted
  def props(db: Database) = Props(new PostEventListener(db))
}
