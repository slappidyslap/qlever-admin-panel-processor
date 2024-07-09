//Ежедневные лимиты:
//
//        Лимит запросов: 100 000 запросов в день.
//        Скоростные лимиты:
//
//        Лимит запросов в секунду: 100 запросов в секунду на проект.
//        Лимит запросов в секунду на пользователя: 60 запросов в секунду на пользователя.
//
//С самого начала таймаут через 5 мин, потом 15 секунд на нахождения элемента таблицы
//
//i + 2 потому что итерация начинается со строки 3

package kg.seo.musabaev;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static java.lang.Integer.parseInt;
import static java.lang.Thread.sleep;
import static java.util.Collections.singletonList;

public class Main {

    final static int offset = 22;
    final static String subdomain = "kz"; //FIXME
    final static String defaultSheetName = "Interesting"; //FIXME
    final static String range = defaultSheetName + "!A:E"; //FIXME
    final static String spreadsheetId = "133h0xzoONmB4_DpPqrrU6tHd_QCuY8xqQmKXxTcz2W4"; //FIXME
    final static boolean implForLocal = true; //FIXME
    final static String statusCell = implForLocal ? "G" : "F";
    // TODO когда тип статьи меняют, тоже надо менять
    // исправит ошибку когда уникальность нарушаетсяz
    //FIXME sms.peklo@gmail.com
    final static String QLEVER_BASE_URL = "https://" + subdomain + ".qlever.asia/admin/?entity=InterestArticle&action=edit&menuIndex=2&submenuIndex=0&sortField=id&sortDirection=DESC&page=1&referer=%252Fadmin%252F%253Fentity%253DInterestArticle%2526action%253Dlist%2526menuIndex%253D2%2526submenuIndex%253D0%2526sortField%253Did%2526sortDirection%253DDESC%2526page%253D1&id=";
    final static Sheets sheets;

    static {
        try {
            sheets = Google.getSheets();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String... args) throws IOException, InterruptedException {

        WebDriverManager.chromedriver().setup();
        WebDriver web = new ChromeDriver();
        ValueRange response = sheets.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            System.out.println("No data found.");
        } else {
            for (int i = offset; i < values.size(); i++) {
                final String[] segments = ((String) values.get(i).get(0)).split("/");
                final int articleId = parseInt(segments[segments.length - 1]);
                final String url = QLEVER_BASE_URL + articleId;
                web.get(url);
                WebElement element1;
                WebElement element2;
                String selector1 = "article_metaTitle_" + (implForLocal ? "loc" : "ru");
                String selector2 = "article_metaDescription_" + (implForLocal ? "loc" : "ru");
                if (i == offset) {
                    WebDriverWait wait = new WebDriverWait(web, Duration.ofMinutes(5));
                    if (implForLocal) {
                        var a = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("/html/body/div[1]/div[2]/div[33]/ul/li[2]/a"))).get(0);
                        a.click();
                    }
                    element1 = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector1))).get(0);
                    element2 = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector2))).get(0);
                } else {
                    try {
                        WebDriverWait wait = new WebDriverWait(web, Duration.ofSeconds(5));
                        if (implForLocal) {
                            var a = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("/html/body/div[1]/div[2]/div[33]/ul/li[2]/a"))).get(0);
                            a.click();
                        }
                        element1 = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector1))).get(0);
                        element2 = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector2))).get(0);
                    } catch (Exception e) {
                        ValueRange body = new ValueRange().setValues(singletonList(singletonList(e.getMessage())));
                        sheets.spreadsheets().values()
                                .update(spreadsheetId, defaultSheetName + ("!" + statusCell) + (i + 1), body)
                                .setValueInputOption("RAW")
                                .execute();
                        continue;
                    }
                }
                String metaTitle;
                String metaDesc;
                try {
                    metaTitle = (String) values.get(i).get(implForLocal ? 3 : 1);
                    metaDesc = (String) values.get(i).get(implForLocal ? 4 : 2);
                } catch (Exception e) {
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("not updated")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + ("!" + statusCell) + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                    continue;
                }

                if (metaTitle.contains("error") || metaDesc.contains("error")) {
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("not updated")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + ("!" + statusCell) + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                    continue;
                }
                element1.clear();
                element2.clear();
                sleep(300);
                element1.sendKeys(metaTitle);
                element2.sendKeys(metaDesc);
                sleep(300);
                web.findElement(By.cssSelector("[data-target='#modal-publish-article']")).click();
                sleep(300);// FIXME
                web.findElement(By.id("article-submit")).click(); // FIXME

                try {
                    WebDriverWait wait = new WebDriverWait(web, Duration.ofSeconds(2));
                    wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("//h3[@class='modals-text_title' and text()='Ошибка']"))).get(0);
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("not updated")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + ("!" + statusCell) + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                } catch (Exception $) {
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("updated")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + ("!" + statusCell) + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                }
            }
        }
        web.close();
    }
}
