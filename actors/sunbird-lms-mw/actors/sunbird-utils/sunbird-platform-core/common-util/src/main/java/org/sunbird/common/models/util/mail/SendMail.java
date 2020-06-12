package org.sunbird.common.models.util.mail;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerEnum;
import org.sunbird.common.models.util.ProjectLogger;
import org.sunbird.common.models.util.PropertiesCache;

/**
 * this api is used to sending mail.
 *
 * @author Manzarul.Haque
 */
public class SendMail {

  private static Properties props = null;
  private static String host;
  private static String port;
  private static String userName;
  private static String password;
  private static String fromEmail;
  private static Session session;
  private static Transport transport;

  static {
    // collecting setup value from ENV
    host = System.getenv(JsonKey.EMAIL_SERVER_HOST);
    port = System.getenv(JsonKey.EMAIL_SERVER_PORT);
    userName = System.getenv(JsonKey.EMAIL_SERVER_USERNAME);
    password = System.getenv(JsonKey.EMAIL_SERVER_PASSWORD);
    fromEmail = System.getenv(JsonKey.EMAIL_SERVER_FROM);
    if (StringUtils.isBlank(host)
        || StringUtils.isBlank(port)
        || StringUtils.isBlank(userName)
        || StringUtils.isBlank(password)
        || StringUtils.isBlank(fromEmail)) {
      ProjectLogger.log(
          "Email setting value is not provided by Env variable=="
              + host
              + " "
              + port
              + " "
              + fromEmail,
          LoggerEnum.INFO.name());
      initialiseFromProperty();
    }
    props = System.getProperties();
    props.put("mail.smtp.host", host);
    props.put("mail.smtp.socketFactory.port", port);
    /*
     * props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
     */
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.port", port);

    getTransportClient();
  }

  private static Transport getTransportClient() {
    if (null == session) {
      session = Session.getInstance(props, new GMailAuthenticator(userName, password));
    }
    if (null == transport) {
      try {
        transport = session.getTransport("smtp");
        transport.connect(host, userName, password);
      } catch (Exception e) {
        ProjectLogger.log("Exception occurred while creating transport client.", e);
      }
    }
    return transport;
  }

  /** This method will initialize values from property files. */
  public static void initialiseFromProperty() {
    host = PropertiesCache.getInstance().getProperty(JsonKey.EMAIL_SERVER_HOST);
    port = PropertiesCache.getInstance().getProperty(JsonKey.EMAIL_SERVER_PORT);
    userName = PropertiesCache.getInstance().getProperty(JsonKey.EMAIL_SERVER_USERNAME);
    password = PropertiesCache.getInstance().getProperty(JsonKey.EMAIL_SERVER_PASSWORD);
    fromEmail = PropertiesCache.getInstance().getProperty(JsonKey.EMAIL_SERVER_FROM);
  }

  /**
   * Send email using given template name.
   *
   * @param emailList List of recipient emails
   * @param context Context for Velocity template
   * @param templateName Name of email template
   * @param subject Subject of email
   */
  public static boolean sendMail(
      String[] emailList, String subject, VelocityContext context, String templateName) {
    VelocityEngine engine = new VelocityEngine();
    Properties p = new Properties();
    p.setProperty("resource.loader", "class");
    p.setProperty(
        "class.resource.loader.class",
        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
    StringWriter writer = null;
    try {
      engine.init(p);
      Template template = engine.getTemplate(templateName);
      writer = new StringWriter();
      template.merge(context, writer);
    } catch (Exception e) {
      ProjectLogger.log(
          "SendMail:sendMail : Exception occurred with message = " + e.getMessage(), e);
    }

    return sendEmail(emailList, subject, context, writer);
  }

  /**
   * Send email using given template body.
   *
   * @param emailList List of recipient emails
   * @param context Context for Velocity template
   * @param templateBody Email template body
   * @param subject Subject of email
   */
  public static boolean sendMailWithBody(
      String[] emailList, String subject, VelocityContext context, String templateBody) {
    StringWriter writer = null;
    try {
      Velocity.init();
      writer = new StringWriter();
      Velocity.evaluate(context, writer, "SimpleVelocity", templateBody);
    } catch (Exception e) {
      ProjectLogger.log(
          "SendMail:sendMailWithBody : Exception occurred with message =" + e.getMessage(), e);
    }
    if (null != emailList && emailList.length == 1) {
      return sendEmailViaSendGrid(emailList[0], subject, writer);
    } else {
      return sendEmail(emailList, subject, context, writer);
    }
  }

  /**
   * Send email (with Cc) using given template name.
   *
   * @param emailList List of recipient emails
   * @param context Context for Velocity template
   * @param templateName Name of email template
   * @param subject Subject of email
   * @param ccEmailList List of Cc emails
   */
  public static void sendMail(
      String[] emailList,
      String subject,
      VelocityContext context,
      String templateName,
      String[] ccEmailList) {
    ProjectLogger.log("Mail Template name - " + templateName, LoggerEnum.INFO.name());

    try {
      getTransportClient();
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(fromEmail));
      int size = emailList.length;
      int i = 0;
      while (size > 0) {
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailList[i]));
        i++;
        size--;
      }
      size = ccEmailList.length;
      i = 0;
      while (size > 0) {
        message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccEmailList[i]));
        i++;
        size--;
      }
      message.setSubject(subject);
      VelocityEngine engine = new VelocityEngine();
      Properties p = new Properties();
      p.setProperty("resource.loader", "class");
      p.setProperty(
          "class.resource.loader.class",
          "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
      engine.init(p);
      Template template = engine.getTemplate(templateName);
      StringWriter writer = new StringWriter();
      template.merge(context, writer);
      message.setContent(writer.toString(), "text/html; charset=utf-8");
      getTransportClient().sendMessage(message, message.getAllRecipients());
    } catch (Exception e) {
      ProjectLogger.log("Exception occurred while sending mail.", e);
    }
  }

  /**
   * Send email (with attachment) and given body.
   *
   * @param emailList List of recipient emails
   * @param emailBody Text of email body
   * @param subject Subject of email
   * @param filePath Path of attachment file
   */
  public static void sendAttachment(
      String[] emailList, String emailBody, String subject, String filePath) {
    try {
      getTransportClient();
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(fromEmail));
      int size = emailList.length;
      int i = 0;
      while (size > 0) {
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailList[i]));
        i++;
        size--;
      }
      message.setSubject(subject);
      BodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setContent(emailBody, "text/html; charset=utf-8");
      // messageBodyPart.setText(mail);
      // Create a multipar message
      Multipart multipart = new MimeMultipart();
      multipart.addBodyPart(messageBodyPart);
      DataSource source = new FileDataSource(filePath);
      messageBodyPart = null;
      messageBodyPart = new MimeBodyPart();
      messageBodyPart.setDataHandler(new DataHandler(source));
      messageBodyPart.setFileName(filePath);
      multipart.addBodyPart(messageBodyPart);
      message.setSubject(subject);
      message.setContent(multipart);
      getTransportClient().sendMessage(message, message.getAllRecipients());
    } catch (Exception e) {
      ProjectLogger.log(e.toString(), e);
    }
  }

  private static boolean sendEmail(
      String[] emailList, String subject, VelocityContext context, StringWriter writer) {
    boolean sentStatus = true;
    try {
      if (context != null) {
        context.put(JsonKey.FROM_EMAIL, fromEmail);
      }
      getTransportClient();
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(fromEmail));
      RecipientType recipientType = null;
      if (emailList.length > 1) {
        recipientType = Message.RecipientType.BCC;
      } else {
        recipientType = Message.RecipientType.TO;
      }
      for (String email : emailList) {
        message.addRecipient(recipientType, new InternetAddress(email));
      }
      if (recipientType == Message.RecipientType.BCC)
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(fromEmail));
      message.setSubject(subject);
      message.setContent(writer.toString(), "text/html; charset=utf-8");
      getTransportClient().sendMessage(message, message.getAllRecipients());
    } catch (Exception e) {
      sentStatus = false;
      ProjectLogger.log(
          "SendMail:sendMail: Exception occurred with message = " + e.getMessage(), e);
    }
    return sentStatus;
  }

  private static boolean sendEmailViaSendGrid(String email, String subject, StringWriter writer) {
    Email from = new Email(fromEmail);
    Email to = new Email(email);
    Content content = new Content("text/html", writer.toString());
    Mail mail = new Mail(from, subject, to, content);

    SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
    com.sendgrid.Request request = new com.sendgrid.Request();
    try {
      request.setMethod(Method.POST);
      request.setEndpoint("mail/send");
      request.setBody(mail.build());
      com.sendgrid.Response response = sg.api(request);
      ProjectLogger.log(
          "SendMail:sendEmailViaSendGrid : Response from sendgrid :" + response.getStatusCode(),
          LoggerEnum.INFO.name());
      return response.getStatusCode() == 200;
    } catch (IOException ex) {
      ProjectLogger.log(
          "SendMail:sendEmailViaSendGrid : Exception occurred while sending mail via sendgrid", ex);
    }
    return false;
  }
}
