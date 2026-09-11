package io.kestra.plugin.twilio.notify;

import com.twilio.http.NetworkHttpClient;
import com.twilio.http.Request;
import com.twilio.http.Response;

/**
 * The Twilio SDK hardcodes api.twilio.com and its builder only offers region/edge, which stay inside that host. This
 * rewrites each request so tests can point the SDK at a stub.
 */
public final class RebasingHttpClient extends NetworkHttpClient {
    private static final String DEFAULT_BASE_URL = "https://api.twilio.com";

    private final String baseUrl;

    public RebasingHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Response makeRequest(Request original) {
        var rebased = new Request(original.getMethod(), original.getUrl().replace(DEFAULT_BASE_URL, baseUrl));

        original.getPostParams().forEach((name, values) -> values.forEach(value -> rebased.addPostParam(name, value)));
        original.getQueryParams().forEach((name, values) -> values.forEach(value -> rebased.addQueryParam(name, value)));
        original.getHeaderParams().forEach((name, values) -> values.forEach(value -> rebased.addHeaderParam(name, value)));
        rebased.setAuth(original.getUsername(), original.getPassword());

        return super.makeRequest(rebased);
    }
}
