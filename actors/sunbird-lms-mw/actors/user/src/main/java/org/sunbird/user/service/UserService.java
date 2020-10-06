package org.sunbird.user.service;

import akka.actor.ActorRef;
import java.util.List;
import java.util.Map;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.models.user.User;

public interface UserService {

  User getUserById(String userId, RequestContext context);

  void validateUserId(Request request, String managedById, RequestContext context);

  void validateUploader(Request request, RequestContext context);

  Map<String, Object> esGetPublicUserProfileById(String userId, RequestContext context);

  Map<String, Object> esGetPrivateUserProfileById(String userId, RequestContext context);

  void syncUserProfile(
      String userId,
      Map<String, Object> userDataMap,
      Map<String, Object> userPrivateDataMap,
      RequestContext context);

  String getRootOrgIdFromChannel(String channel, RequestContext context);

  String getCustodianChannel(
      Map<String, Object> userMap, ActorRef actorRef, RequestContext context);

  List<Map<String, Object>> esSearchUserByFilters(
      Map<String, Object> filters, RequestContext context);

  List<Map<String, Object>> searchUserLookup(
      String type, List<String> valueList, RequestContext context);

  List<String> generateUsernames(
      String name, List<String> excludedUsernames, RequestContext context);

  List<String> getEncryptedList(List<String> dataList, RequestContext context);

  String getCustodianOrgId(ActorRef actorRef, RequestContext context);

  Map<String, Object> fetchEncryptedToken(
      String parentId, List<Map<String, Object>> respList, RequestContext context);

  void appendEncryptedToken(
      Map<String, Object> encryptedTokenList,
      List<Map<String, Object>> respList,
      RequestContext context);
}
