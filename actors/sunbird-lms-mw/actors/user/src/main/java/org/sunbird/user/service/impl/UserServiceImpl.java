package org.sunbird.user.service.impl;

import akka.actor.ActorRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actorutil.systemsettings.SystemSettingClient;
import org.sunbird.actorutil.systemsettings.impl.SystemSettingClientImpl;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ElasticSearchHelper;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.factory.EsClientFactory;
import org.sunbird.common.inf.ElasticSearchService;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.*;
import org.sunbird.common.models.util.ProjectUtil.EsType;
import org.sunbird.common.models.util.datasecurity.DecryptionService;
import org.sunbird.common.models.util.datasecurity.EncryptionService;
import org.sunbird.common.models.util.datasecurity.impl.DefaultDecryptionServiceImpl;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.dto.SearchDTO;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.AdminUtilHandler;
import org.sunbird.learner.util.DataCacheHandler;
import org.sunbird.learner.util.Util;
import org.sunbird.models.adminutil.AdminUtilRequestData;
import org.sunbird.models.systemsetting.SystemSetting;
import org.sunbird.models.user.User;
import org.sunbird.user.dao.UserDao;
import org.sunbird.user.dao.UserExternalIdentityDao;
import org.sunbird.user.dao.impl.UserDaoImpl;
import org.sunbird.user.dao.impl.UserExternalIdentityDaoImpl;
import org.sunbird.user.service.UserService;
import scala.concurrent.Future;

public class UserServiceImpl implements UserService {

  private static DecryptionService decryptionService = new DefaultDecryptionServiceImpl();
  private EncryptionService encryptionService =
      org.sunbird.common.models.util.datasecurity.impl.ServiceFactory.getEncryptionServiceInstance(
          null);
  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private static UserDao userDao = UserDaoImpl.getInstance();
  private static UserService userService = null;
  private UserExternalIdentityDao userExtIdentityDao = new UserExternalIdentityDaoImpl();
  private Util.DbInfo usrDbInfo = Util.dbInfoMap.get(JsonKey.USER_DB);
  private static final int GENERATE_USERNAME_COUNT = 10;
  private ElasticSearchService esUtil = EsClientFactory.getInstance(JsonKey.REST);

  public static UserService getInstance() {
    if (userService == null) {
      userService = new UserServiceImpl();
    }
    return userService;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> esGetUserOrg(String userId, String orgId) {
    Map<String, Object> filters = new HashMap<>();
    filters.put(StringFormatter.joinByDot(JsonKey.ORGANISATIONS, JsonKey.ORGANISATION_ID), orgId);
    filters.put(StringFormatter.joinByDot(JsonKey.ORGANISATIONS, JsonKey.USER_ID), userId);
    Map<String, Object> map = new HashMap<>();
    map.put(JsonKey.FILTERS, filters);
    SearchDTO searchDto = Util.createSearchDto(map);
    Future<Map<String, Object>> resultF =
        esUtil.search(searchDto, ProjectUtil.EsType.user.getTypeName());
    Map<String, Object> result =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
    List<Map<String, Object>> userMapList = (List<Map<String, Object>>) result.get(JsonKey.CONTENT);
    if (CollectionUtils.isNotEmpty(userMapList)) {
      Map<String, Object> userMap = userMapList.get(0);
      return decryptionService.decryptData(userMap);
    } else {
      return Collections.EMPTY_MAP;
    }
  }

  @Override
  public User getUserById(String userId) {
    User user = userDao.getUserById(userId);
    if (null == user) {
      throw new ProjectCommonException(
          ResponseCode.userNotFound.getErrorCode(),
          ResponseCode.userNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    return user;
  }

  // This function is called during createUserV4 and update of users.
  @Override
  public void validateUserId(Request request, String managedById) {
    String userId = null;
    String ctxtUserId = (String) request.getContext().get(JsonKey.USER_ID);
    String managedForId = (String) request.getContext().get(JsonKey.MANAGED_FOR);
    if (StringUtils.isEmpty(ctxtUserId)) {
      // In case of create, pick the ctxUserId from a different header
      // TODO: Unify and rely on one header for the context user identification
      ctxtUserId = (String) request.getContext().get(JsonKey.REQUESTED_BY);
    } else {
      userId = userExtIdentityDao.getUserId(request);
    }
    ProjectLogger.log(
        "validateUserId :: ctxtUserId : "
            + ctxtUserId
            + " userId: "
            + userId
            + " managedById: "
            + managedById,
        LoggerEnum.INFO);
    // LIUA token is validated when LIUA is updating own account details or LIUA token is validated
    // when updating MUA details
    if ((StringUtils.isNotEmpty(managedForId) && !managedForId.equals(userId))
        || (StringUtils.isEmpty(managedById)
            && (!StringUtils.isBlank(userId) && !userId.equals(ctxtUserId))) // UPDATE
        || (StringUtils.isNotEmpty(managedById)
            && !(ctxtUserId.equals(managedById)))) // CREATE NEW USER/ UPDATE MUA {
    throw new ProjectCommonException(
          ResponseCode.unAuthorized.getErrorCode(),
          ResponseCode.unAuthorized.getErrorMessage(),
          ResponseCode.UNAUTHORIZED.getResponseCode());
  }

  @Override
  public void syncUserProfile(
      String userId, Map<String, Object> userDataMap, Map<String, Object> userPrivateDataMap) {
    esUtil.save(ProjectUtil.EsType.userprofilevisibility.getTypeName(), userId, userPrivateDataMap);
    esUtil.save(ProjectUtil.EsType.user.getTypeName(), userId, userDataMap);
  }

  @Override
  public Map<String, Object> esGetPublicUserProfileById(String userId) {
    Future<Map<String, Object>> esResultF =
        esUtil.getDataByIdentifier(ProjectUtil.EsType.user.getTypeName(), userId);
    Map<String, Object> esResult =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(esResultF);
    if (esResult == null || esResult.size() == 0) {
      throw new ProjectCommonException(
          ResponseCode.userNotFound.getErrorCode(),
          ResponseCode.userNotFound.getErrorMessage(),
          ResponseCode.RESOURCE_NOT_FOUND.getResponseCode());
    }
    return esResult;
  }

  @Override
  public Map<String, Object> esGetPrivateUserProfileById(String userId) {
    Future<Map<String, Object>> resultF =
        esUtil.getDataByIdentifier(ProjectUtil.EsType.userprofilevisibility.getTypeName(), userId);
    return (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(resultF);
  }

  @Override
  public String getValidatedCustodianOrgId(Map<String, Object> userMap, ActorRef actorRef) {
    String custodianOrgId = "";
    try {
      SystemSettingClient client = SystemSettingClientImpl.getInstance();
      SystemSetting systemSetting =
          client.getSystemSettingByField(actorRef, JsonKey.CUSTODIAN_ORG_ID);
      if (null != systemSetting && StringUtils.isNotBlank(systemSetting.getValue())) {
        custodianOrgId = systemSetting.getValue();
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserUtil:getValidatedCustodianOrgId: Exception occurred with error message = "
              + ex.getMessage(),
          ex);
      ProjectCommonException.throwServerErrorException(
          ResponseCode.errorSystemSettingNotFound,
          ProjectUtil.formatMessage(
              ResponseCode.errorSystemSettingNotFound.getErrorMessage(), JsonKey.CUSTODIAN_ORG_ID));
    }
    Map<String, Object> custodianOrg = null;
    if (StringUtils.isNotBlank(custodianOrgId)) {
      Future<Map<String, Object>> custodianOrgF =
          esUtil.getDataByIdentifier(ProjectUtil.EsType.organisation.getTypeName(), custodianOrgId);
      custodianOrg = (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(custodianOrgF);
      if (MapUtils.isNotEmpty(custodianOrg)) {

        if (null != custodianOrg.get(JsonKey.STATUS)) {
          int status = (int) custodianOrg.get(JsonKey.STATUS);
          if (1 != status) {
            ProjectCommonException.throwClientErrorException(
                ResponseCode.errorInactiveCustodianOrg);
          }
        } else {
          ProjectCommonException.throwClientErrorException(ResponseCode.errorInactiveCustodianOrg);
        }
      } else {
        ProjectCommonException.throwServerErrorException(
            ResponseCode.errorSystemSettingNotFound,
            ProjectUtil.formatMessage(
                ResponseCode.errorSystemSettingNotFound.getErrorMessage(),
                JsonKey.CUSTODIAN_ORG_ID));
      }
    } else {
      ProjectCommonException.throwServerErrorException(
          ResponseCode.errorSystemSettingNotFound,
          ProjectUtil.formatMessage(
              ResponseCode.errorSystemSettingNotFound.getErrorMessage(), JsonKey.CUSTODIAN_ORG_ID));
    }
    userMap.put(JsonKey.ROOT_ORG_ID, custodianOrgId);
    userMap.put(JsonKey.CHANNEL, custodianOrg.get(JsonKey.CHANNEL));
    return custodianOrgId;
  }

  @Override
  public String getRootOrgIdFromChannel(String channel) {

    Map<String, Object> filters = new HashMap<>();
    filters.put(JsonKey.IS_ROOT_ORG, true);
    if (StringUtils.isNotBlank(channel)) {
      filters.put(JsonKey.CHANNEL, channel);
    } else {
      // If channel value is not coming in request then read the default channel value provided from
      // ENV.
      if (StringUtils.isNotBlank(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DEFAULT_CHANNEL))) {
        filters.put(JsonKey.CHANNEL, ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DEFAULT_CHANNEL));
      } else {
        throw new ProjectCommonException(
            ResponseCode.mandatoryParamsMissing.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.CHANNEL),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
    }
    SearchDTO searchDTO = new SearchDTO();
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filters);
    Future<Map<String, Object>> esResultF =
        esUtil.search(searchDTO, EsType.organisation.getTypeName());
    Map<String, Object> esResult =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(esResultF);
    if (MapUtils.isNotEmpty(esResult)
        && CollectionUtils.isNotEmpty((List) esResult.get(JsonKey.CONTENT))) {
      Map<String, Object> esContent =
          ((List<Map<String, Object>>) esResult.get(JsonKey.CONTENT)).get(0);
      if (null != esContent.get(JsonKey.STATUS)) {
        int status = (int) esContent.get(JsonKey.STATUS);
        if (1 != status) {
          ProjectCommonException.throwClientErrorException(
              ResponseCode.errorInactiveOrg,
              ProjectUtil.formatMessage(
                  ResponseCode.errorInactiveOrg.getErrorMessage(), JsonKey.CHANNEL, channel));
        }
      } else {
        ProjectCommonException.throwClientErrorException(
            ResponseCode.errorInactiveOrg,
            ProjectUtil.formatMessage(
                ResponseCode.errorInactiveOrg.getErrorMessage(), JsonKey.CHANNEL, channel));
      }
      return (String) esContent.get(JsonKey.ID);
    } else {
      if (StringUtils.isNotBlank(channel)) {
        throw new ProjectCommonException(
            ResponseCode.invalidParameterValue.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.invalidParameterValue.getErrorMessage(), channel, JsonKey.CHANNEL),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      } else {
        throw new ProjectCommonException(
            ResponseCode.mandatoryParamsMissing.getErrorCode(),
            ProjectUtil.formatMessage(
                ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.CHANNEL),
            ResponseCode.CLIENT_ERROR.getResponseCode());
      }
    }
  }

  @Override
  public String getCustodianChannel(Map<String, Object> userMap, ActorRef actorRef) {
    String channel = (String) userMap.get(JsonKey.CHANNEL);
    if (StringUtils.isBlank(channel)) {
      try {
        Map<String, String> configSettingMap = DataCacheHandler.getConfigSettings();
        channel = configSettingMap.get(JsonKey.CUSTODIAN_ORG_CHANNEL);
        if (StringUtils.isBlank(channel)) {
          SystemSettingClient client = SystemSettingClientImpl.getInstance();
          SystemSetting custodianOrgChannelSetting =
              client.getSystemSettingByField(actorRef, JsonKey.CUSTODIAN_ORG_CHANNEL);
          if (custodianOrgChannelSetting != null
              && StringUtils.isNotBlank(custodianOrgChannelSetting.getValue())) {
            configSettingMap.put(
                custodianOrgChannelSetting.getId(), custodianOrgChannelSetting.getValue());
            channel = custodianOrgChannelSetting.getValue();
          }
        }
      } catch (Exception ex) {
        ProjectLogger.log(
            "Util:getCustodianChannel: Exception occurred while fetching custodian channel from system setting.",
            ex);
      }
    }
    if (StringUtils.isBlank(channel)) {
      channel = ProjectUtil.getConfigValue(JsonKey.SUNBIRD_DEFAULT_CHANNEL);
      userMap.put(JsonKey.CHANNEL, channel);
    }
    if (StringUtils.isBlank(channel)) {
      throw new ProjectCommonException(
          ResponseCode.mandatoryParamsMissing.getErrorCode(),
          ProjectUtil.formatMessage(
              ResponseCode.mandatoryParamsMissing.getErrorMessage(), JsonKey.CHANNEL),
          ResponseCode.CLIENT_ERROR.getResponseCode());
    }
    return channel;
  }

  @Override
  public void validateUploader(Request request) {
    // uploader and user should belong to same root org,
    // then only will allow to update user profile details.
    Map<String, Object> userMap = request.getRequest();
    String userId = (String) userMap.get(JsonKey.USER_ID);
    String uploaderUserId = (String) userMap.get(JsonKey.UPDATED_BY);
    User uploader = userService.getUserById(uploaderUserId);
    User user = userService.getUserById(userId);
    if (!user.getRootOrgId().equalsIgnoreCase(uploader.getRootOrgId())) {
      ProjectCommonException.throwUnauthorizedErrorException();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> getUserByUsername(String userName) {
    Response response =
        cassandraOperation.getRecordsByIndexedProperty(
            usrDbInfo.getKeySpace(), usrDbInfo.getTableName(), JsonKey.USERNAME, userName);
    List<Map<String, Object>> userList =
        (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
    if (CollectionUtils.isNotEmpty(userList)) {
      return userList.get(0);
    }
    return null;
  }

  @Override
  public List<String> getEncryptedList(List<String> dataList) {
    List<String> encryptedDataList = new ArrayList<>();
    for (String data : dataList) {
      String encData = "";
      try {
        encData = encryptionService.encryptData(data);
      } catch (Exception e) {
        ProjectLogger.log(
            "UserServiceImpl:getEncryptedDataList: Exception occurred with error message = "
                + e.getMessage());
      }
      if (StringUtils.isNotBlank(encData)) {
        encryptedDataList.add(encData);
      }
    }
    return encryptedDataList;
  }

  @Override
  public List<String> generateUsernames(String name, List<String> excludedUsernames) {
    if (name == null || name.isEmpty()) return null;
    name = Slug.makeSlug(name, true);
    int numOfDigitsToAppend =
        Integer.valueOf(ProjectUtil.getConfigValue(JsonKey.SUNBIRD_USERNAME_NUM_DIGITS).trim());
    HashSet<String> userNameSet = new HashSet<>();
    int totalUserNameGenerated = 0;
    String nameLowercase = name.toLowerCase().replaceAll("\\-+", "");
    while (totalUserNameGenerated < GENERATE_USERNAME_COUNT) {
      int numberSuffix = getRandomFixedLengthInteger(numOfDigitsToAppend);

      StringBuilder userNameSB = new StringBuilder();
      userNameSB.append(nameLowercase).append(numberSuffix);
      String generatedUsername = userNameSB.toString();

      if (!userNameSet.contains(generatedUsername)
          && !excludedUsernames.contains(generatedUsername)) {
        userNameSet.add(generatedUsername);
        totalUserNameGenerated += 1;
      }
    }
    return new ArrayList<>(userNameSet);
  }

  private int getRandomFixedLengthInteger(int numDigits) {
    int min = (int) Math.pow(10, numDigits - 1);
    int max = ((int) Math.pow(10, numDigits)) - 1;
    int randomNum = (int) (Math.random() * ((max - min) + 1)) + min;
    return randomNum;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Map<String, Object>> esSearchUserByFilters(Map<String, Object> filters) {
    SearchDTO searchDTO = new SearchDTO();

    List<String> list = new ArrayList<>();
    list.add(JsonKey.ID);
    list.add(JsonKey.USERNAME);

    searchDTO.setFields(list);
    searchDTO.getAdditionalProperties().put(JsonKey.FILTERS, filters);

    Future<Map<String, Object>> esResultF = esUtil.search(searchDTO, EsType.user.getTypeName());
    Map<String, Object> esResult =
        (Map<String, Object>) ElasticSearchHelper.getResponseFromFuture(esResultF);

    return (List<Map<String, Object>>) esResult.get(JsonKey.CONTENT);
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean checkUsernameUniqueness(String username, boolean isEncrypted) {
    try {
      if (!isEncrypted) username = encryptionService.encryptData(username);
    } catch (Exception e) {
      ProjectLogger.log(
          "UserServiceImpl:checkUsernameUniqueness: Exception occurred with error message = "
              + e.getMessage(),
          e);
      ProjectCommonException.throwServerErrorException(ResponseCode.userDataEncryptionError);
    }

    Response result =
        cassandraOperation.getRecordsByIndexedProperty(
            usrDbInfo.getKeySpace(), usrDbInfo.getTableName(), JsonKey.USERNAME, username);

    List<Map<String, Object>> userMapList =
        (List<Map<String, Object>>) result.get(JsonKey.RESPONSE);

    if (CollectionUtils.isNotEmpty(userMapList)) {
      return false;
    }
    return true;
  }

  @Override
  public String getCustodianOrgId(ActorRef actorRef) {
    String custodianOrgId = "";
    try {
      SystemSettingClient client = SystemSettingClientImpl.getInstance();
      SystemSetting systemSetting =
          client.getSystemSettingByField(actorRef, JsonKey.CUSTODIAN_ORG_ID);
      if (null != systemSetting && StringUtils.isNotBlank(systemSetting.getValue())) {
        custodianOrgId = systemSetting.getValue();
      }
    } catch (Exception ex) {
      ProjectLogger.log(
          "UserServiceImpl:getCustodianOrgId: Exception occurred with error message = "
              + ex.getMessage(),
          ex);
      ProjectCommonException.throwServerErrorException(
          ResponseCode.errorSystemSettingNotFound,
          ProjectUtil.formatMessage(
              ResponseCode.errorSystemSettingNotFound.getErrorMessage(), JsonKey.CUSTODIAN_ORG_ID));
    }
    return custodianOrgId;
  }

  /**
   * Fetch encrypted token list from admin utils
   *
   * @param parentId
   * @param respList
   * @return encryptedTokenList
   */
  public Map<String, Object> fetchEncryptedToken(
      String parentId, List<Map<String, Object>> respList) {
    Map<String, Object> encryptedTokenList = null;
    try {
      // create AdminUtilRequestData list of managedUserId and parentId
      List<AdminUtilRequestData> managedUsers = createManagedUserList(parentId, respList);
      // Fetch encrypted token list from admin utils
      encryptedTokenList =
          AdminUtilHandler.fetchEncryptedToken(
              AdminUtilHandler.prepareAdminUtilPayload(managedUsers));
    } catch (ProjectCommonException pe) {
      throw pe;
    } catch (Exception e) {
      throw new ProjectCommonException(
          ResponseCode.unableToParseData.getErrorCode(),
          ResponseCode.unableToParseData.getErrorMessage(),
          ResponseCode.SERVER_ERROR.getResponseCode());
    }
    return encryptedTokenList;
  }

  /**
   * Append encrypted token to the user list
   *
   * @param encryptedTokenList
   * @param respList
   */
  public void appendEncryptedToken(
      Map<String, Object> encryptedTokenList, List<Map<String, Object>> respList) {
    ArrayList<Map<String, Object>> data =
        (ArrayList<Map<String, Object>>) encryptedTokenList.get(JsonKey.DATA);
    for (Object object : data) {
      Map<String, Object> tempMap = (Map<String, Object>) object;
      respList
          .stream()
          .filter(o -> o.get(JsonKey.ID).equals(tempMap.get(JsonKey.SUB)))
          .forEach(
              o -> {
                o.put(JsonKey.MANAGED_TOKEN, tempMap.get(JsonKey.TOKEN));
              });
    }
  }

  /**
   * Create managed user user list with parentId(managedBY) and childId(managedUser) in admin util
   * request format
   *
   * @param parentId
   * @param respList
   * @return reqData List<AdminUtilRequestData>
   */
  private List<AdminUtilRequestData> createManagedUserList(
      String parentId, List<Map<String, Object>> respList) {
    List<AdminUtilRequestData> reqData =
        respList
            .stream()
            .map(p -> new AdminUtilRequestData(parentId, (String) p.get(JsonKey.ID)))
            .collect(Collectors.toList());
    reqData.forEach(System.out::println);
    return reqData;
  }
}
