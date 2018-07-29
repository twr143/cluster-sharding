package sample.blog.util
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.{FSMState, LogEntry}
/**
  * Created by Ilya Volynin on 29.07.2018 at 9:09.
  */
trait FSMLoggingSupport[S <: FSMState, D, E] { self: PersistentFSM[S, D, E] =>

  def prettyPrint(logEntries: IndexedSeq[LogEntry[S, D]]): String = {
    def itemToStr(e: LogEntry[S, D]) = s"in state: ${e.stateName}\nwidth data: ${e.stateData}\nreceived: ${e.event.toString}"
    s"Last ${logEntries.size} entries leading up to this point:\n${logEntries.map(itemToStr).mkString("\n")}"
  }
}
