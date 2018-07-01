package sample.blog.read
import java.time.{OffsetDateTime, ZonedDateTime}
import java.util.UUID

import sample.blog.util.MyPostgresProfile.api._
import slick.lifted.ProvenShape
/**
  * Created by Ilya Volynin on 28.06.2018 at 15:34.
  */
class Posts(tag: Tag)
  extends Table[(UUID, String, String, String, OffsetDateTime)](tag, "es_posts") {
  // This is the primary key column:
  def id: Rep[UUID] = column[UUID]("ID", O.PrimaryKey)

  def author: Rep[String] = column[String]("author")

  def title: Rep[String] = column[String]("title")

  def body: Rep[String] = column[String]("body")

  def added: Rep[OffsetDateTime] = column[OffsetDateTime]("adde")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(UUID, String, String, String, OffsetDateTime)] =
    (id, author, title, body, added)
}