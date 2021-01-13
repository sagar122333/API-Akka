
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import spray.json._

import org.mongodb.scala._
import org.mongodb.scala.model._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util._


case class Client(id: String,
                  name: String,
                  inboundFeedUrl: String)
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val printer = PrettyPrinter
  implicit val clientFormat = jsonFormat3(Client)
}
object AkkaHTTPServerServer extends App with JsonSupport {
  implicit val system = ActorSystem("akka-http-rest-server")
  //  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher


  val host = "127.0.0.1"
  val port = 8080

  val routes: Route =
    concat(
      path("") {
        get {
          parameters('page.as[Int], 'limit.as[Int]){ (page, limit) => {
            val res = Await.result(DB.clients.find().skip((page-1)*limit).limit(limit).toFuture(), 10 seconds)
            complete(res)}
          }
        }
      },
      path("") {
        post {
          entity(as[Client]) { client => {
            Await.result(DB.clients.insertOne(client).toFuture(), 10 seconds)
            complete(StatusCodes.Created, "Created") }
          }
        }
      },
      path(""){
        put {
          parameters('id.as[String]){id => {
            entity(as[Client]){ client => {
              Await.result(DB.clients.replaceOne(Filters.eq("id", id), client).toFuture(), 10 seconds)}
              complete(StatusCodes.Created, "Updated")
            }}
          }
        }
      },
      path(""){
        delete {
          parameters('id.as[String]){id => {
            Await.result(DB.clients.deleteOne(Filters.eq("id", id)).toFuture(), 10 seconds)
            complete(StatusCodes.Created, "Deleted")}
          }
        }
      }
    )


  val httpServerFuture = Http().bindAndHandle(routes, host, port)
  httpServerFuture.onComplete {
    case Success(binding) =>
      println(s"Akka Http Server is UP and is bound to ${binding.localAddress}")

    case Failure(e) =>
      println(s"Akka Http server failed to start", e)
      system.terminate()
  }

  StdIn.readLine()
  httpServerFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}



object DB {
  import org.bson.codecs.configuration.CodecRegistries._
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  private val customCodecs = fromProviders(classOf[Client])
  private val codecRegistry = fromRegistries(customCodecs,
    DEFAULT_CODEC_REGISTRY)

  private val database: MongoDatabase = MongoClient().getDatabase("Joveo")
    .withCodecRegistry(codecRegistry)

  val clients: MongoCollection[Client] = database.getCollection("Employee")
}
