package org.sunbird.user.util;

import static org.junit.Assert.assertNotNull;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.datasecurity.EncryptionService;
import org.sunbird.common.models.util.datasecurity.impl.DefaultEncryptionServivceImpl;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.Util;
import org.sunbird.user.service.UserService;
import org.sunbird.user.service.impl.UserServiceImpl;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  CassandraOperationImpl.class,
  DataCacheHandler.class,
  EsClientFactory.class,
  ElasticSearchRestHighImpl.class,
  DefaultEncryptionServivceImpl.class,
  Util.class,
  EncryptionService.class,
  UserService.class,
  UserServiceImpl.class,
  org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.class,
  UserLookUp.class
})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class SetUserDefaultValueTest {

  private static Response response;
  public static CassandraOperationImpl cassandraOperationImpl;
  private static ElasticSearchService esService;

  public void beforeEachTest() {
    PowerMockito.mockStatic(DataCacheHandler.class);
    response = new Response();
    List<Map<String, Object>> userMapList = new ArrayList<Map<String, Object>>();
    Map<String, Object> userMap = new HashMap<String, Object>();
    userMapList.add(userMap);
    Response existResponse = new Response();
    existResponse.put(JsonKey.RESPONSE, userMapList);
    PowerMockito.mockStatic(ServiceFactory.class);
    cassandraOperationImpl = mock(CassandraOperationImpl.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperationImpl);
    Map<String, String> settingMap = new HashMap<String, String>();
    settingMap.put(JsonKey.PHONE_UNIQUE, "True");
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperationImpl);
    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put(JsonKey.TYPE, JsonKey.EMAIL);
    reqMap.put(JsonKey.VALUE, "test@test.com");
    when(cassandraOperationImpl.getRecordsByCompositeKey(
            JsonKey.SUNBIRD, JsonKey.USER_LOOKUP, reqMap, null))
        .thenReturn(response);
    Map<String, Object> reqMapPhone = new HashMap<>();
    reqMap.put(JsonKey.TYPE, JsonKey.PHONE);
    reqMap.put(JsonKey.VALUE, "9663890400");
    when(cassandraOperationImpl.getRecordsByCompositeKey(
            JsonKey.SUNBIRD, JsonKey.USER_LOOKUP, reqMapPhone, null))
        .thenReturn(existResponse);
    when(DataCacheHandler.getConfigSettings()).thenReturn(settingMap);

    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    PowerMockito.when(
            cassandraOperationImpl.deleteRecord(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(response);
    PowerMockito.mockStatic(Util.class);
  }

  @Test
  public void setUserDefaultValueForV3() throws Exception {
    beforeEachTest();
    UserService userService = PowerMockito.mock(UserService.class);
    PowerMockito.mockStatic(UserServiceImpl.class);
    PowerMockito.when(UserServiceImpl.getInstance()).thenReturn(userService);
    List<String> usernameList = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      usernameList.add("username" + i);
    }
    when(userService.generateUsernames(Mockito.anyString(), Mockito.anyList(), Mockito.any()))
        .thenReturn(usernameList);
    when(userService.searchUserNameInUserLookup(Mockito.anyList(), Mockito.any()))
        .thenReturn(new ArrayList());
    when(userService.getEncryptedList(Mockito.anyList(), Mockito.any())).thenReturn(usernameList);
    UserLookUp userLookUp = PowerMockito.mock(UserLookUp.class);
    PowerMockito.whenNew(UserLookUp.class).withNoArguments().thenReturn(userLookUp);
    PowerMockito.when(
            userLookUp.checkUsernameUniqueness(
                Mockito.anyString(), Mockito.anyBoolean(), Mockito.any()))
        .thenReturn(true);
    Response response = new Response();
    response.getResult().put(JsonKey.RESPONSE, new ArrayList<>());
    PowerMockito.when(
            cassandraOperationImpl.getRecordsByCompositeKey(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(response);
    Map<String, Object> userMap = new HashMap<>();
    userMap.put(JsonKey.FIRST_NAME, "Test User");
    UserUtil.setUserDefaultValueForV3(userMap, null);
    assertNotNull(userMap.get(JsonKey.USERNAME));
    assertNotNull(userMap.get(JsonKey.STATUS));
    assertNotNull(userMap.get(JsonKey.ROLES));
  }
}
