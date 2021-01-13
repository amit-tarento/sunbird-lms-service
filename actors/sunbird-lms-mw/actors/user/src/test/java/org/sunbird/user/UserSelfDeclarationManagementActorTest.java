package org.sunbird.user;

import static akka.testkit.JavaTestKit.duration;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.actors.notificationservice.dao.impl.EmailTemplateDaoImpl;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.UserDeclareEntity;
import org.sunbird.user.actors.UserSelfDeclarationManagementActor;
import org.sunbird.user.dao.UserOrgDao;
import org.sunbird.user.dao.impl.UserOrgDaoImpl;
import org.sunbird.user.util.UserActorOperations;
import org.sunbird.user.util.UserUtil;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  DataCacheHandler.class,
  org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.class,
  EmailTemplateDaoImpl.class,
  Util.class,
  EsClientFactory.class,
  ElasticSearchRestHighImpl.class,
  UserUtil.class,
  DataCacheHandler.class,
  UserOrgDaoImpl.class,
  UserOrgDao.class
})
@PowerMockIgnore({"jdk.internal.reflect.*", "javax.management.*"})
public class UserSelfDeclarationManagementActorTest {

  private static final Props props = Props.create(UserSelfDeclarationManagementActor.class);
  private ActorSystem system = ActorSystem.create("system");
  private static CassandraOperationImpl cassandraOperation;
  private ObjectMapper mapper = new ObjectMapper();
  private static ElasticSearchService esService;
  private static UserOrgDao userOrgDao;

  @BeforeClass
  public static void setUp() {

    PowerMockito.mockStatic(ServiceFactory.class);
    PowerMockito.mockStatic(EmailTemplateDaoImpl.class);
    PowerMockito.mockStatic(org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.class);
    cassandraOperation = mock(CassandraOperationImpl.class);
  }

  private static Response getSuccessResponse() {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    return response;
  }

  @Before
  public void beforeTest() {

    PowerMockito.mockStatic(ServiceFactory.class);
    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);
    when(cassandraOperation.insertRecord(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap(),
            Mockito.any(RequestContext.class)))
        .thenReturn(cassandraInsertRecord());
    when(cassandraOperation.getRecordsByProperties(
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.anyMap(),
            Mockito.any(RequestContext.class)))
        .thenReturn(getCassandraRecordsByProperties());
    cassandraOperation.deleteRecord(
        Mockito.anyString(),
        Mockito.anyString(),
        Mockito.anyMap(),
        Mockito.any(RequestContext.class));
    cassandraOperation.updateRecord(
        Mockito.anyString(),
        Mockito.anyString(),
        Mockito.anyMap(),
        Mockito.anyMap(),
        Mockito.any(RequestContext.class));

    PowerMockito.mockStatic(Util.class);
    when(Util.encryptData(Mockito.anyString())).thenReturn("userExtId");

    PowerMockito.mockStatic(UserUtil.class);

    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Map<String, Object>> promise = Futures.promise();
    promise.success(getEsResponseMap());
    when(esService.getDataByIdentifier(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(promise.future());
    PowerMockito.mockStatic(DataCacheHandler.class);
    when(DataCacheHandler.getConfigSettings()).thenReturn(configSettingsMap());

    userOrgDao = Mockito.mock(UserOrgDao.class);
    PowerMockito.mockStatic(UserOrgDaoImpl.class);
    when(UserOrgDaoImpl.getInstance()).thenReturn(userOrgDao);
  }

  public static Map<String, Object> getEsResponseMap() {
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.IS_ROOT_ORG, true);
    map.put(JsonKey.ID, "rootOrgId");
    map.put(JsonKey.CHANNEL, "anyChannel");
    return map;
  }

  private Response cassandraInsertRecord() {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    return response;
  }

  private Response getCassandraRecordsByProperties() {
    Response response = new Response();
    List list = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.CREATED_BY, "createdBy");
    map.put(JsonKey.PROVIDER, "anyProvider");
    map.put(JsonKey.USER_ID, "userid1");
    map.put(JsonKey.ORG_ID, "org1");
    map.put(JsonKey.PERSONA, JsonKey.TEACHER_PERSONA);
    list.add(map);
    response.put(JsonKey.RESPONSE, list);
    return response;
  }

  @Test
  public void testAddUserSelfDeclaredDetailsSuccess() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPSERT_USER_SELF_DECLARATIONS.getValue());

    List<UserDeclareEntity> list = new ArrayList<>();
    list.add(addUserDeclaredEntity());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, list);
    request.setRequest(requestMap);

    subject.tell(request, probe.getRef());
    Response response = probe.expectMsgClass(duration("100 second"), Response.class);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    Assert.assertEquals(JsonKey.SUCCESS, response.getResult().get(JsonKey.RESPONSE));
  }

  @Test
  public void testAddRemoveEditProvidersUserSelfDeclaredDetailsSuccess() {

    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPSERT_USER_SELF_DECLARATIONS.getValue());
    List<UserDeclareEntity> list = new ArrayList<>();
    list.add(addUserDeclaredEntity());
    list.add(removeUserDeclaredEntity());
    list.add(editRoleChangeUserDeclaredEntity());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, list);
    request.setRequest(requestMap);
    subject.tell(request, probe.getRef());
    Response response = probe.expectMsgClass(duration("100 second"), Response.class);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    Assert.assertEquals(JsonKey.SUCCESS, response.getResult().get(JsonKey.RESPONSE));
  }

  @Test
  public void testEditOrgChangeUserSelfDeclaredDetailsSuccess() {

    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPSERT_USER_SELF_DECLARATIONS.getValue());
    List<UserDeclareEntity> list = new ArrayList<>();
    list.add(editOrgChangeUserDeclaredEntity());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, list);
    request.setRequest(requestMap);
    subject.tell(request, probe.getRef());
    Response response = probe.expectMsgClass(duration("100 second"), Response.class);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    Assert.assertEquals(JsonKey.SUCCESS, response.getResult().get(JsonKey.RESPONSE));
  }

  @Test
  public void testUpdateUserSelfDeclaredDetailsSuccess() {

    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPSERT_USER_SELF_DECLARATIONS.getValue());
    List<UserDeclareEntity> list = new ArrayList<>();
    list.add(editOrgChangeUserDeclaredEntity());
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, list);
    request.setRequest(requestMap);
    subject.tell(request, probe.getRef());
    Response response = probe.expectMsgClass(duration("100 second"), Response.class);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    Assert.assertEquals(JsonKey.SUCCESS, response.getResult().get(JsonKey.RESPONSE));
  }

  private UserDeclareEntity addUserDeclaredEntity() {
    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    userDeclareEntity.setOrgId("01234848481");
    userDeclareEntity.setPersona(JsonKey.TEACHER_PERSONA);
    userDeclareEntity.setStatus(JsonKey.SUBMITTED);
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put(JsonKey.DECLARED_EMAIL, "dsadaddasdadadadadE^JD");
    userInfo.put(JsonKey.DECLARED_PHONE, "0890321830");
    userDeclareEntity.setUserInfo(userInfo);
    userDeclareEntity.setOperation(JsonKey.ADD);
    return userDeclareEntity;
  }

  private UserDeclareEntity removeUserDeclaredEntity() {
    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    userDeclareEntity.setOrgId("018329328293892");
    userDeclareEntity.setPersona(JsonKey.TEACHER_PERSONA);
    userDeclareEntity.setStatus(JsonKey.SUBMITTED);
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put(JsonKey.DECLARED_EMAIL, "dsadaddasdadadadadE^JD");
    userInfo.put(JsonKey.DECLARED_PHONE, "0890321830");
    userDeclareEntity.setUserInfo(userInfo);
    userDeclareEntity.setOperation(JsonKey.REMOVE);
    return userDeclareEntity;
  }

  private UserDeclareEntity editRoleChangeUserDeclaredEntity() {
    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    userDeclareEntity.setOrgId("org1");
    userDeclareEntity.setPersona("volunteer");
    userDeclareEntity.setStatus(JsonKey.SUBMITTED);
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put(JsonKey.DECLARED_EMAIL, "dsadaddasdadadadadE^JD");
    userInfo.put(JsonKey.DECLARED_PHONE, "0890321830");
    userDeclareEntity.setUserInfo(userInfo);
    userDeclareEntity.setOperation(JsonKey.EDIT);
    return userDeclareEntity;
  }

  private UserDeclareEntity editOrgChangeUserDeclaredEntity() {
    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    userDeclareEntity.setOrgId("org2");
    userDeclareEntity.setPersona(JsonKey.TEACHER_PERSONA);
    userDeclareEntity.setStatus(JsonKey.SUBMITTED);
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put(JsonKey.DECLARED_EMAIL, "dsadaddasdadadadadE^JD");
    userInfo.put(JsonKey.DECLARED_PHONE, "0890321830");
    userDeclareEntity.setUserInfo(userInfo);
    userDeclareEntity.setOperation(JsonKey.EDIT);
    return userDeclareEntity;
  }

  @Test
  public void updateUserSelfDeclaredErrorStatus() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPDATE_USER_SELF_DECLARATIONS_ERROR_TYPE.getValue());
    UserDeclareEntity userDeclareEntity = userDeclaredEntityWithErrorStatus(true);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, userDeclareEntity);
    request.setRequest(requestMap);
    subject.tell(request, probe.getRef());
    Response response = probe.expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(null != response && response.getResponseCode() == ResponseCode.OK);
    Assert.assertEquals(JsonKey.SUCCESS, response.getResult().get(JsonKey.RESPONSE));
  }

  @Test
  public void testUpdateUserSelfDeclaredErrorStatusWithOtherStatus() {
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);

    Request request = new Request();
    request.setOperation(UserActorOperations.UPDATE_USER_SELF_DECLARATIONS_ERROR_TYPE.getValue());
    UserDeclareEntity userDeclareEntity = userDeclaredEntityWithErrorStatus(false);
    Map<String, Object> requestMap = new HashMap<>();
    requestMap.put(JsonKey.DECLARATIONS, userDeclareEntity);
    request.setRequest(requestMap);
    subject.tell(request, probe.getRef());
    ProjectCommonException projectCommonException =
        probe.expectMsgClass(duration("10 second"), ProjectCommonException.class);
    Assert.assertTrue(
        null != projectCommonException && projectCommonException.getResponseCode() == 500);
    Assert.assertEquals(
        ResponseCode.declaredUserErrorStatusNotUpdated.getErrorMessage(),
        projectCommonException.getMessage());
  }

  private UserDeclareEntity userDeclaredEntityWithErrorStatus(boolean errorStatus) {
    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    userDeclareEntity.setOrgId("org2");
    userDeclareEntity.setPersona(JsonKey.TEACHER_PERSONA);
    userDeclareEntity.setUserId("someUserID");
    if (errorStatus) {
      userDeclareEntity.setStatus(JsonKey.SELF_DECLARED_ERROR);
    } else {
      userDeclareEntity.setStatus(JsonKey.VALIDATED);
    }
    userDeclareEntity.setErrorType("ERROR-PHONE");
    return userDeclareEntity;
  }

  @Test
  public void testUpdateUserSelfDeclarations() throws Exception {

    UserDeclareEntity userDeclareEntity = new UserDeclareEntity();
    Map userInfo = new HashMap();
    userInfo.put(JsonKey.DECLARED_EMAIL, "9909090909");
    userInfo.put(JsonKey.DECLARED_PHONE, "abc@tenant.com");
    userDeclareEntity.setUserId("userid");
    userDeclareEntity.setOperation("add");
    userDeclareEntity.setPersona("somePersona");
    userDeclareEntity.setUserInfo(userInfo);
    Map<String, Object> reqMap = createUpdateUserDeclrationRequests();

    List userOrgLst = new LinkedList<HashMap<String, Object>>();
    Map userOrg = new HashMap();
    userOrg.put(JsonKey.ORGANISATION_ID, "someStateSubOrgId");
    userOrg.put(JsonKey.EXTERNAL_ID, "someStateExternalId");
    userOrgLst.add(userOrg);
    Response response = new Response();
    response.put(JsonKey.RESPONSE, userOrgLst);
    when(userOrgDao.getUserOrgDetails(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(response);
    doNothing()
        .when(
            UserUtil.class,
            "encryptDeclarationFields",
            Mockito.anyList(),
            Mockito.anyMap(),
            Mockito.any());
    when(UserUtil.class, "createUserDeclaredObject", Mockito.anyMap(), Mockito.anyString())
        .thenReturn(userDeclareEntity);
    boolean result =
        testScenario(
            getRequest(false, false, false, reqMap, ActorOperations.UPDATE_USER_DECLARATIONS),
            null);
    assertTrue(result);
  }

  public Map createUpdateUserDeclrationRequests() {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put(JsonKey.DECLARED_EMAIL, "abc@tenant.com");
    userInfo.put(JsonKey.DECLARED_PHONE, "9909090909");
    Map<String, Object> userDeclareFieldMap = new HashMap<>();
    userDeclareFieldMap.put(JsonKey.USER_ID, "userid");
    userDeclareFieldMap.put(JsonKey.ORG_ID, "orgID");
    userDeclareFieldMap.put(JsonKey.PERSONA, "somePersona");
    userDeclareFieldMap.put(JsonKey.OPERATION, JsonKey.ADD);
    userDeclareFieldMap.put(JsonKey.INFO, userInfo);
    List<Map<String, Object>> userDeclareEntityList = new ArrayList<>();
    userDeclareEntityList.add(userDeclareFieldMap);
    request.put(JsonKey.DECLARATIONS, userDeclareEntityList);
    return request;
  }

  private Map<String, String> configSettingsMap() {
    Map<String, String> configMap = new HashMap<>();
    configMap.put(JsonKey.CUSTODIAN_ORG_ID, "anyOrgId");
    return configMap;
  }

  public boolean testScenario(Request reqObj, ResponseCode errorCode) {

    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(reqObj, probe.getRef());

    if (errorCode == null) {
      Response res = probe.expectMsgClass(duration("1000 second"), Response.class);
      return null != res && res.getResponseCode() == ResponseCode.OK;
    } else {
      ProjectCommonException res =
          probe.expectMsgClass(duration("1000 second"), ProjectCommonException.class);
      return res.getCode().equals(errorCode.getErrorCode())
          || res.getResponseCode() == errorCode.getResponseCode();
    }
  }

  public Request getRequest(
      boolean isCallerIdReq,
      boolean isRootOrgIdReq,
      boolean isVersionReq,
      Map<String, Object> reqMap,
      ActorOperations actorOperation) {

    Request reqObj = new Request();
    HashMap<String, Object> innerMap = new HashMap<>();
    if (isCallerIdReq) innerMap.put(JsonKey.CALLER_ID, "anyCallerId");
    if (isVersionReq) innerMap.put(JsonKey.VERSION, "v2");
    if (isRootOrgIdReq) innerMap.put(JsonKey.ROOT_ORG_ID, "MY_ROOT_ORG_ID");
    innerMap.put(JsonKey.REQUESTED_BY, "requestedBy");
    reqObj.setRequest(reqMap);
    reqObj.setContext(innerMap);
    reqObj.setOperation(actorOperation.getValue());
    return reqObj;
  }
}
