import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * HYPOTHESIS TEST A — closed model, short-lived users.
 * Gatling holds exactly 5 concurrent users for 3 minutes, but each user
 * runs a short scenario (~4s) and is REPLACED by a brand-new user with a
 * new user ID when it finishes.
 * True concurrency: flat 5. Distinct user IDs over the test: several hundred.
 * If BlazeMeter derives VUs from cumulative/distinct user IDs, the VU chart
 * will climb linearly here despite flat true concurrency and flat throughput.
 */
class ClosedConstantConcurrent extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/ClosedConstantConcurrent")

  val scn = scenario("ClosedConstantConcurrent")
    .exec(http("home").get("/"))
    .pause(1)
    .exec(http("reserve").get("/reserve.php?fromPort=Paris&toPort=London"))
    .pause(1)

  setUp(
    scn.inject(constantConcurrentUsers(5).during(3.minutes))
  ).protocols(httpProtocol)
}
