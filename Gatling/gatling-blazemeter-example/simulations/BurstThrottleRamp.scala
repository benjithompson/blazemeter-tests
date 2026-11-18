import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._

/**
 * BURST BEHIND A THROTTLE RAMP. A short injection burst behind a ramping
 * throttle. Two compounding traps that none of tests 1-6 cover:
 *
 * 1. RATE READ AS COUNT. A variable named `productConcurrentUsers` is fed to
 *    `constantUsersPerSec` — an ARRIVAL RATE, not a concurrency level. The
 *    author appears to intend "15 concurrent users"; Gatling injects 15 users
 *    PER SECOND for 10s = 150 users total.
 *
 * 2. THROTTLE RAMP (not the cap) IS THE DEFICIT. The throttle cap (20 rps)
 *    comfortably EXCEEDS demand (15 users/s x 1 request = 15 rps), so the usual
 *    review check "is my throttle above my demand?" passes. But `reachRps(20)
 *    in 10.seconds` ramps linearly 0 -> 20 rps, so the cap is below 15 rps for
 *    the first 7.5s — exactly while all the injection is happening. Arrivals
 *    outrun the served rate and pile up in the throttle queue:
 *      backlog(t) = 15t - t^2   ->   peak ~56 users at t = 7.5s
 *    then drains at 20 rps within a few seconds.
 *
 * Expected BlazeMeter report: a sharp VU SPIKE to ~55-60 in the first 10s that
 * drains back to 0, throughput capped flat at <=20 rps, and CVU (peak) ~3.7x
 * the "15" written in the script. Distinct signature from test 4 (VUs pegged
 * high until test end) and test 6 (linear climb across the whole test).
 */
class BurstThrottleRamp extends Simulation {

  val SMOKE_PROFILE = "SMOKE"

  val envProfile: String = System.getProperty("GATLING_PROFILE")
  val testProfile: String = if (envProfile != null) envProfile.toUpperCase else "LOAD"
  private val isSmoke: Boolean = testProfile == SMOKE_PROFILE

  // Misnamed on purpose — this is an arrival RATE (users/sec), not a concurrency level.
  val productConcurrentUsers: Int = if (isSmoke) 1 else 15
  val productTargetThroughput: Int = if (isSmoke) 1 else 20

  val concurrentUsersInjectionPeriod: FiniteDuration = 10.seconds
  val rumpUpPeriod: FiniteDuration = 10.seconds
  val holdTime: FiniteDuration = 1.minute
  val maxTestDuration: FiniteDuration = 2.minutes

  val httpProtocol: HttpProtocolBuilder = http
    .baseUrl("https://blazedemo.com")
    .acceptHeader("text/html,application/xhtml+xml")
    .userAgentHeader("gatling-cvu-validation/BurstThrottleRamp")
    .disableWarmUp

  val productScenario: ScenarioBuilder =
    scenario("Product")
      .exec(http("Product").get("/").check(status.is(200)))

  before {
    println("Profile: " + testProfile)
    println("Arrival rate (users/sec, misnamed 'concurrent'): " + productConcurrentUsers)
    println("Throttle cap (rps): " + productTargetThroughput + " reached in " + rumpUpPeriod)
    println("Injection period: " + concurrentUsersInjectionPeriod)
  }

  setUp(
    productScenario.inject(constantUsersPerSec(productConcurrentUsers) during concurrentUsersInjectionPeriod)
  ).protocols(httpProtocol)
    .throttle(
      reachRps(productTargetThroughput) in rumpUpPeriod,
      holdFor(holdTime)
    )
    .maxDuration(maxTestDuration)
}
