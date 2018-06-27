package sample.blog.serialization
import java.util.logging.Logger

import akka.persistence.PersistentRepr
import akka.serialization.Serializer
import org.json4s.{DefaultFormats, Formats, NoTypeHints}
import org.json4s.jackson.Serialization
/**
  * Created by Ilya Volynin on 27.06.2018 at 12:33.
  */
class AllEventSerializer extends Serializer {
  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  val logger = Logger.getLogger("AllEventSerializer")

  override def identifier: Int = 9001009

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    implicit val mf = Manifest.classType(manifest.get)
    logger.info(s"manifest: $mf, incoming: ${new String(bytes)}")
    val obj = Serialization.read(new String(bytes))
    logger.info(s"obj decoded: $obj")
    obj
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    Serialization.write(o).getBytes("UTF-8")
  }
}
