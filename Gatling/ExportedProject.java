package nocodeexport;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

public class ExportedProject extends Simulation {

  HttpProtocolBuilder httpProtocol =
    http.baseUrl("https://blazedemo.com")
      .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .acceptLanguageHeader("en-US,en;q=0.5")
      .acceptEncodingHeader("gzip, deflate")
      .userAgentHeader(
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:109.0) Gecko/20100101 Firefox/119.0"
      );

  ScenarioBuilder scn = scenario("No code scenario").exec(
    http("GET Home").get("/")
  );

  {
    setUp(
      scn.injectOpen(rampUsersPerSec(3).to(12).during(120))
    ).assertions(
      global().responseTime().percentile(95.0).lte(200),
      global().failedRequests().percent().lt(5.0)
    ).protocols(httpProtocol);
  }
}