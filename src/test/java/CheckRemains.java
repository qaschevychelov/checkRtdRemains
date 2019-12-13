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
    // ������ ������
    private String host = "http://rtd-testx-app.corp.mvideo.ru";
    private int port = 8080;
    private String authEndPoint = "/api/v2/session/employee/auth";
    private String availabilityEndPoint = "/api/v2/catalogue/material/availability";

    // ������ ��� ����������� ����������
    private String employeeNumber = "44539";

    private String employeeSessionId;
    private String requestId;
    private String requestBody;
    private String responseBody;

    @BeforeClass
    public void before() {
    }

    @Test(enabled = true, description = "����������� ����������")
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

    @Test(enabled = true, description = "�������� �������� ��� �������, ��� ������ ���� ��� �������")
    public void checkRemains_01() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // ���������� ������ sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/all.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // ������ ������
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // ������ ���� ����������� � ����� � ������� ������ ������ ��� ������
            // �������� ������ �� ����������
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

                // ��������� ���� �����������
                Map<String, String> constraints = new HashMap<>();
                if (tz < 300) {
                    constraints.put("��", "�������� ����. ������ �������� " + tz);
                }
                if (os < 300) {
                    constraints.put("��", "�������� ����. ������ �������� " + os);
                }

                if (uds < 300) {
                    constraints.put("���", "�������� ����. ������ �������� " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // ������ �����������. ������ ���� ������������ json ��� ��������
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\d�-��-�]+)=([�-��-�\\d \\.]+)", "\"$1\":\"$2\"");


            // �������� ���� � ������
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"������ ����������\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/allRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //��������� � ����
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "�������� �������� ��� �������, ��� ������ ���� ������ ������� � �����")
    public void checkRemains_02() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // ���������� ������ sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/vitrineOnly.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // ������ ������
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // ������ ���� ����������� � ����� � ������� ������ ������ ��� ������
            // �������� ������ �� ����������
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

                // ��������� ���� �����������
                Map<String, String> constraints = new HashMap<>();
                if (tz < 300) {
                    constraints.put("��", "�������� ����. ������ �������� " + tz);
                }
                if (os > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + os);
                }

                if (uds > 0) {
                    constraints.put("���", "�������� ���� �� ������. ������ �������� " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // ������ �����������. ������ ���� ������������ json ��� ��������
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\d�-��-�]+)=([�-��-�\\d \\.]+)", "\"$1\":\"$2\"");


            // �������� ���� � ������
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"������ ������� �����\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyManyVitrineRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //��������� � ����
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "�������� �������� ��� �������, ��� ������ ���� ������ ������� 1 �������")
    public void checkRemains_03() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // ���������� ������ sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/vitrineLast.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // ������ ������
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // ������ ���� ����������� � ����� � ������� ������ ������ ��� ������
            // �������� ������ �� ����������
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

                // ��������� ���� �����������
                Map<String, String> constraints = new HashMap<>();
                if (tz != 1) {
                    constraints.put("��", "����� �� ���������, ������ ���� 1. ������ �������� " + tz);
                }
                if (os > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + os);
                }

                if (uds > 0) {
                    constraints.put("���", "�������� ���� �� ������. ������ �������� " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // ������ �����������. ������ ���� ������������ json ��� ��������
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\d�-��-�]+)=([�-��-�\\d \\.]+)", "\"$1\":\"$2\"");


            // �������� ���� � ������
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"������ ������� 1 �������\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyLastVitrineRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //��������� � ����
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "�������� �������� ��� �������, ��� ������ ���� ������ ��� �����")
    public void checkRemains_04() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // ���������� ������ sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/onlyManyUDS.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // ������ ������
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // ������ ���� ����������� � ����� � ������� ������ ������ ��� ������
            // �������� ������ �� ����������
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

                // ��������� ���� �����������
                Map<String, String> constraints = new HashMap<>();
                if (tz > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + tz);
                }
                if (os > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + os);
                }

                if (uds < 300) {
                    constraints.put("���", "�������� ����. ������ �������� " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // ������ �����������. ������ ���� ������������ json ��� ��������
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\d�-��-�]+)=([�-��-�\\d \\.]+)", "\"$1\":\"$2\"");


            // �������� ���� � ������
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"������ ��� �����\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/onlyManyUDSRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //��������� � ����
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(finalJson);
                writer.flush();
                writer.close();
            }
        } catch (Throwable e) { e.printStackTrace(); }
    }

    @Test(enabled = true, description = "�������� �������� ��� ������������ �������")
    public void checkRemains_05() {
        try {
            requestId = UUID.randomUUID().toString();
            requestBody = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/requestTemplates/availabilityRequestBody.json").getCanonicalPath())));
            requestBody = requestBody
                    .replaceAll("\\{\\{employeeSessionId}}", employeeSessionId)
                    .replaceAll("\\{\\{requestId}}", requestId);

            // ���������� ������ sku
            String skuList = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/materials/soldOut.txt").getCanonicalPath())));
            skuList = skuList.replaceAll("(\\d+)\\s?", "\"$1\",");
            skuList = skuList.replaceAll("^\\\"([\\d\\\",\\s]+)\\\",$", "$1");

            requestBody = requestBody.replaceAll("\\{\\{skuList}}", skuList);

            // ������ ������
            responseBody = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(requestBody)
                    .when()
                    .post(host + ":" + port + availabilityEndPoint).thenReturn().getBody().asString();


            // ������ ���� ����������� � ����� � ������� ������ ������ ��� ������
            // �������� ������ �� ����������
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

                // ��������� ���� �����������
                Map<String, String> constraints = new HashMap<>();
                if (tz > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + tz);
                }
                if (os > 0) {
                    constraints.put("��", "�������� ���� �� ������. ������ �������� " + os);
                }

                if (uds > 0) {
                    constraints.put("���", "�������� ���� �� ������. ������ �������� " + uds);
                }
                if (!constraints.isEmpty()) {
                    allSku.put(
                            fullArray.get(i).getAsJsonObject().get("material").getAsString(),
                            constraints
                    );
                }
            }

            // ������ �����������. ������ ���� ������������ json ��� ��������
            String jSon = allSku.toString().replaceAll("([\\d]+)=", "\"$1\":");
            jSon = jSon.replaceAll("([\\d�-��-�]+)=([�-��-�\\d \\.]+)", "\"$1\":\"$2\"");


            // �������� ���� � ������
            String finalJson = new String(Files.readAllBytes(Paths.get(new File("src/test/resources/output/allRemains.json").getCanonicalPath())));
            finalJson = finalJson.replaceAll("\"\\{\\{title}}\"", "\"����������\"");
            finalJson = finalJson.replaceAll("\"\\{\\{body}}\"", jSon);
            File file = new File("target/soldOutRemains.json");
            if (file.isFile()) file.delete();
            if (!file.isFile() && file.createNewFile()) {
                //��������� � ����
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
