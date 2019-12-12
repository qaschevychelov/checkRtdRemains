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
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/authRequestBody.json").getCanonicalPath())));
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

    @Test(enabled = true, description = "Проверка остатков")
    public void checkRemains() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // подготовим массив sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/all.txt").getCanonicalPath())));
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
                    constraints.put("ТЗ", "Остатков меньше 300. Сейчас остатков " + tz);
                }
                if (os < 300) {
                    constraints.put("ОС", "Остатков меньше 300. Сейчас остатков " + os);
                }

                if (uds < 300) {
                    constraints.put("УДС", "Остатков меньше 300. Сейчас остатков " + uds);
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

    @AfterClass
    public void after() {
    }
}
