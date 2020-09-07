package controllers.usermanagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import akka.http.javadsl.model.HttpMethods;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controllers.BaseApplicationTest;
import controllers.DummyActor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import modules.OnRequestHandler;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.responsecode.ResponseCode;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

@PrepareForTest(OnRequestHandler.class)
public class UserDataEncryptionControllerTest extends BaseApplicationTest {

  private static Map<String, String[]> headerMap;
  private static final String USER_ID = "someUserId";
  private static final String ENCRYPT_DATA_URL = "/v1/user/data/encrypt";
  private static final String DECRYPT_DATA_URL = "/v1/user/data/decrypt";

  @Before
  public void before() throws Exception {
    setup(DummyActor.class);
    mockStatic(RequestInterceptor.class);
    PowerMockito.mockStatic(SunbirdMWService.class);
    SunbirdMWService.tellToBGRouter(Mockito.any(), Mockito.any());
    Map userAuthentication = new HashMap<String, String>();
    userAuthentication.put(JsonKey.USER_ID, "userId");
    PowerMockito.when(RequestInterceptor.verifyRequestData(Mockito.anyObject()))
        .thenReturn(userAuthentication);
    mockStatic(OnRequestHandler.class);
    PowerMockito.doReturn("12345678990").when(OnRequestHandler.class, "getCustodianOrgHashTagId");
    headerMap = new HashMap<>();
    headerMap.put(HeaderParam.X_Consumer_ID.getName(), new String[] {"Service test consumer"});
    headerMap.put(HeaderParam.X_Device_ID.getName(), new String[] {"Some Device Id"});
    headerMap.put(
        HeaderParam.X_Authenticated_Userid.getName(), new String[] {"Authenticated user id"});
    headerMap.put(JsonKey.MESSAGE_ID, new String[] {"Unique Message id"});
  }

  @Test
  public void testEncryptDataFailureWithoutUserIds() {
    Result result =
        performTest(
            ENCRYPT_DATA_URL,
            HttpMethods.POST.name(),
            createInvalidEncryptionDecryptionRequest(false));
    assertEquals(getResponseCode(result), ResponseCode.mandatoryParamsMissing.getErrorCode());
    assertTrue(getResponseStatus(result) == 400);
  }

  @Test
  public void testEncryptDataFailureWithInvalidUserIdsDataType() {
    Result result =
        performTest(
            ENCRYPT_DATA_URL,
            HttpMethods.POST.name(),
            createInvalidEncryptionDecryptionRequest(true));
    assertEquals(getResponseCode(result), ResponseCode.dataTypeError.getErrorCode());
    assertTrue(getResponseStatus(result) == 400);
  }

  @Test
  public void testDecryptDataFailureWithoutUserIds() {
    Result result =
        performTest(
            DECRYPT_DATA_URL,
            HttpMethods.POST.name(),
            createInvalidEncryptionDecryptionRequest(false));
    assertEquals(getResponseCode(result), ResponseCode.mandatoryParamsMissing.getErrorCode());
    assertTrue(getResponseStatus(result) == 400);
  }

  @Test
  public void testDecryptDataFailureWithInvalidUserIdsDataType() {
    Result result =
        performTest(
            DECRYPT_DATA_URL,
            HttpMethods.POST.name(),
            createInvalidEncryptionDecryptionRequest(true));
    assertEquals(getResponseCode(result), ResponseCode.dataTypeError.getErrorCode());
    assertTrue(getResponseStatus(result) == 400);
  }

  private Map createInvalidEncryptionDecryptionRequest(boolean isUserIdsPresent) {
    Map<String, Object> requestMap = new HashMap<>();
    Map<String, Object> innerMap = new HashMap<>();
    if (isUserIdsPresent) {
      innerMap.put(JsonKey.USER_IDs, USER_ID);
    }
    requestMap.put(JsonKey.REQUEST, innerMap);
    return requestMap;
  }

  public Result performTest(String url, String method, Map map) {
    String data = mapToJson(map);
    Http.RequestBuilder req;
    if (StringUtils.isNotBlank(data)) {
      JsonNode json = Json.parse(data);
      req = new Http.RequestBuilder().bodyJson(json).uri(url).method(method);
    } else {
      req = new Http.RequestBuilder().uri(url).method(method);
    }
    // req.headers(new Http.Headers(headerMap));
    Result result = Helpers.route(application, req);
    return result;
  }

  public String mapToJson(Map map) {
    ObjectMapper mapperObj = new ObjectMapper();
    String jsonResp = "";

    if (map != null) {
      try {
        jsonResp = mapperObj.writeValueAsString(map);
      } catch (IOException e) {
        ProjectLogger.log(e.getMessage(), e);
      }
    }
    return jsonResp;
  }

  public String getResponseCode(Result result) {
    String responseStr = Helpers.contentAsString(result);
    ObjectMapper mapper = new ObjectMapper();

    try {
      Response response = mapper.readValue(responseStr, Response.class);

      if (response != null) {
        ResponseParams params = response.getParams();
        return params.getStatus();
      }
    } catch (Exception e) {
      ProjectLogger.log(
          "BaseControllerTest:getResponseCode: Exception occurred with error message = "
              + e.getMessage(),
          LoggerEnum.ERROR.name());
    }
    return "";
  }

  public int getResponseStatus(Result result) {
    return result.status();
  }
}
