import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * CONTROL TEST — closed model, long-lived users.
 * 5 users start at once and each loops for 3 minutes.
 * Only 5 distinct Gatling user IDs exist for the whole test.
 * True concurrency: flat 5. Expected BlazeMeter VU report: flat 5.
 */
class ClosedAtOnceLoop extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/ClosedAtOnceLoop")

  val scn = scenario("ClosedAtOnceLoop")
    .during(3.minutes) {
      exec(http("home").get("/"))
        .pause(1)
        .exec(http("reserve").get("/reserve.php?fromPort=Paris&toPort=London"))
        .pause(1)
    }

  setUp(
    scn.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
}
