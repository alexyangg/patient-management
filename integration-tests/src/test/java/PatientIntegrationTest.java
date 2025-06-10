import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class PatientIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:4004";
    }

    @Test
    public void shouldReturnPatientsWithValidToken() {
        String loginPayload = """
                    {
                        "email": "testuser@test.com",
                        "password": "password123"
                    }
                """;

        // call login endpoint to return a token
        String token = given()
                .contentType("application/json")
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("token");

        Response response = given()
                .header("Authorization", "Bearer " + token) // 1. Arrange
                .when()
                .get("/api/patients") // 2. Act
                .then()
                .statusCode(200) // 3. Assert
                .body("patients", notNullValue())
                .extract()
                .response();

        List<Map<String, Object>> patients = response.getBody().jsonPath().getList(".");
        System.out.println("Patients:");
        for (Object patient : patients) {
            System.out.println(patient);
        }

    }
}
