import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * HYPOTHESIS TEST B — open model, constant arrival rate.
 * 2 new users arrive per second for 3 minutes (~360 total users).
 * Each user runs a short scenario (~4s) and terminates, so true concurrency
 * hovers around 4-6 and throughput is flat.
 * If BlazeMeter counts distinct
 * Gatling user IDs as VUs, the reported VU/CVU max will climb linearly for
 * the whole test while throughput and engine CPU/memory stay flat.
 */
class OpenConstantArrival extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/OpenConstantArrival")

  val scn = scenario("OpenConstantArrival")
    .exec(http("home").get("/"))
    .pause(1)
    .exec(http("reserve").get("/reserve.php?fromPort=Paris&toPort=London"))
    .pause(1)

  setUp(
    scn.inject(constantUsersPerSec(2).during(3.minutes))
  ).protocols(httpProtocol)
}
