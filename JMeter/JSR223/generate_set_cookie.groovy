import org.apache.jmeter.protocol.http.control.Cookie
import org.apache.jmeter.protocol.http.control.CookieManager
import java.text.SimpleDateFormat

// Retrieve the user value from a JMeter variable 'usr', or default to "defaultUser"
def usr = vars.get("usr") ?: "defaultUser"

// Create the current datetime string in MMddyyyyHHmmssSSS format
def sdf = new SimpleDateFormat("MMddyyyyHHmmssSSS")
def currentDateStr = sdf.format(new Date())

// Create the session string by concatenating the user and the datetime string
def session = usr + ":" + currentDateStr

// Calculate the expiration time (current time plus 1000 milliseconds, converted to seconds)
def expires = ((new Date().time + 1000) / 1000) as long

// Create the cookie with name, value, domain, path, secure flag (false), and expiration time.
Cookie cookie = new Cookie("dpa.cookie", session, ".demoblaze.com", "/", false, expires)

// Retrieve the Cookie Manager from the current sampler and add the cookie.
CookieManager cookieManager = ctx.getCurrentSampler().getCookieManager()
if (cookieManager == null) {
    log.warn("No CookieManager found in the current sampler. Please add a HTTP Cookie Manager to your Test Plan.")
} else {
    cookieManager.add(cookie)
    log.info("Cookie added: " + cookie)
}