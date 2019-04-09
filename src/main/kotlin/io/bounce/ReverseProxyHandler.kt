package io.bounce

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.withTimeout

class ReverseProxyHandler(private val client: WebClient, private val timeout: Int) {
  suspend fun handle(ctx: RoutingContext) {
    val incoming: HttpServerRequest = ctx.request()
    val request = client.request(incoming.method(), incoming.uri())
        .also { req -> copyHeaders(incoming, req) }
    val response = withTimeout(timeout.toLong()) {
      awaitResult<HttpResponse<Buffer>> { handler ->
        request.sendBuffer(ctx.body, handler)
      }
    }

    ctx.response()
        .setStatusCode(response.statusCode())
        .also { res -> copyHeaders(response, res) }
        .also { res -> copyBody(response, res) }
        .end()
  }

  private fun copyBody(incomingResponse: HttpResponse<Buffer>, res: HttpServerResponse) {
    if (incomingResponse.body() != null) {
      res.write(incomingResponse.body())
    }
  }

  private fun copyHeaders(incomingResponse: HttpResponse<Buffer>, res: HttpServerResponse) {
    incomingResponse.headers().entries().forEach { (k, v) -> res.putHeader(k, v) }
  }

  private fun copyHeaders(incoming: HttpServerRequest, req: HttpRequest<Buffer>) {
    incoming.headers().forEach { (k, v) -> req.putHeader(k, v) }
  }
}