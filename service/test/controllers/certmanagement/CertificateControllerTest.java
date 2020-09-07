package controllers.certmanagement;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import controllers.BaseApplicationTest;
import controllers.DummyActor;
import controllers.TestUtil;
import java.util.*;
import modules.OnRequestHandler;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.HeaderParam;
import org.sunbird.common.responsecode.ResponseCode;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

@PrepareForTest(OnRequestHandler.class)
public class CertificateControllerTest extends BaseApplicationTest {

  public static Map<String, List<String>> headerMap;

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
    headerMap.put(HeaderParam.X_Consumer_ID.getName(), Arrays.asList("Some consumer ID"));
    headerMap.put(HeaderParam.X_Device_ID.getName(), Arrays.asList("Some device ID"));
    headerMap.put(
        HeaderParam.X_Authenticated_Userid.getName(), Arrays.asList("Some authenticated user ID"));
    headerMap.put(JsonKey.MESSAGE_ID, Arrays.asList("Some message ID"));
    headerMap.put(HeaderParam.X_APP_ID.getName(), Arrays.asList("Some app Id"));
  }

  @After
  public void tearDown() throws Exception {
    headerMap.clear();
  }

  @Test
  public void tesCertificateDownloadSuccess() {
    Result result =
        TestUtil.performTest("/v1/user/certs/download", "POST", getRequest(), application);
    System.out.println("dfkd" + Helpers.contentAsString(result));
    assertEquals(
        ResponseCode.success.getErrorCode().toLowerCase(), TestUtil.getResponseCode(result));
  }

  @Test
  public void testCertificateDownloadFailure() {
    Result result =
        TestUtil.performTest("/v1/user/certs/download", "POST", getFailureReq(), application);
    assertEquals(
        ResponseCode.mandatoryParamsMissing.getErrorCode(), TestUtil.getResponseCode(result));
  }

  @Test
  public void tesCertificateValidateSuccess() {
    Result result =
        TestUtil.performTest(
            "/private/user/v1/certs/validate", "POST", getValidateReq(null), application);
    System.out.println("dfkd" + Helpers.contentAsString(result));
    assertEquals(
        ResponseCode.success.getErrorCode().toLowerCase(), TestUtil.getResponseCode(result));
  }

  @Test
  public void tesCertificateValidateFailure() {
    Result result =
        TestUtil.performTest(
            "/private/user/v1/certs/validate",
            "POST",
            getValidateReq(JsonKey.CERT_ID),
            application);
    System.out.println("dfkd" + Helpers.contentAsString(result));
    assertEquals(
        ResponseCode.mandatoryParamsMissing.getErrorCode(), TestUtil.getResponseCode(result));
  }

  private Map<String, Object> getRequest() {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put("pdfUrl", "pdf_url");
    request.put(JsonKey.REQUEST, reqMap);
    return request;
  }

  private Map<String, Object> getFailureReq() {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put(JsonKey.PDF_URL, StringUtils.EMPTY);
    request.put(JsonKey.REQUEST, reqMap);
    return request;
  }

  private Map<String, Object> getValidateReq(String param) {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> reqMap = new HashMap<>();
    reqMap.put(JsonKey.ACCESS_CODE, "pdf_url");
    reqMap.put(JsonKey.CERT_ID, "certificateId");
    reqMap.remove(param);
    request.put(JsonKey.REQUEST, reqMap);
    return request;
  }
}
