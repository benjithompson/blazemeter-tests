package com.example.tests;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Digital Bank flow: log in, open View Checking, assert the account heading, log out.
 * Converted from a Selenium IDE export. Every waitFor step is now an explicit WebDriverWait.
 */
public class Dbankdemo20260930Testng {
  private static final String LOGIN_URL = "http://dbankdemo.com/bank/login";
  // Public demo credentials for dbankdemo.com.
  private static final String USERNAME = "jsmith@demo.io";
  private static final String PASSWORD = "Demo123!";

  private WebDriver driver;
  private WebDriverWait wait;

  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception {
    // The BlazeMeter engine has no display. Chrome must run headless without the sandbox.
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new");
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-gpu");
    options.addArguments("--remote-allow-origins=*");
    options.addArguments("--window-size=1920,1080");
    driver = new ChromeDriver(options);
    wait = new WebDriverWait(driver, Duration.ofSeconds(10));
  }

  private WebElement clickable(By by) {
    return wait.until(ExpectedConditions.elementToBeClickable(by));
  }

  @Test
  public void testDbankdemo20260930() throws Exception {
    driver.get(LOGIN_URL);

    WebElement username = clickable(By.id("username"));
    username.click();
    username.clear();
    username.sendKeys(USERNAME);

    clickable(By.id("password")).sendKeys(PASSWORD);
    clickable(By.id("submit")).click();

    clickable(By.id("checking-menu")).click();
    clickable(By.id("view-checking-menu-item")).click();

    WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.h4.m-0")));
    assertEquals(heading.getText(), "Individual Checking");

    clickable(By.id("home-menu-item")).click();
    clickable(By.cssSelector("img.user-avatar.rounded-circle")).click();
    clickable(By.linkText("Logout")).click();
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    if (driver != null) {
      driver.quit();
    }
  }
}
