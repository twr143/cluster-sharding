package sample.blog
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged}
import sample.blog.AuthorListing.{PostSummary, PostSummaryEx}

/**
  * Created by Ilya Volynin on 28.09.2018 at 11:47.
  */
class ActorListingEventAdapter extends EventAdapter {

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = identity(event)

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    val e = event match {
      case evt: PostSummary =>
        PostSummaryEx.fromPostSummary(evt)
      case any => any
    }
    EventSeq(e)
  }
}
