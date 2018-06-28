package sample.blog.read
import java.util.UUID

import slick.lifted.{ProvenShape, Rep, Tag}
import slick.jdbc.PostgresProfile.api._

/**
  * Created by Ilya Volynin on 28.06.2018 at 15:34.
  */
class Posts(tag: Tag)
  extends Table[(UUID, String, String, String)](tag, "SUPPLIERS") {

  // This is the primary key column:
  def id: Rep[UUID] = column[UUID]("ID", O.PrimaryKey)
  def author: Rep[String] = column[String]("author")
  def title: Rep[String] = column[String]("title")
  def body: Rep[String] = column[String]("body")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(UUID, String, String, String)] =
    (id, author, title, body)
}