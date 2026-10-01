package com.miniciv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requires the frontend to have been built into the server's static content,
 * which the Maven build does before running tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendServingTest {

	@LocalServerPort
	int port;

	HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void servesTheFrontendPageAtTheRoot() throws Exception {
		HttpResponse<String> response = get("/");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("<title>miniciv</title>");
	}

	@Test
	void opensAnInviteLinkDirectlyInTheFrontend() throws Exception {
		HttpResponse<String> response = get("/rooms/ABC234");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("<title>miniciv</title>");
	}

	@Test
	void doesNotServeTheFrontendForUnknownApiPaths() throws Exception {
		assertThat(get("/api/nothing-here").statusCode()).isEqualTo(404);
		assertThat(get("/api").statusCode()).isEqualTo(404);
	}

	@Test
	void doesNotServeTheFrontendForMissingFiles() throws Exception {
		assertThat(get("/assets/missing.js").statusCode()).isEqualTo(404);
	}

}
