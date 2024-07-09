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

    final static int offset = 1; // offset в стиле программирование, то есть -1
    final static String subdomain = "tj";
    final static String defaultSheetName = "Магазин";
    final static String range = defaultSheetName + "!A:D";
    final static String spreadsheetId = "1pxqSkr73w5fx1QQKZwPIKYgFcQREvafftZ5pK-rdf_I";

    // TODO когда тип статьи меняют, тоже надо менять
    // исправит ошибку когда уникальность нарушается
    final static String QLEVER_BASE_URL = "https://" + subdomain + ".qlever.asia/admin/?entity=Product&action=edit&menuIndex=6&submenuIndex=0&sortField=id&sortDirection=DESC&page=1&referer=%252Fadmin%252F%253Fentity%253DProduct%2526action%253Dlist%2526menuIndex%253D6%2526submenuIndex%253D0%2526sortField%253Did%2526sortDirection%253DDESC%2526page%253D1&id=";
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
                final String transliterated = (String) values.get(i).get(2);
                if (transliterated.contains("error")) {
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("Не обновлен")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + "!D" + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                    continue;
                }

                web.get(url);
                WebElement element;
                String selector1 = "product_urlName";
                if (i == offset) {
                    WebDriverWait wait = new WebDriverWait(web, Duration.ofMinutes(5));
                    element = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector1))).get(0); // FIXME
                } else {
                    try {
                        WebDriverWait wait = new WebDriverWait(web, Duration.ofSeconds(5));
                        element = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.id(selector1))).get(0); // FIXME
                    } catch (TimeoutException e) {
                        ValueRange body = new ValueRange().setValues(singletonList(singletonList("Не обновлен")));
                        sheets.spreadsheets().values()
                                .update(spreadsheetId, defaultSheetName + "!D" + (i + 1), body)
                                .setValueInputOption("RAW")
                                .execute();
                        continue;
                    }
                }
                element.clear();
                element.sendKeys(transliterated);
//                sleep(300);
                web.findElement(By.cssSelector("[data-target='#modal-publish-new-product']")).click(); // FIXME
                sleep(300);
                web.findElement(By.id("product-submit")).click(); // FIXME

                try {
                    WebDriverWait wait = new WebDriverWait(web, Duration.ofSeconds(2));
                    element = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("//h3[@class='modals-text_title' and text()='Ошибка']"))).get(0);
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("Не обновлен")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + "!D" + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                } catch (Exception $) {
                    ValueRange body = new ValueRange().setValues(singletonList(singletonList("Обновлен")));
                    sheets.spreadsheets().values()
                            .update(spreadsheetId, defaultSheetName + "!D" + (i + 1), body)
                            .setValueInputOption("RAW")
                            .execute();
                }
            }
        }
        web.close();
    }
}
