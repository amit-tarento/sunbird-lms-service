package org.sunbird.common;

public class RequestContext {

  private String mid;
  private String traceId;
  private String uri;
  private String jobName;
  private String actorOperation;

  private String query;
  private String dbOperation;
  private long totalTime;

  public RequestContext() {}

  public RequestContext(String jobName) {
    this.jobName = jobName;
  }

  public RequestContext(String traceId, String uri, String actorOperation) {
    this.traceId = traceId;
    this.uri = uri;
    this.actorOperation = actorOperation;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }

  public String getActorOperation() {
    return actorOperation;
  }

  public void setActorOperation(String actorOperation) {
    this.actorOperation = actorOperation;
  }

  public String getDbOperation() {
    return dbOperation;
  }

  public void setDbOperation(String dbOperation) {
    this.dbOperation = dbOperation;
  }

  public long getTotalTime() {
    return totalTime;
  }

  public void setTotalTime(long totalTime) {
    this.totalTime = totalTime;
  }

  public String getMid() {
    return mid;
  }

  public void setMid(String mid) {
    this.mid = mid;
  }

  public String getJobName() {
    return jobName;
  }

  public void setJobName(String jobName) {
    this.jobName = jobName;
  }
}
