package org.sunbird.auth.verifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.common.util.Time;
import org.sunbird.common.models.util.*;

public class AccessTokenValidator {
  private static LoggerUtil logger = new LoggerUtil(AccessTokenValidator.class);
  private static ObjectMapper mapper = new ObjectMapper();

  private static Map<String, Object> validateToken(String token) throws JsonProcessingException {
    String[] tokenElements = token.split("\\.");
    String header = tokenElements[0];
    String body = tokenElements[1];
    String signature = tokenElements[2];
    String payLoad = header + JsonKey.DOT_SEPARATOR + body;
    Map<Object, Object> headerData =
        mapper.readValue(new String(decodeFromBase64(header)), Map.class);
    String keyId = headerData.get("kid").toString();
    logger.info("token kid : " + keyId);
    logger.info("public key " + KeyManager.getPublicKey(keyId).getPublicKey());
    boolean isValid =
        CryptoUtil.verifyRSASign(
            payLoad,
            decodeFromBase64(signature),
            KeyManager.getPublicKey(keyId).getPublicKey(),
            JsonKey.SHA_256_WITH_RSA);
    logger.info("Token validity : " + isValid);
    if (isValid) {
      Map<String, Object> tokenBody =
          mapper.readValue(new String(decodeFromBase64(body)), Map.class);
      logger.info("Token body : " + tokenBody);
      boolean isExp = isExpired((Integer) tokenBody.get("exp"));
      logger.info("Token expiry : " + isExp);
      if (isExp) {
        return Collections.EMPTY_MAP;
      }
      return tokenBody;
    }
    return Collections.EMPTY_MAP;
  }

  /**
   * managedtoken is validated and requestedByUserID, requestedForUserID values are validated
   * aganist the managedEncToken
   *
   * @param managedEncToken
   * @param requestedByUserId
   * @param requestedForUserId
   * @return
   */
  public static String verifyManagedUserToken(
      String managedEncToken, String requestedByUserId, String requestedForUserId) {
    String managedFor = JsonKey.UNAUTHORIZED;
    try {
      Map<String, Object> payload = validateToken(managedEncToken);
      if (MapUtils.isNotEmpty(payload)) {
        String parentId = (String) payload.get(JsonKey.PARENT_ID);
        String muaId = (String) payload.get(JsonKey.SUB);
        logger.info(
            "AccessTokenValidator: parent uuid: "
                + parentId
                + " managedBy uuid: "
                + muaId
                + " requestedByUserID: "
                + requestedByUserId
                + " requestedForUserId: "
                + requestedForUserId);
        boolean isValid =
            parentId.equalsIgnoreCase(requestedByUserId)
                && muaId.equalsIgnoreCase(requestedForUserId);
        if (isValid) {
          managedFor = muaId;
        }
      }
    } catch (Exception ex) {
      logger.error("Exception in AccessTokenValidator: verify ", ex);
    }
    return managedFor;
  }

  public static String verifyUserToken(String token) {
    String userId = JsonKey.UNAUTHORIZED;
    try {
      logger.info("Token received to verify : " + token);
      Map<String, Object> payload = validateToken(token);
      if (MapUtils.isNotEmpty(payload) && checkIss((String) payload.get("iss"))) {
        userId = (String) payload.get(JsonKey.SUB);
        logger.info("Token sub : " + userId);
        if (StringUtils.isNotBlank(userId)) {
          int pos = userId.lastIndexOf(":");
          userId = userId.substring(pos + 1);
        }
      }
    } catch (Exception ex) {
      logger.error("Exception in verifyUserAccessToken: verify ", ex);
    }
    return userId;
  }

  private static boolean checkIss(String iss) {
    String realmUrl =
        KeyCloakConnectionProvider.SSO_URL + "realms/" + KeyCloakConnectionProvider.SSO_REALM;
    return (realmUrl.equalsIgnoreCase(iss));
  }

  private static boolean isExpired(Integer expiration) {
    return (Time.currentTime() > expiration);
  }

  private static byte[] decodeFromBase64(String data) {
    return Base64Util.decode(data, 11);
  }
}
