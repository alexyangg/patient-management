import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:4004"; // does not run inside a Docker container
    }

    @Test
    public void shouldReturnOKWithValidToken() {
        /*
        Structure for testing:
        1. Arrange - setup data the test needs to work 100% of the time
        2. Act - code that triggers the thing we are testing. i.e. calling an endpoint
        3. Assert - check that the result is what we expect
         */

        // 1. Arrange
        String loginPayload = """
                    {
                        "email": "testuser@test.com",
                        "password": "password123"
                    }
                """;

        // 2. Act
        Response response = given() // arrange
                .contentType("application/json")
                .body(loginPayload)
                .when() // act
                .post("/auth/login")
                .then()
                .statusCode(200) // 3. Assert
                .body("token", notNullValue()) // last 3 lines extract token from the response
                .extract()
                .response();

        System.out.println("Generated token: " + response.jsonPath().getString("token"));
    }

    @Test
    public void shouldReturnUnauthorizedOnInvalidLogin() {
        String loginPayload = """
                    {
                        "email": "invalid_user@test.com",
                        "password": "wrongpassword"
                    }
                """;

        given() // arrange
            .contentType("application/json")
            .body(loginPayload)
            .when() // act
            .post("/auth/login")
            .then()
            .statusCode(401);

        // no response to log since there's no token returned from an unauthorized login
    }
}
