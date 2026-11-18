package com.example.tests;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DemoBankTest {
  private WebDriver driver;
  private boolean acceptNextAlert = true;
  private StringBuffer verificationErrors = new StringBuffer();

  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-gpu");
    options.addArguments("--remote-allow-origins=*");
    driver = new ChromeDriver(options);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
  }

  @Test
  public void testNgDbank() throws Exception {
    // Navigate to login page
    driver.get("http://dbankdemo.com/bank/login");
    assertEquals(driver.getTitle(), "Digital Bank", "Page title should be 'Digital Bank'");
    
    // Enter username
    driver.findElement(By.id("username")).sendKeys("jsmith@demo.io");

    // Enter password
    driver.findElement(By.id("password")).sendKeys("Demo123!");
    
    // Click sign in button
    driver.findElement(By.id("submit")).click();
    
    // Wait for redirect to home page
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    wait.until(ExpectedConditions.urlToBe("http://dbankdemo.com/bank/home"));
    
    // Verify successful login by checking we're on the home page
    assertEquals(driver.getCurrentUrl(), "http://dbankdemo.com/bank/home", "Should be redirected to home page after login");
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    if (driver != null) {
      driver.quit();
    }
    String verificationErrorString = verificationErrors.toString();
    if (!"".equals(verificationErrorString)) {
      fail(verificationErrorString);
    }
  }
}
