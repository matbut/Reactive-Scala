package EShop.lab5

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class ProductCatalogHttpApp(actorSystem: ActorSystem) extends HttpApp {
  private implicit val timeout: Timeout                   = Timeout(10 seconds)
  private implicit val executionContext: ExecutionContext = ExecutionContext.global

  private val searchServicePath: String = "akka.tcp://ProductCatalog@127.0.0.1:2553/user/searchService"

  import EShop.lab5.ProductCatalog._
  override protected def routes: Route = {
    path("search") {
      post {
        entity(as[GetItems]) { query: Query =>
          complete {
            actorSystem
              .actorSelection(searchServicePath)
              .resolveOne()
              .map(_ ? query)
              .map(_.mapTo[Items])
          }
        }
      }
    }
  }
}

object ProductCatalogHttpApp extends App {
  private val config = ConfigFactory.load()

  val httpActorSystem: ActorSystem =
    ActorSystem("ProductCatalogApp", config.getConfig("productcatalogapp").withFallback(config))

  private val productCatalogSystem: ActorSystem =
    ActorSystem("ProductCatalog", config.getConfig("productcatalog").withFallback(config))

  productCatalogSystem.actorOf(ProductCatalog.props(new SearchService()), "searchService")

  val server = new ProductCatalogHttpApp(httpActorSystem)

  server.startServer("localhost", 9001, httpActorSystem)
}
