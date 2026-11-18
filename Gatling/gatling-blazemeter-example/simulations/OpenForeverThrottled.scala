import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * REPRODUCTION TEST — deliberately ever-growing VUs with flat throughput.
 * 1 new user arrives per second for 3 minutes, and every user loops FOREVER,
 * so no user ever completes: active VUs climb linearly 0 -> ~180.
 * A throttle caps request rate at 10 rps, so throughput stays flat and the
 * accumulating users just sit idle in the throttle queue (low CPU/memory).
 * This pattern targets a fixed RPS with open
 * injection while users never terminate.
 */
class OpenForeverThrottled extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/OpenForeverThrottled")

  val scn = scenario("OpenForeverThrottled")
    .forever {
      exec(http("home").get("/"))
        .pause(2)
    }

  setUp(
    scn.inject(constantUsersPerSec(1).during(3.minutes))
  ).throttle(
    reachRps(10).in(10.seconds),
    holdFor(3.minutes)
  ).protocols(httpProtocol)
   .maxDuration(3.minutes)
}
