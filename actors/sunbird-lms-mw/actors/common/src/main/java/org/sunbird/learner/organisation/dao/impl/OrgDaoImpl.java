package org.sunbird.learner.organisation.dao.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.request.RequestContext;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.organisation.dao.OrgDao;
import org.sunbird.learner.organisation.external.identity.service.OrgExternalService;
import org.sunbird.learner.util.Util;

public class OrgDaoImpl implements OrgDao {

  private CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private static OrgDao orgDao = null;

  public static OrgDao getInstance() {
    if (orgDao == null) {
      orgDao = new OrgDaoImpl();
    }
    return orgDao;
  }

  @Override
  public Map<String, Object> getOrgById(String orgId, RequestContext context) {
    Util.DbInfo orgDb = Util.dbInfoMap.get(JsonKey.ORG_DB);
    Response response =
        cassandraOperation.getRecordById(orgDb.getKeySpace(), orgDb.getTableName(), orgId, context);
    List<Map<String, Object>> responseList =
        (List<Map<String, Object>>) response.get(JsonKey.RESPONSE);
    if (CollectionUtils.isNotEmpty(responseList)) {
      Map<String, Object> orgMap = responseList.get(0);
      orgMap.remove(JsonKey.CONTACT_DETAILS);
      return orgMap;
    }
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> esGetOrgByExternalId(
      String externalId, String provider, RequestContext context) {
    OrgExternalService orgExternalService = new OrgExternalService();
    String orgId =
        orgExternalService.getOrgIdFromOrgExternalIdAndProvider(externalId, provider, context);
    return getOrgById(orgId, context);
  }
}
