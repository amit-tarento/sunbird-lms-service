package controllers.feed;

import controllers.BaseController;
import controllers.feed.validator.FeedRequestValidator;
import java.util.concurrent.CompletionStage;
import org.sunbird.common.models.util.ActorOperations;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import play.mvc.Http;
import play.mvc.Result;

public class FeedController extends BaseController {

  public CompletionStage<Result> getUserFeed(String userId, Http.Request httpRequest) {
    String callerId = httpRequest.flash().get(JsonKey.USER_ID);
    ProjectLogger.log("callerId from request flash : " + callerId, LoggerEnum.INFO.name());
    return handleRequest(
        ActorOperations.GET_USER_FEED_BY_ID.getValue(),
        null,
        req -> {
          FeedRequestValidator.userIdValidation(callerId, userId);
          return null;
        },
        userId,
        JsonKey.USER_ID,
        false,
        httpRequest);
  }
}
