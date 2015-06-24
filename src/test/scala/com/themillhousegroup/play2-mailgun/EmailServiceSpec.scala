package com.themillhousegroup.play2.mailgun

import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.mockito.ArgumentCaptor
import play.twirl.api.Html
import play.api.libs.ws._
import play.api.http._
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.apache.commons.fileupload._
import com.ning.http.multipart._
import org.jboss.netty.handler.codec.http.multipart.FileUpload
import scala.Predef._
import scala.Some

class EmailServiceSpec extends Specification with Mockito {
  val timeout = Duration(10, "seconds")
  val mockWS = mock[WSRequestHolder]
  val mockResponse = mock[WSResponse]
  mockResponse.json returns Json.obj("message" -> "OK", "id" -> "abc123")
  mockResponse.status returns 200
  mockWS.withAuth(any[String], any[String], any[WSAuthScheme]) returns mockWS
  mockWS.post[Array[Byte]](any[Array[Byte]])(any[Writeable[Array[Byte]]], any[ContentTypeOf[Array[Byte]]]) returns Future.successful(mockResponse)

  val noSenderEmailMessage = EmailMessage(None, "to@to.com", "subject", "text", Html("<em>text</em>"))
  val senderEmailMessage = EmailMessage(Some("from@from.com"), "to@to.com", "subject", "text", Html("<em>text</em>"))
  val emailService = new MailgunEmailService("apiKey", None)(mockWS)

  def parsedMailgunRequest(ws: WSRequestHolder) = {
    import scala.collection.JavaConverters._

    val byteCaptor = ArgumentCaptor.forClass(classOf[Array[Byte]])
    val contentTypeCaptor = ArgumentCaptor.forClass(classOf[ContentTypeOf[Array[Byte]]])

    there was one(mockWS).post(byteCaptor.capture())(any[Writeable[Array[Byte]]], contentTypeCaptor.capture())
    val theBytes = byteCaptor.getValue
    theBytes must not beNull

    println(new String(theBytes))

    val fu = new org.apache.commons.fileupload.FileUpload(new org.apache.commons.fileupload.disk.DiskFileItemFactory())
    val ctx = new UploadContext {
      def getCharacterEncoding = "UTF-8"

      def getContentType = contentTypeCaptor.getValue.mimeType.get

      def getContentLength = contentLength.toInt
      def contentLength = theBytes.length

      def getInputStream: java.io.InputStream = {
        new java.io.ByteArrayInputStream(theBytes)
      }
    }
    fu.parseRequest(ctx).asScala
  }

  "EmailService" should {
    "Bomb out if no default from address and none in supplied message" in {
      Await.result(emailService.send(noSenderEmailMessage), timeout) must throwAn[IllegalStateException]
    }

    "Use the sender from the message if supplied" in {
      val response = Await.result(emailService.send(senderEmailMessage), timeout)
      response must beEqualTo(MailgunResponse("OK", "abc123"))

      val multipartItems = parsedMailgunRequest(mockWS)

      multipartItems.size must beEqualTo(5)

    }
  }
}
