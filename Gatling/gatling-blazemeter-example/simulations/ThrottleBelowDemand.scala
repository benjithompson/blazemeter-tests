import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * SUBTLE ACCUMULATION TEST — throttle below demand, NO forever loop.
 * Looks innocent: 2 users arrive per second, each makes just 3 requests and
 * exits (~2s lifetime unthrottled). But demand is 2 x 3 = 6 rps while the
 * throttle caps at 4 rps — a 2 rps deficit. Users queue at the throttle and
 * their lifetimes stretch, so the backlog grows ~40 users/min: VUs climb
 * linearly to ~120 while throughput sits pinned flat at 4 rps.
 * Every user DOES eventually finish — nothing in the script says "run
 * forever" — which is why this pattern hides in plain sight in reviews.
 */
class ThrottleBelowDemand extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/ThrottleBelowDemand")

  val scn = scenario("ThrottleBelowDemand")
    .exec(http("home").get("/"))
    .pause(500.milliseconds)
    .exec(http("reserve").get("/reserve.php?fromPort=Paris&toPort=London"))
    .pause(500.milliseconds)
    .exec(http("home2").get("/"))

  setUp(
    scn.inject(constantUsersPerSec(2).during(3.minutes))
  ).throttle(
    reachRps(4).in(10.seconds),
    holdFor(4.minutes)
  ).protocols(httpProtocol)
   .maxDuration(4.minutes)
}
