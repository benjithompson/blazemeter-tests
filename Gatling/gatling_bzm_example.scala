import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class DemoBlazeApiSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("https://demoblaze.com")
    .inferHtmlResources()
    .acceptHeader("*/*")
    .contentTypeHeader("application/json")

  val scn = scenario("API Simulation")
    .exec(http("Get Products")
      .get("/")
      .check(status.is(200)))
    .pause(1.second)
    .exec(http("Get Product Details")
      .get("/prod.html?idp_=1")
      .check(status.is(200)))
    .pause(1.second)
    .exec(http("Add Product to Cart")
      .post("/prod.html?idp_=1#")
      .body(StringBody("""{"id":1}""")).asJson
      .check(status.is(200)))
    .pause(1.second)
    .exec(http("Get Cart")
      .get("/cart.html")
      .check(status.is(200)))
    .pause(1.second)

  setUp(
    scn.inject(
      rampUsers(10) during (10.seconds)
    )
  ).protocols(httpProtocol)
   .maxDuration(5.minutes)
}