package filters;

import akka.util.ByteString;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.telemetry.util.TelemetryEvents;
import org.sunbird.telemetry.util.TelemetryLmaxWriter;
import org.sunbird.telemetry.util.TelemetryUtil;
import play.libs.streams.Accumulator;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Result;

public class AccessLogFilter extends EssentialFilter {

  private final Executor executor;
  private TelemetryLmaxWriter lmaxWriter;
  private ExecutionContext executionContext;

  @Inject
  public AccessLogFilter(Executor executor) {
    super();
    this.lmaxWriter = TelemetryLmaxWriter.getInstance();
    this.executor = executor;
    this.executionContext = ExecutionContext;
  }

  @Override
  public EssentialAction apply(EssentialAction next) {
    return EssentialAction.of(
        request -> {
          long startTime = System.currentTimeMillis();
          Accumulator<ByteString, Result> accumulator = next.apply(request);
          return accumulator.map(
              result -> {
                long endTime = System.currentTimeMillis();
                long requestTime = endTime - startTime;
                try {
                  org.sunbird.common.request.Request req = new org.sunbird.common.request.Request();
                  Map<String, Object> params = new WeakHashMap<>();
                  params.put(JsonKey.URL, request.uri());
                  params.put(JsonKey.METHOD, request.method());
                  params.put(JsonKey.LOG_TYPE, JsonKey.API_ACCESS);
                  params.put(JsonKey.MESSAGE, "");
                  params.put(JsonKey.METHOD, request.method());
                  params.put(JsonKey.DURATION, requestTime);
                  params.put(JsonKey.STATUS, result.status());
                  params.put(JsonKey.LOG_LEVEL, JsonKey.INFO);
                  Map<String, Object> context = new HashMap();
                  context.putAll(ExecutionContext.getCurrent().getRequestContext());
                  context.putAll(ExecutionContext.getCurrent().getGlobalContext());
                  // return context;
                  req.setRequest(
                      generateTelemetryRequestForController(
                          TelemetryEvents.LOG.getName(),
                          params,
                          TelemetryUtil.getTelemetryContext()));
                  lmaxWriter.submitMessage(req);
                } catch (Exception ex) {
                  ProjectLogger.log("AccessLogFilter:apply Exception in writing telemetry", ex);
                }
                return result;
              },
              executor);
        });
  }

  private Map<String, Object> generateTelemetryRequestForController(
      String eventType, Map<String, Object> params, Map<String, Object> context) {

    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.TELEMETRY_EVENT_TYPE, eventType);
    map.put(JsonKey.CONTEXT, context);
    map.put(JsonKey.PARAMS, params);
    return map;
  }
}
