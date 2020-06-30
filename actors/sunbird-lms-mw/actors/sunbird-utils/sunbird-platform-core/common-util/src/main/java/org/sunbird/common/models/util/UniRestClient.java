package org.sunbird.common.models.util;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UniRestClient {

  static {
    Unirest.setDefaultHeader("Content-Type", "application/json");
    Unirest.setDefaultHeader("Connection", "Keep-Alive");
  }

  public static CompletableFuture<String> postAsync(String requestURL, String params, Map<String, String> headers) {
    CompletableFuture<String>  completableFuture = new CompletableFuture<>();
    HttpRequestWithBody requestWithBody = Unirest.post(requestURL);
    if (MapUtils.isNotEmpty(headers)) {
      requestWithBody.headers(headers);
    }

    requestWithBody.body(params).asStringAsync(
      new Callback<String>() {
        @Override
        public void completed(HttpResponse<String> httpResponse) {
          completableFuture.complete(httpResponse.getBody());
        }

        @Override
        public void failed(UnirestException e) {
          completableFuture.complete(e.getMessage());
        }

        @Override
        public void cancelled() {
          completableFuture.complete("cancelled");
        }
      }
    );
    return completableFuture;
  }

}
