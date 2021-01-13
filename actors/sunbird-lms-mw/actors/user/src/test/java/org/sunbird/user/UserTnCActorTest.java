package org.sunbird.user;

import static akka.testkit.JavaTestKit.duration;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.sunbird.actor.router.RequestRouter;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraOperationImpl;
import org.sunbird.common.ElasticSearchRestHighImpl;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectUtil;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.user.actors.UserTnCActor;
import org.sunbird.user.service.UserTncService;
import scala.concurrent.Promise;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
  ServiceFactory.class,
  RequestRouter.class,
  EsClientFactory.class,
  ElasticSearchRestHighImpl.class,
  SunbirdMWService.class,
  DataCacheHandler.class,
  UserTncService.class
})
@PowerMockIgnore({
  "jdk.internal.reflect.*",
  "javax.management.*",
  "javax.crypto.*",
  "javax.net.ssl.*",
  "javax.security.*"
})
public class UserTnCActorTest {
  private static ActorSystem system;
  private String tncConfig =
      "{\"latestVersion\":\"V1\",\"v1\":{\"url\":\"http://dev/terms.html\"},\"v2\":{\"url\":\"http://dev/terms.html\"},\"v4\":{\"url\":\"http://dev/terms.html\"}}";
  private String groupsConfig =
      "{\"latestVersion\":\"V1\",\"v1\":{\"url\":\"http://dev/terms.html\"},\"v2\":{\"url\":\"http://dev/terms.html\"},\"v4\":{\"url\":\"http://dev/terms.html\"}}";

  private String orgAdminTnc =
      "{\"latestVersion\":\"V1\",\"v1\":{\"url\":\"http://dev/terms.html\"},\"v2\":{\"url\":\"http://dev/terms.html\"},\"v4\":{\"url\":\"http://dev/terms.html\"}}";

  private static final Props props = Props.create(UserTnCActor.class);
  private static final CassandraOperation cassandraOperation = mock(CassandraOperationImpl.class);
  private static final String LATEST_VERSION = "V2";
  private static final String ACCEPTED_CORRECT_VERSION = "V1";
  private static final String ACCEPTED_INVALID_VERSION = "invalid";
  private static ElasticSearchService esService;

  @BeforeClass
  public static void setUp() {
    system = ActorSystem.create("system");
  }

  @Before
  public void beforeEachTest() throws Exception {
    PowerMockito.mockStatic(DataCacheHandler.class);
    PowerMockito.mockStatic(ServiceFactory.class);
    PowerMockito.mockStatic(RequestRouter.class);
    ActorRef actorRef = mock(ActorRef.class);
    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    when(RequestRouter.getActor(Mockito.anyString())).thenReturn(actorRef);

    when(ServiceFactory.getInstance()).thenReturn(cassandraOperation);

    Map<String, String> config = new HashMap<>();
    config.put(JsonKey.TNC_CONFIG, tncConfig);
    config.put("groups", groupsConfig);
    config.put("orgAdminTnc", orgAdminTnc);
    when(DataCacheHandler.getConfigSettings()).thenReturn(config);

    PowerMockito.mockStatic(EsClientFactory.class);
    esService = mock(ElasticSearchRestHighImpl.class);
    when(EsClientFactory.getInstance(Mockito.anyString())).thenReturn(esService);
    Promise<Boolean> promise = Futures.promise();
    promise.success(true);
    when(esService.update(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(promise.future());
  }

  @Test
  public void testAcceptUserTcnSuccessWithAcceptFirstTime() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);

    mockCassandraOperationGetUserCall(getUser(null));
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser(null));

    Response response =
        setRequest(ACCEPTED_CORRECT_VERSION).expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  @Test
  public void testAcceptUserTncSuccessManagedUser() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    mockCassandraOperationGetUserCall(getUser(LATEST_VERSION));
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser(LATEST_VERSION));

    Response response =
        setManagedUSerRequest(ACCEPTED_CORRECT_VERSION)
            .expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  private TestKit setManagedUSerRequest(String version) {
    mockCassandraOperation();
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_TNC_ACCEPT.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.VERSION, version);
    innerMap.put(JsonKey.USER_ID, "someUserId");
    innerMap.put(JsonKey.MANAGED_BY, "someUserId");
    reqObj.setRequest(innerMap);
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(reqObj, probe.getRef());
    return probe;
  }

  @Test
  public void testAcceptUserTncSuccessAlreadyAccepted() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    mockCassandraOperationGetUserCall(getUser(LATEST_VERSION));
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser(LATEST_VERSION));

    Response response =
        setRequest(ACCEPTED_CORRECT_VERSION).expectMsgClass(duration("10 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  @Test
  public void testAcceptUserTncForBlockedUser() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    Map<String, Object> user = new HashMap<>();
    user.put(JsonKey.ROOT_ORG_ID, "anyRootId");
    user.put(JsonKey.IS_DELETED, true);
    mockCassandraOperationGetUserCall(user);
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(user);

    ProjectCommonException response =
        setRequest(ACCEPTED_CORRECT_VERSION)
            .expectMsgClass(duration("10 second"), ProjectCommonException.class);
    Assert.assertEquals(ResponseCode.userAccountlocked.getErrorCode(), response.getCode());
    Assert.assertEquals("User account has been blocked .", response.getMessage());
  }

  @Test
  public void testAcceptUserTncFailureWithInvalidVersion() {
    ProjectCommonException exception =
        setRequest(ACCEPTED_INVALID_VERSION)
            .expectMsgClass(duration("10 second"), ProjectCommonException.class);
    Assert.assertTrue(
        null != exception
            && exception
                .getCode()
                .equalsIgnoreCase(ResponseCode.invalidParameterValue.getErrorCode()));
  }

  @Test
  public void testAllTncAcceptUserTcnSuccessWithAcceptFirstTime() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    mockCassandraOperationGetUserCall(getUser(LATEST_VERSION));
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser(LATEST_VERSION));

    Response response =
        setGroupsTncRequest(ACCEPTED_CORRECT_VERSION)
            .expectMsgClass(duration("1000 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  @Test
  public void testAllTncAcceptUserTcnSuccessWithSecondTime() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    mockCassandraOperationGetUserCall(getUser("v2"));
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser("v2"));

    Response response =
        setGroupsTncRequest(ACCEPTED_CORRECT_VERSION)
            .expectMsgClass(duration("1000 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  @Test
  public void testOrgAdminTnCSuccessWithAcceptFirstTime() throws Exception {
    UserTncService tncService = PowerMockito.mock(UserTncService.class);
    PowerMockito.whenNew(UserTncService.class).withNoArguments().thenReturn(tncService);
    mockCassandraOperationGetUserCall(getUser(ACCEPTED_CORRECT_VERSION));
    mockCassandraOperationGetUserOrgCall();
    PowerMockito.when(
            tncService.getUserById(Mockito.anyString(), Mockito.any(RequestContext.class)))
        .thenReturn(getUser(ACCEPTED_CORRECT_VERSION));

    Response response =
        setOrgAdminTncRequest(ACCEPTED_CORRECT_VERSION)
            .expectMsgClass(duration("1000 second"), Response.class);
    Assert.assertTrue(
        null != response && "SUCCESS".equals(response.getResult().get(JsonKey.RESPONSE)));
  }

  private TestKit setRequest(String version) {
    mockCassandraOperation();
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_TNC_ACCEPT.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.VERSION, version);
    reqObj.setRequest(innerMap);
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(reqObj, probe.getRef());
    return probe;
  }

  private TestKit setGroupsTncRequest(String version) {
    mockCassandraOperation();
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_TNC_ACCEPT.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.VERSION, version);
    innerMap.put(JsonKey.TNC_TYPE, "groups");
    innerMap.put(JsonKey.USER_ID, "123456");
    reqObj.setRequest(innerMap);
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(reqObj, probe.getRef());
    return probe;
  }

  private TestKit setOrgAdminTncRequest(String version) {
    mockCassandraOperation();
    Request reqObj = new Request();
    reqObj.setOperation(ActorOperations.USER_TNC_ACCEPT.getValue());
    HashMap<String, Object> innerMap = new HashMap<>();
    innerMap.put(JsonKey.VERSION, version);
    innerMap.put(JsonKey.TNC_TYPE, "orgAdminTnc");
    reqObj.setRequest(innerMap);
    TestKit probe = new TestKit(system);
    ActorRef subject = system.actorOf(props);
    subject.tell(reqObj, probe.getRef());
    return probe;
  }

  private void mockCassandraOperationGetUserCall(Map<String, Object> user) {
    Response response = new Response();
    List<Map<String, Object>> userList = new ArrayList<>();
    userList.add(user);
    response.put(JsonKey.RESPONSE, userList);
    when(cassandraOperation.getRecordById(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenReturn(response);
  }

  private void mockCassandraOperationGetUserOrgCall() {
    Response response = new Response();
    List<Map<String, Object>> orgs = new ArrayList<>();
    Map<String, Object> org = new HashMap<>();
    org.put(JsonKey.ORGANISATION_ID, "orgid1");
    org.put(JsonKey.ROLES, Arrays.asList("PUBLIC", "ORG_ADMIN"));
    orgs.add(org);
    response.put(JsonKey.RESPONSE, orgs);
    when(cassandraOperation.getRecordsByCompositeKey(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(response);
  }

  private void mockCassandraOperation() {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    when(cassandraOperation.updateRecord(
            Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.any()))
        .thenReturn(response);
  }

  private Map<String, Object> getUser(String lastAcceptedVersion) throws JsonProcessingException {
    Map<String, Object> user = new HashMap<>();
    user.put(JsonKey.NAME, "someName");
    user.put(JsonKey.IS_DELETED, false);
    user.put(JsonKey.MANAGED_BY, "managedby");
    if (lastAcceptedVersion != null) {
      user.put(JsonKey.TNC_ACCEPTED_VERSION, lastAcceptedVersion);
    }

    user.put(JsonKey.ROOT_ORG_ID, "orgid1");
    List<Map<String, Object>> orgs = new ArrayList<>();
    Map<String, Object> org = new HashMap<>();
    org.put(JsonKey.ORGANISATION_ID, "orgid1");
    org.put(JsonKey.ROLES, Arrays.asList("PUBLIC", "ORG_ADMIN"));
    orgs.add(org);
    user.put(JsonKey.ORGANISATIONS, orgs);
    // alltncAccepted
    ObjectMapper mapper = new ObjectMapper();
    if (lastAcceptedVersion != null) {
      Map<String, Object> allTncAccepted = new HashMap<>();
      Map<String, String> groupsTnc = new HashMap<>();
      groupsTnc.put(JsonKey.VERSION, "v2");
      groupsTnc.put(JsonKey.TNC_ACCEPTED_ON, ProjectUtil.getFormattedDate());
      allTncAccepted.put("groups", mapper.writeValueAsString(groupsTnc));
      user.put(JsonKey.ALL_TNC_ACCEPTED, allTncAccepted);
    }

    return user;
  }
}
