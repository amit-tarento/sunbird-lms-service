package controllers;

import static org.powermock.api.mockito.PowerMockito.mockStatic;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import modules.StartModule;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.actor.service.SunbirdMWService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.telemetry.util.TelemetryWriter;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import util.RequestInterceptor;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({RequestInterceptor.class, TelemetryWriter.class, SunbirdMWService.class})
public class BaseApplicationTest {
  protected Application application;
  private ActorSystem system;
  private Props props;

  public <T> void setup(Class<T> actorClass) {
    try {
      application =
          new GuiceApplicationBuilder()
              .in(new File("path/to/app"))
              .in(Mode.TEST)
              .disable(StartModule.class)
              .build();
      Helpers.start(application);
      system = ActorSystem.create("system");
      props = Props.create(actorClass);
      ActorRef subject = system.actorOf(props);
      BaseController.setActorRef(subject);
      mockStatic(TelemetryWriter.class);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Result performTest(String url, String method) {
    Http.RequestBuilder req = new Http.RequestBuilder().uri(url).method(method);
    Result result = Helpers.route(application, req);
    return result;
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
