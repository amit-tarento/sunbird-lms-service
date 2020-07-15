package org.sunbird.helper;

import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.cassandraimpl.CassandraDACImpl;
import org.sunbird.common.RequestContext;

/**
 * This class will provide cassandraOperationImpl instance.
 *
 * @author Manzarul
 */
public class ServiceFactory {
  private static CassandraOperation operation = null;
  private static CassandraConnectionManager connectionManager;

  private ServiceFactory() {}

  /**
   * On call of this method , it will provide a new CassandraOperationImpl instance on each call.
   *
   * @return
   */
  public static CassandraOperation getInstance(RequestContext requestContext) {
    if (null == connectionManager) {
      synchronized (ServiceFactory.class) {
        if (null == connectionManager) {
          connectionManager = CassandraConnectionMngrFactory.getInstance();
        }
      }
    }
    return new CassandraDACImpl(requestContext, connectionManager);
  }

  public static CassandraOperation getInstance() {
    if (null == connectionManager) {
      synchronized (ServiceFactory.class) {
        if (null == connectionManager) {
          connectionManager = CassandraConnectionMngrFactory.getInstance();
        }
      }
    }
    return new CassandraDACImpl(null, connectionManager);
  }
}
