package com.themillhousegroup.play2.mailgun

import play.api.libs.concurrent.Execution.Implicits._
import play.api.{ Play }
import play.api.Logger
import scala.concurrent.Future
import org.apache.commons.lang3.StringUtils
import play.api._
import play.api.http._
import play.api.libs.ws._
import play.api.Play.current
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.ning.http.client.multipart._
import java.io.ByteArrayOutputStream

import com.ning.http.client.providers.jdk.MultipartRequestEntity
import com.ning.http.client.multipart.{ FilePart, Part }
import play.api.libs.json
import java.nio.ByteBuffer
import javax.inject.Inject

/** For static-style usage: */
object MailgunEmailService extends MailgunEmailService(WS.client, Play.current)

class MailgunEmailService @Inject() (wsClient: WSClient, app: Application) extends MailgunResponseJson {
  lazy val configuration = app.configuration
  lazy val mailgunApiKey: String = configuration.getString("mailgun.api.key").get
  lazy val defaultSender: Option[String] = configuration.getString("mailgun.default.sender")
  lazy val mailgunUrl: String = configuration.getString("mailgun.api.url").get
  lazy val ws: WSRequest = wsClient.url(mailgunUrl)

  /** Sends the message via Mailgun's API, respecting any options provided */
  def send(message: EssentialEmailMessage, options: Set[MailgunOption] = Set()): Future[MailgunResponse] = {

    if (defaultSender.isEmpty && message.from.isEmpty) {
      Future.failed(new IllegalStateException("From: field is None and no default sender configured"))
    } else {
      val sender = message.from.getOrElse(defaultSender.get)
      val mpre = buildMultipartRequest(sender, message, options)

      ws
        .withHeaders(contentType(mpre))
        .withAuth("api", mailgunApiKey, WSAuthScheme.BASIC)
        .post(requestBytes(mpre))(Writeable.wByteArray)
        .flatMap(handleMailgunResponse)
    }
  }

  private def buildMultipartRequest(sender: String, message: EssentialEmailMessage, options: Set[MailgunOption]) = {
    //    val logo = Play.getExistingFile("/public/images/logo.png").get
    //    form.bodyPart(new FileDataBodyPart("inline", logo, MediaType.APPLICATION_OCTET_STREAM_TYPE))
    import scala.collection.JavaConverters._

    // Use the Ning AsyncHttpClient multipart class to get the bytes
    val parts = List[Part](
      new StringPart("from", sender),
      new StringPart("to", message.to),
      new StringPart("subject", message.subject),
      new StringPart("text", message.text),
      new StringPart("html", message.html.toString())
    )
    //      new FilePart("attachment", file)

    //    new MultipartRequestEntity(addOptions(parts, options).asJava, new FluentCaseInsensitiveStringsMap)
    MultipartUtils.newMultipartBody(addOptions(parts, options).asJava, new FluentCaseInsensitiveStringsMap)

  }

  private def addOptions(basicParts: List[Part], options: Set[MailgunOption]): List[Part] = {
    basicParts ++ options.map { o =>
      Logger.debug(s"Adding option $o: ${o.renderAsApiParameter}")
      o.renderAsApiParameter
    }
  }

  private def requestBytes(mpre: MultipartRequestEntity): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    mpre.writeRequest(baos)
    baos.toByteArray
  }

  private def requestBytes(mpb: MultipartBody): Array[Byte] = {
    val length = mpb.getContentLength.intValue
    val bb = ByteBuffer.allocate(length)
    mpb.read(bb)
    bb.array
  }

  private def contentType(mpb: MultipartBody) = {
    val contentType = mpb.getContentType
    "Content-Type" -> contentType
  }

  /**
   * As per https://documentation.mailgun.com/api-intro.html#errors
   */
  private def handleMailgunResponse(response: WSResponse): Future[MailgunResponse] = {
    if (response.status == Status.OK) {
      Future.successful(response.json.as[MailgunResponse])
    } else {
      Future.failed(
        response.status match {
          case Status.UNAUTHORIZED => new MailgunAuthenticationException((response.json \ "message").as[String])
          case _ => new MailgunSendingException((response.json \ "message").as[String])
        }
      )
    }

  }
}
