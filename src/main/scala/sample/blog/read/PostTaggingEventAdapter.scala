package sample.blog.read
import akka.persistence.journal.{EventAdapter, EventSeq, Tagged, WriteEventAdapter}
/**
  * Created by Ilya Volynin on 28.06.2018 at 13:50.
  */
class PostTaggingEventAdapter extends EventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tag: String) = Tagged(event, Set(tag))

  override def toJournal(event: Any): Any = event match {
    case _ => withTag(event, "postTag")
  }
  override def fromJournal(event: Any, manifest: String): EventSeq = {
      EventSeq(event)
  }

}