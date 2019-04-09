package io.bounce

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit.WireMockClassRule
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.core.DeploymentOptions
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.ext.web.client.WebClientOptions
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import kotlin.random.Random

@RunWith(VertxUnitRunner::class)
class ReverseProxyTest {

  @Rule
  @JvmField
  var wireMockRule = WireMockClassRule(0)

  private var verticleName = ReverseProxy::class.java.canonicalName
  private lateinit var vertx: Vertx
  private lateinit var client: WebClient

  @Before
  fun before(context: TestContext) {
    vertx = Vertx.vertx(VertxOptions().setMaxEventLoopExecuteTime(1L))
    val async = context.async()
    val port = Random.nextInt(0, 65535)
    val config = json {
      obj(
          "http.port" to port,
          "routes" to buildRoutes()
      )
    }
    val deploymentOptions = DeploymentOptions(config = config)
    vertx.deployVerticle(verticleName, deploymentOptions) { res ->
      if (res.succeeded()) {
        client = WebClient.create(vertx,
            WebClientOptions(defaultHost = "127.0.0.1", defaultPort = port))
      } else {
        res.cause().printStackTrace()
        context.fail("Verticle deployment failed!")
      }
      async.complete()
    }
  }

  @After
  fun after(context: TestContext) {
    client.close()
    vertx.close(context.asyncAssertSuccess())
  }

  @Test
  fun reverseProxy_withBody_200(context: TestContext) {

    val async = context.async()

    val mockResponse = json {
      obj("something" to "something")
    }

    stubFor(get(urlEqualTo("/test"))
        .willReturn(aResponse().withStatus(200).withBody(mockResponse.encode())))

    client.get("/test")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 200)
            context.assertEquals(httpResponse.bodyAsJsonObject(), mockResponse)
            async.complete()
          } else {
            context.fail(response.cause())
          }
        }
  }

  @Test
  fun reverseProxy_withHeader_200(context: TestContext) {

    val async = context.async()

    stubFor(get(urlEqualTo("/test")).withHeader("something", equalTo("something"))
        .willReturn(aResponse().withStatus(200)))

    client.get("/test")
        .putHeader("something", "something")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 200)
            async.complete()
          } else {
            context.fail(response.cause())
          }
        }
  }

  @Test
  fun reverseProxy_hostNotFound_500(context: TestContext) {

    val async = context.async()

    client.get("/host-not-found")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 500)
            async.complete()
          } else {
            context.fail()
          }
        }
  }

  @Test
  fun reverseProxy_returnsError_500(context: TestContext) {

    val async = context.async()

    stubFor(get(urlEqualTo("/test"))
        .willReturn(aResponse().withStatus(500)))

    client.get("/test")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 500)
            async.complete()
          } else {
            context.fail(response.cause())
          }
        }
  }

  @Test
  fun reverseProxy_timeout_500(context: TestContext) {

    val async = context.async()

    stubFor(get(urlEqualTo("/test"))
        .willReturn(aResponse().withFixedDelay(2000).withStatus(200)))

    client.get("/test")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 500)
            async.complete()
          } else {
            context.fail(response.cause())
          }
        }
  }

  @Test
  fun reverseProxy_noStub_404(context: TestContext) {

    val async = context.async()

    client.get("/test")
        .send { response ->
          if (response.succeeded()) {
            val httpResponse = response.result()
            context.assertNotNull(httpResponse)
            context.assertTrue(httpResponse.statusCode() == 404)
            async.complete()
          } else {
            context.fail(response.cause())
          }
        }
  }

  private fun buildRoutes(): JsonArray = json {
    array(
        validRoute(),
        hostNotFound()
    )
  }

  private fun hostNotFound(): JsonObject = json {
    obj(
        "path" to "/host-not-found",
        "host" to UUID.randomUUID().toString(),
        "method" to "GET",
        "port" to wireMockRule.port()
    )
  }

  private fun validRoute(): JsonObject = json {
    obj(
        "path" to "/test",
        "host" to "127.0.0.1",
        "method" to "GET",
        "port" to wireMockRule.port()
    )
  }
}
