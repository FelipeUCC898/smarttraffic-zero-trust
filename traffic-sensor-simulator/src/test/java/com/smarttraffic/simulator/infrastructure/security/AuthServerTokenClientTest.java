package com.smarttraffic.simulator.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.simulator.infrastructure.config.AuthClientProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class AuthServerTokenClientTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String CLIENT = "simulator-test";
    private static final String SECRET = "only-a-test-secret";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void obtainsTokenUsingTheExistingAuthContractAndRequestsAgainForRenewal() throws Exception {
        String value = jwt("HS256", "{\"sub\":\"simulator-test\",\"iat\":" + NOW.getEpochSecond()
                + ",\"exp\":" + NOW.plusSeconds(900).getEpochSecond() + "}");
        Fixture fixture = fixture(mapper.writeValueAsBytes(java.util.Map.of("accessToken", value)), 200);

        var token = fixture.client.obtainToken();
        fixture.client.obtainToken();

        assertThat(token.value()).isEqualTo(value);
        assertThat(token.principal()).isEqualTo(CLIENT);
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(token.issuedAt()).isEqualTo(NOW);
        assertThat(token.toString()).doesNotContain(value, SECRET);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(fixture.http, times(2)).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri()).isEqualTo(URI.create("https://auth.example/token"));
        assertThat(request.getValue().method()).isEqualTo("POST");
        CompletableFuture<byte[]> requestBytes = new CompletableFuture<>();
        request.getValue().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer bytes) {
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                requestBytes.complete(data);
            }
            @Override public void onError(Throwable failure) { requestBytes.completeExceptionally(failure); }
            @Override public void onComplete() { }
        });
        var tree = mapper.readTree(requestBytes.join());
        assertThat(tree.size()).isEqualTo(2);
        assertThat(tree.get("clientId").asText()).isEqualTo(CLIENT);
        assertThat(tree.get("clientSecret").asText()).isEqualTo(SECRET);
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void rejectsUnusableTokenMetadataWithoutExposingTheToken(String value) throws Exception {
        Fixture fixture = fixture(mapper.writeValueAsBytes(java.util.Map.of("accessToken", value)), 200);

        assertThatThrownBy(fixture.client::obtainToken).isInstanceOf(IllegalStateException.class)
                .hasMessage("No se pudo obtener un JWT utilizable para el cliente").hasNoCause();
    }

    @Test
    void rejectsHttpErrorsMissingTokenAndOversizedResponsesWithoutLeakingBodies() {
        assertThatThrownBy(fixture(SECRET.getBytes(StandardCharsets.UTF_8), 401).client::obtainToken)
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(fixture("{}".getBytes(StandardCharsets.UTF_8), 200).client::obtainToken)
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(fixture(new byte[65537], 200).client::obtainToken)
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void timesOutAndCancelsAnUnfinishedRequest() {
        HttpClient http = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        AuthClientProperties properties = new AuthClientProperties(URI.create("https://auth.example/token"), CLIENT, SECRET, 1);
        AuthServerTokenClient client = new AuthServerTokenClient(properties, http, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(client::obtainToken).isInstanceOf(IllegalStateException.class);

        assertThat(pending.isCancelled()).isTrue();
    }

    @Test
    void preservesThreadInterruptionAndCancelsTheRequest() {
        HttpClient http = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        AuthServerTokenClient client = client(http);
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(client::obtainToken).isInstanceOf(IllegalStateException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(pending.isCancelled()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void boundsTheBodyDuringReceptionAndCancelsExcessData() {
        var subscriber = AuthServerTokenClient.boundedBody().apply(mock(HttpResponse.ResponseInfo.class));
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65537])));

        verify(subscription).cancel();
        assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
    }

    @Test
    void refusesPlainHttpAndDoesNotExposeClientCredentialsInConfigurationText() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthClientProperties(
                URI.create("http://auth.example/token"), CLIENT, SECRET, 5000));
        assertThat(new AuthClientProperties(URI.create("https://auth.example/token"), CLIENT, SECRET, 5000).toString())
                .doesNotContain(SECRET);
    }

    private AuthServerTokenClient client(HttpClient http) {
        return new AuthServerTokenClient(new AuthClientProperties(URI.create("https://auth.example/token"),
                CLIENT, SECRET, 5000), http, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(byte[] body, int status) {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        return new Fixture(client(http), http);
    }

    private static Stream<String> invalidTokens() {
        long validExpiry = NOW.plusSeconds(900).getEpochSecond();
        return Stream.of("not-a-jwt", jwt("none", "{\"sub\":\"simulator-test\",\"exp\":" + validExpiry + "}"),
                jwt("HS256", "{\"sub\":\"other\",\"exp\":" + validExpiry + "}"),
                jwt("HS256", "{\"sub\":\"simulator-test\",\"exp\":" + NOW.minusSeconds(1).getEpochSecond() + "}"),
                jwt("HS256", "{\"sub\":\"simulator-test\",\"exp\":\"invalid\"}"),
                jwt("HS256", "{\"sub\":\"simulator-test\",\"iat\":" + NOW.plusSeconds(30).getEpochSecond()
                        + ",\"exp\":" + validExpiry + "}"));
    }

    // JWT de estructura artificial: se prueban metadatos, no una firma criptográfica.
    private static String jwt(String alg, String claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(("{\"alg\":\"" + alg + "\"}").getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".test-signature";
    }

    private record Fixture(AuthServerTokenClient client, HttpClient http) { }
}
