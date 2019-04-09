package io.bounce

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.web.client.WebClientOptions
import kotlinx.coroutines.launch

class ReverseProxy : CoroutineVerticle() {

  private lateinit var httpServer: HttpServer

  override suspend fun start() {
    val httpPort = config.getInteger("http.port", 8080)
    val router: Router = Router.router(vertx)

    validateConfiguration(config)

    val routeList: JsonArray = config["routes"]

    routeList.forEach { config ->
      val routeConfig = config as JsonObject
      val method: String = routeConfig["method"]
      val path: String = routeConfig["path"]
      val timeout: Int = routeConfig.getInteger("timeout", 1000)
      val route = router.route(HttpMethod.valueOf(method), path)
      route.coroutineHandler { ctx -> ReverseProxyHandler(webClient(routeConfig), timeout).handle(ctx) }
    }

    httpServer = vertx.createHttpServer()
        .requestHandler(router)
        .listenAwait(httpPort)

    log().info("Reverse Proxy deployed on port $httpPort.")
  }

  private fun webClient(route: JsonObject) =
      WebClient.create(vertx, WebClientOptions(
          defaultHost = route["host"],
          defaultPort = route["port"],
          connectTimeout = route.getInteger("timeout", 1000))
      )

  private fun validateConfiguration(config: JsonObject) {
    if (config.getJsonArray("routes").isEmpty) {
      throw RuntimeException("No routes in your configuration file.")
    }
  }

  override suspend fun stop() {
    httpServer.close()
  }

  private fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
      launch(ctx.vertx().dispatcher()) {
        try {
          fn(ctx)
        } catch (e: Throwable) {
          ctx.fail(e)
        }
      }
    }
  }
}
