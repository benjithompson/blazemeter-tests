import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * RPS-TARGET TEST — open model where VUs auto-scale to sustain a request rate.
 * 10 users arrive per second for 3 minutes; each performs exactly ONE request
 * and terminates. Request rate therefore equals the arrival rate: flat 10 rps
 * (~1800 total users over the run).
 * Active VUs self-adjust via Little's law: VUs = rps x response time
 * (e.g. 10 rps x 0.3s = ~3 concurrent). If blazedemo.com slows down, VUs rise
 * automatically to hold the rate; if it speeds up, they fall.
 * Expected BlazeMeter report: flat low single-digit VUs tracking latency,
 * flat 10 rps throughput — NOT a linear climb (contrast with CVU 4).
 */
class RpsTargetOpen extends Simulation {

  val httpProtocol = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/RpsTargetOpen")

  val scn = scenario("RpsTargetOpen")
    .exec(http("home").get("/"))

  setUp(
    scn.inject(constantUsersPerSec(10).during(3.minutes))
  ).protocols(httpProtocol)
}
