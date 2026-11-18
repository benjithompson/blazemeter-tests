import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import static org.junit.jupiter.api.Assertions.*;

public class HelloSelenium {
    
    private WebDriver driver;
    
    @BeforeEach
    public void setUp() {
        driver = new ChromeDriver();
    }
    
    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
    
    @Test
    @DisplayName("Verify Selenium website title")
    public void testSeleniumWebsiteTitle() {
        // Navigate to the Selenium website
        driver.get("https://selenium.dev");
        
        // Get the page title
        String pageTitle = driver.getTitle();
        System.out.println("Page title: " + pageTitle);
        
        // Assert the title is not empty and contains expected text
        assertNotNull(pageTitle, "Page title should not be null");
        assertFalse(pageTitle.isEmpty(), "Page title should not be empty");
        assertTrue(pageTitle.contains("Selenium"), "Page title should contain 'Selenium'");
        
        // Additional verification - check if we're on the correct page
        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains("selenium.dev"), "Current URL should contain 'selenium.dev'");
    }
    
    @Test
    @DisplayName("Verify Selenium website loads successfully")
    public void testSeleniumWebsiteLoads() {
        // Navigate to the Selenium website
        driver.get("https://selenium.dev");
        
        // Wait for page to load (implicit wait)
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        
        // Verify page source is not empty
        String pageSource = driver.getPageSource();
        assertNotNull(pageSource, "Page source should not be null");
        assertFalse(pageSource.isEmpty(), "Page source should not be empty");
        
        // Verify page title is present
        assertNotNull(driver.getTitle(), "Page title should be present");
    }
}
