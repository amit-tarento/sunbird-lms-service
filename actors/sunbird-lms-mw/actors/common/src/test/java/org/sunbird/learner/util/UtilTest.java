package org.sunbird.learner.util;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.dispatch.Futures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.helper.ServiceFactory;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  EsClientFactory.class,
  ElasticSearchRestHighImpl.class,
  ElasticSearchHelper.class,
  CassandraOperationImpl.class,
})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class UtilTest {
  private static CassandraOperationImpl cassandraOperationImpl;
  private static ElasticSearchService esService;

  @Before
  public void beforeEachTest() {
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperationImpl = mock(CassandraOperationImpl.class);
    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
  }

  @Test
  public void testGetUserOrgDetails() {
    Response response = new Response();
    List<Map<String, Object>> responseList = new ArrayList<>();
    Map<String, Object> result = new HashMap<>();
    result.put(JsonKey.IS_DELETED, false);
    result.put(JsonKey.USER_ID, "123-456-789");
    result.put(JsonKey.ORGANISATION_ID, "1234567890");
    responseList.add(result);
    response.getResult().put(JsonKey.RESPONSE, responseList);
    List<String> ids = new ArrayList<>();
    ids.add("123-456-789");
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperationImpl);
    when(cassandraOperationImpl.getRecordsByPrimaryKeys(
            JsonKey.SUNBIRD, "user_organisation", ids, JsonKey.USER_ID, null))
        .thenReturn(response);

    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(getEsResponseMap());
    when(esService.getDataByIdentifier(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise.future());
    Promise<String> esPromise = Futures.promise();
    esPromise.success("success");

    Promise<Map<String, Map<String, Object>>> promise2 = Futures.promise();
    promise2.success(getEs2ResponseMap());
    when(esService.getEsResultByListOfIds(
            Mockito.anyList(), Mockito.anyList(), Mockito.anyString(), Mockito.any()))
        .thenReturn(promise2.future());
    List<Map<String, Object>> res = Util.getUserOrgDetails("123-456-789", null);
    Assert.assertNotNull(res);
  }

  public static Map<String, Object> getEsResponseMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.IS_ROOT_ORG, true);
    map.put(JsonKey.ID, "rootOrgId");
    map.put(JsonKey.CHANNEL, "anyChannel");
    return map;
  }

  public static Map<String, Map<String, Object>> getEs2ResponseMap() {
    Map<String, Map<String, Object>> map2 = new HashMap<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.IS_ROOT_ORG, true);
    map.put(JsonKey.ID, "rootOrgId");
    map.put(JsonKey.CHANNEL, "anyChannel");
    map2.put(JsonKey.RESPONSE, map);
    return map2;
  }
}
