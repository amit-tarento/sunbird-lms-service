/** */
package org.sunbird.common.models.util.fcm;

import static org.powermock.api.mockito.PowerMockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.common.models.util.HttpClientUtil;
import org.sunbird.common.models.util.JsonKey;

/**
 * Test cases for FCM notification service.
 *
 * @author Manzarul
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(PowerMockRunner.class)
@PrepareForTest({
  HttpClients.class,
  CloseableHttpClient.class,
  ConnectionKeepAliveStrategy.class,
  PoolingHttpClientConnectionManager.class,
  CloseableHttpResponse.class,
  HttpGet.class,
  HttpPost.class,
  UrlEncodedFormEntity.class,
  HttpPatch.class,
  EntityUtils.class,
  HttpClientUtil.class
})
@PowerMockIgnore({
  "jdk.internal.reflect.*",
  "javax.management.*",
  "javax.net.ssl.*",
  "javax.security.*"
})
public class FCMNotificationTest {

  private CloseableHttpClient httpclient;

  @Before
  public void init() {
    PowerMockito.mockStatic(HttpClients.class);
    HttpClientBuilder clientBuilder = PowerMockito.mock(HttpClientBuilder.class);
    httpclient = PowerMockito.mock(CloseableHttpClient.class);
    PowerMockito.when(HttpClients.custom()).thenReturn(clientBuilder);
    PowerMockito.when(clientBuilder.build()).thenReturn(httpclient);
    HttpClientUtil.getInstance();
  }

  @Test
  public void testSendNotificationSuccessWithListAndStringData() {
    PowerMockito.mockStatic(HttpClientUtil.class);
    Map<String, Object> map = new HashMap<>();
    map.put("title", "some title");
    map.put("summary", "some value");
    List<Object> list = new ArrayList<>();
    list.add("test12");
    list.add("test45");

    map.put("extra", list);
    Map<String, Object> innerMap = new HashMap<>();
    innerMap.put("title", "some value");
    innerMap.put("link", "https://google.com");
    map.put("map", innerMap);

    String val = Notification.sendNotification("nameOFTopic", map, Notification.FCM_URL);
    Assert.assertEquals(JsonKey.FAILURE, val);
  }

  @Test
  public void testSendNotificationSuccessWithStringData() {
    PowerMockito.mockStatic(HttpClientUtil.class);
    Map<String, Object> map = new HashMap<>();
    map.put("title", "some title");
    map.put("summary", "some value");
    String val = Notification.sendNotification("nameOFTopic", map, Notification.FCM_URL);
    Assert.assertEquals(JsonKey.FAILURE, val);
  }

  @Test
  public void testSendNotificationFailureWithEmptyFcmUrl() {
    Map<String, Object> map = new HashMap<>();
    map.put("title", "some title");
    map.put("summary", "some value");
    String val = Notification.sendNotification("nameOFTopic", map, "");
    Assert.assertEquals(JsonKey.FAILURE, val);
  }

  @Test
  public void testSendNotificationFailureWithNullData() {
    Map<String, Object> map = null;
    String val = Notification.sendNotification("nameOFTopic", map, "");
    Assert.assertEquals(JsonKey.FAILURE, val);
  }

  @Test
  public void testSendNotificationFailureWithEmptyTopic() {
    Map<String, Object> map = new HashMap<>();
    map.put("title", "some title");
    map.put("summary", "some value");
    String val = Notification.sendNotification("", map, "");
    Assert.assertEquals(JsonKey.FAILURE, val);
  }

  @Before
  public void addMockRules() {
    PowerMockito.mockStatic(System.class);
    try {
      when(System.getenv(JsonKey.SUNBIRD_FCM_ACCOUNT_KEY)).thenReturn("FCM_KEY");
      when(System.getenv(AdditionalMatchers.not(Mockito.eq(JsonKey.SUNBIRD_FCM_ACCOUNT_KEY))))
          .thenCallRealMethod();

      CloseableHttpResponse response = PowerMockito.mock(CloseableHttpResponse.class);
      StatusLine statusLine = PowerMockito.mock(StatusLine.class);
      PowerMockito.when(response.getStatusLine()).thenReturn(statusLine);
      PowerMockito.when(statusLine.getStatusCode()).thenReturn(200);
      HttpEntity entity = PowerMockito.mock(HttpEntity.class);
      PowerMockito.when(response.getEntity()).thenReturn(entity);
      PowerMockito.mockStatic(EntityUtils.class);
      byte[] bytes = "{JsonKey.MESSAGE_Id:123}".getBytes();
      PowerMockito.when(EntityUtils.toByteArray(Mockito.any(HttpEntity.class))).thenReturn(bytes);
      PowerMockito.when(httpclient.execute(Mockito.any(HttpPost.class))).thenReturn(response);
      String res =
          HttpClientUtil.post(Notification.FCM_URL, "{\"message\":\"success\"}", new HashMap<>());
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail("Mock rules addition failed " + e.getMessage());
    }
  }
}
