import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckRemains {
    // данные стенда
    private String host = "http://rtd-testx-app.corp.mvideo.ru";
    private int port = 8080;
    private String authEndPoint = "/api/v2/session/employee/auth";
    private String availabilityEndPoint = "/api/v2/catalogue/material/availability";

    // данные для авторизации сотрудника
    private String employeeNumber = "44539";

    private String employeeSessionId;
    private String requestId;
    private String requestBody;
    private String responseBody;

    @BeforeClass
    public void before() {
    }

    @Test(enabled = true, description = "Авторизация сотрудника")
    public void auth() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/authRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeNumber}}", employeeNumber)
                    .replaceAll("\\{\\{requestId}}", requestId);

            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + authEndPoint).thenReturn().getBody().asString();
            employeeSessionId = responseBody.replaceAll(".*sessionUID\\\":\\\"(.*)\\\",\\\"shopNumber.*", "$1");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test(enabled = true, description = "Проверка остатков для товаров, где должны быть все остатки")
    public void checkRemains_01() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/all.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // делаем запрос
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // теперь надо пробежаться в цикле и отсеять только нужные нам товары
            // конечный список на пополнение
            Map<String, Map<String, String>> allSku = new HashMap<>();

            JsonElement fullList = new JsonParser()
                    .parse(responseBody)
                    .getAsJsonObject().get("ResponseBody").getAsJsonObject().get("availabilities");
            JsonArray fullArray = fullList.getAsJsonArray();

            for (int i = 0; i < fullArray.size(); i++) {
                int tz = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("tradezone").getAsInt();
                int os = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("warehouse").getAsInt();
                int uds = 0;
                JsonArray takeaway = fullArray.get(i).getAsJsonObject()
                        .get("warehouses").getAsJsonObject()
                        .get("takeaway").getAsJsonArray();
                if (takeaway.size() > 0) {
                    int terminator = 30;

                    for (int j = 0; j < terminator; j++) {
                        if (j >= takeaway.size()) break;
                        uds += takeaway.get(j).getAsJsonObject().get("qty").getAsInt();
                    }
                }

                // формируем мэпу ограничений
                Map<String, String> constraints = new HashMap<>();
                if (tz < 300) {
                    constraints.put("ТЗ", "Остатков мало. Сейчас остатков " + tz);
                }
                if (os < 300) {
                    constraints.put("ОС", "Остатков мало. Сейчас остатков " + os);
                }

                if (uds < 300) {
                    constraints.put("УДС", "Остатков мало. Сейчас остатков " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // данные подготовили. Теперь надо сформировать json для отправки
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\dа-яА-Я]+)=([а-яА-Я\\d \\.]+)", "\"$1\":\"$2\"");


            // создадим файл в таргет
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"Полное наполнение\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/allRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //Сохраняем в файл
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "Проверка остатков для товаров, где должна быть ТОЛЬКО витрина и много")
    public void checkRemains_02() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/vitrineOnly.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // делаем запрос
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // теперь надо пробежаться в цикле и отсеять только нужные нам товары
            // конечный список на пополнение
            Map<String, Map<String, String>> allSku = new HashMap<>();

            JsonElement fullList = new JsonParser()
                    .parse(responseBody)
                    .getAsJsonObject().get("ResponseBody").getAsJsonObject().get("availabilities");
            JsonArray fullArray = fullList.getAsJsonArray();

            for (int i = 0; i < fullArray.size(); i++) {
                int tz = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("tradezone").getAsInt();
                int os = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("warehouse").getAsInt();
                int uds = 0;
                JsonArray takeaway = fullArray.get(i).getAsJsonObject()
                        .get("warehouses").getAsJsonObject()
                        .get("takeaway").getAsJsonArray();
                if (takeaway.size() > 0) {
                    int terminator = 30;

                    for (int j = 0; j < terminator; j++) {
                        if (j >= takeaway.size()) break;
                        uds += takeaway.get(j).getAsJsonObject().get("qty").getAsInt();
                    }
                }

                // формируем мэпу ограничений
                Map<String, String> constraints = new HashMap<>();
                if (tz < 300) {
                    constraints.put("ТЗ", "Остатков мало. Сейчас остатков " + tz);
                }
                if (os > 0) {
                    constraints.put("ОС", "Остатков быть не должно. Сейчас остатков " + os);
                }

                if (uds > 0) {
                    constraints.put("УДС", "Остатков быть не должно. Сейчас остатков " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // данные подготовили. Теперь надо сформировать json для отправки
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\dа-яА-Я]+)=([а-яА-Я\\d \\.]+)", "\"$1\":\"$2\"");


            // создадим файл в таргет
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"ТОЛЬКО витрина много\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyManyVitrineRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //Сохраняем в файл
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "Проверка остатков для товаров, где должна быть ТОЛЬКО витрина 1 остаток")
    public void checkRemains_03() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/vitrineLast.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // делаем запрос
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // теперь надо пробежаться в цикле и отсеять только нужные нам товары
            // конечный список на пополнение
            Map<String, Map<String, String>> allSku = new HashMap<>();

            JsonElement fullList = new JsonParser()
                    .parse(responseBody)
                    .getAsJsonObject().get("ResponseBody").getAsJsonObject().get("availabilities");
            JsonArray fullArray = fullList.getAsJsonArray();

            for (int i = 0; i < fullArray.size(); i++) {
                int tz = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("tradezone").getAsInt();
                int os = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("warehouse").getAsInt();
                int uds = 0;
                JsonArray takeaway = fullArray.get(i).getAsJsonObject()
                        .get("warehouses").getAsJsonObject()
                        .get("takeaway").getAsJsonArray();
                if (takeaway.size() > 0) {
                    int terminator = 30;

                    for (int j = 0; j < terminator; j++) {
                        if (j >= takeaway.size()) break;
                        uds += takeaway.get(j).getAsJsonObject().get("qty").getAsInt();
                    }
                }

                // формируем мэпу ограничений
                Map<String, String> constraints = new HashMap<>();
                if (tz != 1) {
                    constraints.put("ТЗ", "Товар НЕ последний, должен быть 1. Сейчас остатков " + tz);
                }
                if (os > 0) {
                    constraints.put("ОС", "Остатков быть не должно. Сейчас остатков " + os);
                }

                if (uds > 0) {
                    constraints.put("УДС", "Остатков быть не должно. Сейчас остатков " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // данные подготовили. Теперь надо сформировать json для отправки
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\dа-яА-Я]+)=([а-яА-Я\\d \\.]+)", "\"$1\":\"$2\"");


            // создадим файл в таргет
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"ТОЛЬКО витрина 1 остаток\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyLastVitrineRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //Сохраняем в файл
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "Проверка остатков для товаров, где должен быть ТОЛЬКО УДС много")
    public void checkRemains_04() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/onlyManyUDS.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // делаем запрос
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // теперь надо пробежаться в цикле и отсеять только нужные нам товары
            // конечный список на пополнение
            Map<String, Map<String, String>> allSku = new HashMap<>();

            JsonElement fullList = new JsonParser()
                    .parse(responseBody)
                    .getAsJsonObject().get("ResponseBody").getAsJsonObject().get("availabilities");
            JsonArray fullArray = fullList.getAsJsonArray();

            for (int i = 0; i < fullArray.size(); i++) {
                int tz = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("tradezone").getAsInt();
                int os = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("warehouse").getAsInt();
                int uds = 0;
                JsonArray takeaway = fullArray.get(i).getAsJsonObject()
                        .get("warehouses").getAsJsonObject()
                        .get("takeaway").getAsJsonArray();
                if (takeaway.size() > 0) {
                    int terminator = 30;

                    for (int j = 0; j < terminator; j++) {
                        if (j >= takeaway.size()) break;
                        uds += takeaway.get(j).getAsJsonObject().get("qty").getAsInt();
                    }
                }

                // формируем мэпу ограничений
                Map<String, String> constraints = new HashMap<>();
                if (tz > 0) {
                    constraints.put("ТЗ", "Остатков быть не должно. Сейчас остатков " + tz);
                }
                if (os > 0) {
                    constraints.put("ОС", "Остатков быть не должно. Сейчас остатков " + os);
                }

                if (uds < 300) {
                    constraints.put("УДС", "Остатков мало. Сейчас остатков " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // данные подготовили. Теперь надо сформировать json для отправки
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\dа-яА-Я]+)=([а-яА-Я\\d \\.]+)", "\"$1\":\"$2\"");


            // создадим файл в таргет
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"ТОЛЬКО УДС много\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyManyUDSRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //Сохраняем в файл
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "Проверка остатков для распроданных товаров")
    public void checkRemains_05() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/soldOut.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // делаем запрос
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // теперь надо пробежаться в цикле и отсеять только нужные нам товары
            // конечный список на пополнение
            Map<String, Map<String, String>> allSku = new HashMap<>();

            JsonElement fullList = new JsonParser()
                    .parse(responseBody)
                    .getAsJsonObject().get("ResponseBody").getAsJsonObject().get("availabilities");
            JsonArray fullArray = fullList.getAsJsonArray();

            for (int i = 0; i < fullArray.size(); i++) {
                int tz = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("tradezone").getAsInt();
                int os = fullArray.get(i).getAsJsonObject()
                        .get("shop").getAsJsonObject().get("warehouse").getAsInt();
                int uds = 0;
                JsonArray takeaway = fullArray.get(i).getAsJsonObject()
                        .get("warehouses").getAsJsonObject()
                        .get("takeaway").getAsJsonArray();
                if (takeaway.size() > 0) {
                    int terminator = 30;

                    for (int j = 0; j < terminator; j++) {
                        if (j >= takeaway.size()) break;
                        uds += takeaway.get(j).getAsJsonObject().get("qty").getAsInt();
                    }
                }

                // формируем мэпу ограничений
                Map<String, String> constraints = new HashMap<>();
                if (tz > 0) {
                    constraints.put("ТЗ", "Остатков быть не должно. Сейчас остатков " + tz);
                }
                if (os > 0) {
                    constraints.put("ОС", "Остатков быть не должно. Сейчас остатков " + os);
                }

                if (uds > 0) {
                    constraints.put("УДС", "Остатков быть не должно. Сейчас остатков " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // данные подготовили. Теперь надо сформировать json для отправки
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\dа-яА-Я]+)=([а-яА-Я\\d \\.]+)", "\"$1\":\"$2\"");


            // создадим файл в таргет
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"РАСПРОДАНО\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/soldOutRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //Сохраняем в файл
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @AfterClass
    public void after() {
    }
}
