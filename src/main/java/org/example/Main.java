package org.example;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openqa.selenium.support.ui.ExpectedConditions.*;

public class Main {

    // Kill all instances of Chrome - `pkill Chrome`

    // Prevent macOS from blocking ChromeDriver - `spctl --add --label 'Approved' chromedriver`

    // Launch Chrome on a remote debugging port - `/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222`

    public static void main(String[] args) throws IOException, InterruptedException {

        long photoCount;

        // Set the path of the ChromeDriver
        System.setProperty("webdriver.chrome.driver", "src/main/resources/chromedriver_mac64/chromedriver");

        // Get running instance of the ChromeDriver
        WebDriver driver = getRemoteDebuggingDriver();

        // Create wait for 15 seconds
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Navigate to amazon photos people site
        System.out.println("Navigating to Amazon Photos People");
        driver.get("https://www.amazon.com/photos/people");

        //Click on the Gianna Filter
        System.out.println("Clicking on Gianna Filter");
        clickElementByParentSelectorAndChildText(driver, wait, "div.face-thumbnail","Gianna");

        // Get the number of photos to initialize the loop
        System.out.println("Getting the number of photos");
        WebElement counter;
        String xPath = "//*[@id=\"people-detail\"]/header/div/div/span/div[2]/span";
        do {
            wait.until(ExpectedConditions.and(
                    presenceOfElementLocated(By.xpath(xPath)),
                    textMatches(By.xpath(xPath), Pattern.compile(".*photos$"))));
            counter = driver.findElement(By.xpath(xPath));
        } while ("".equals(counter.getText()));
        photoCount = Long.parseLong(counter.getText().split(" ")[0]);

        //Click on the first photo
        System.out.println("Clicking on the first photo");
        xPath = "//*[@id=\"people-detail\"]/section/section/div/div/div[1]/div/div/div[1]/figure/div/a";
        clickElement(driver, wait, xPath);

        logPhotoURLs(driver, wait);

        identifyPhotosWithDuplicateNameTags(photoCount, driver, wait);
    }

    private static void identifyPhotosWithDuplicateNameTags(long photoCount, WebDriver driver, WebDriverWait wait) throws InterruptedException, IOException {
        Path file = Paths.get("src/main/resources/log.md");
        String xPath;
        System.out.println("Starting loop through photos");
        for (int i = 270; i < photoCount; i++) {
            if (i < 270) {
                System.out.println("Skipping photo " + (i + 1) + " | " + driver.getCurrentUrl());
                Thread.sleep(400);
                // Click next photo button
                xPath = "//*[@id=\"people-detail\"]/section/section/section/div/a[@class='next']";
                clickElement(driver, wait, xPath);
                continue;
            }
            //Click on ellipsis '...'
            System.out.println("Clicking on ellipsis '...' for photo " + (i + 1));
            xPath = "//*[@id=\"people-detail\"]/section/section/section/header/div/button";
            clickElement(driver, wait, xPath);

            //Click on 'Edit name tags'
            System.out.println("Clicking on 'Edit name tags' for photo " + (i + 1));
            xPath = "//*[@id=\"people-detail\"]/section/section/section/header/div/nav/ul/li[1]/button[@title='Edit name tags']";
            clickElement(driver, wait, xPath);

            //Get Count of Duplicate Names
            System.out.println("Getting names counts for photo " + (i + 1));
            xPath = "//*[@id=\"people-detail\"]/section/section/section/div/div/section/figure/figure";
            Map<String, Long> sortedMap = getNameCountMap(driver, wait, xPath);
            long count = sortedMap.entrySet().stream().filter(entry -> entry.getValue() > 1).count();

            //Click 'x' button to exit face editor
            System.out.println("Clicking 'x' button to exit face editor for photo " + (i + 1));
            xPath = "//*[@id=\"people-detail\"]/section/section/section/div/div/section/button[@class='exit-face-editor']";
            clickElement(driver, wait, xPath);

            String record;
            //Break out of loop if no duplicates found
            if (count == 0) {
                record = String.format("%s No duplicates found for photo", (i + 1));
                appendToLog(record, file);

                // Click next photo button
                System.out.println("Clicking next photo button for photo " + (i + 1));
                xPath = "//*[@id=\"people-detail\"]/section/section/section/div/a[@class='next']";
                clickElement(driver, wait, xPath);
                continue;
            }

            //Click on the info button
            System.out.println("Clicking on the info button for photo " + (i + 1));
            clickElement(driver, wait, "//*[@id=\"people-detail\"]/section/section/section/header/ul/li[5]/button");

            //Get file name
            xPath = "//*[@id=\"people-detail\"]/section/section/section/aside/div[2]/div[1]/div/span[1]";
            do {
                wait.until(and(presenceOfElementLocated(By.xpath(xPath)), textMatches(By.xpath(xPath), Pattern.compile(".*.jp.*g$"))));
            }
            while (driver.findElement(By.xpath(xPath)).getText().length() < 1);
            String fileNameText = driver.findElement(By.xpath(xPath)).getText();

             fileNameText = String.format("[%s](%s)", fileNameText, driver.getCurrentUrl());

            // Append details to log
            record = String.format("%s File: %s\n- People: %s", i + 1, fileNameText, sortedMap);
            appendToLog(record, file);

            //close info
            xPath = "//*[@id=\"people-detail\"]/section/section/section/aside/h2/button[@class='close-details']";
            clickElement(driver, wait, xPath);

            // Click next photo button
            xPath = "//*[@id=\"people-detail\"]/section/section/section/div/a[@class='next']";
            clickElement(driver, wait, xPath);
        }
    }

    private static Map<String, Long> getNameCountMap(WebDriver driver, WebDriverWait wait, String xPath) {
        wait.until(presenceOfElementLocated(By.xpath(xPath)));

        List<String> names = driver
                .findElements(By.xpath(xPath))
                .stream()
                .filter(figure -> figure.findElements(By.xpath(".//*")).stream().anyMatch(e -> e.getAttribute("class").contains("face-name")))
                .map(figure -> figure.findElement(By.className("face-name")).getAttribute("title"))
                .toList();

        Map<String, Long> map = names.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return new TreeMap<>(map);
    }

    private static void appendToLog(String record, Path file) throws IOException {
        System.out.println(record);
        Files.write(file, record.getBytes(), StandardOpenOption.APPEND);
    }

    public static WebDriver getRemoteDebuggingDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "localhost:9222");
        return new ChromeDriver(options);
    }

    private static void clickElement(WebDriver driver, WebDriverWait wait, String xPath) {
        By byXpath = By.xpath(xPath);
        wait.until(ExpectedConditions.and(presenceOfElementLocated(byXpath), elementToBeClickable(byXpath)));
        WebElement button = driver.findElement(By.xpath(xPath));
        button.click();
    }

    private static void clickElementByParentSelectorAndChildText(WebDriver driver, WebDriverWait wait, String parentCss, String childText) {
        By bySelector = By.cssSelector(parentCss);
        wait.until(and(presenceOfElementLocated(bySelector), elementToBeClickable(bySelector)));
        driver.findElements(bySelector).stream()
                .filter(e -> e.getText().contains(childText))
                .findFirst()
                .ifPresent(WebElement::click);
    }

    private static void logPhotoURLs(WebDriver driver, WebDriverWait wait) throws IOException {
        Path  file = Paths.get("src/main/resources/links.md");
        String fileNameText = "";
        String link = String.format("[%s](%s)", fileNameText, driver.getCurrentUrl());
        Files.write(file, link.getBytes(), StandardOpenOption.APPEND);
    }

}