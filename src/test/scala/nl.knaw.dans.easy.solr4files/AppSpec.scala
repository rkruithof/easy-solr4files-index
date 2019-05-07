/**
 * Copyright (C) 2017 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.solr4files

import java.util.UUID

import nl.knaw.dans.easy.solr4files.components._
import org.apache.commons.io.FileUtils.write
import org.apache.http.HttpStatus._
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrRequest, SolrResponse }
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.util.NamedList
import scalaj.http.{ BaseHttp, Http, HttpResponse }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

class AppSpec extends TestSupportFixture {

  private val storeName = "pdbs"
  private implicit val http: BaseHttp = Http

  private class EmptyDDM extends DDM(<empty/>)
  private val mockedDDM: DDM = mock[EmptyDDM]

  private class StubbedSolrApp() extends TestApp {
    override val solrClient: SolrClient = new SolrClient() {
      // can't use mock because SolrClient has a final method

      override def deleteByQuery(q: String): UpdateResponse = new UpdateResponse

      override def commit(): UpdateResponse = new UpdateResponse

      override def add(doc: SolrInputDocument): UpdateResponse = new UpdateResponse {
        override def getStatus = 200
      }

      override def close(): Unit = ()

      override def request(solrRequest: SolrRequest[_ <: SolrResponse], s: String): NamedList[AnyRef] = {
        new NamedList[AnyRef]() {
          // non-zero status (or an Exception) provokes a retry without content
          add("status", nrOfImageStreams(solrRequest).toString)
        }
      }
    }
  }

  private def nrOfImageStreams(solrRequest: SolrRequest[_ <: SolrResponse]) = {
    if (solrRequest.getContentStreams.isEmpty)
      0
    else {
      val solrDocId = solrRequest
        .asInstanceOf[ContentStreamUpdateRequest]
        .getParams.getMap.asScala
        .getOrElse("literal.id", Array(""))
        .mkString.toLowerCase
      if (solrDocId.endsWith(".mpg"))
        1
      else 0
    }
  }

  private def anonymousAuthItem(uuid: UUID, path: String) = Success(
    s"""{
       |  "itemId":"$uuid/$path",
       |  "owner":"someone",
       |  "dateAvailable":"1992-07-30",
       |  "accessibleTo":"ANONYMOUS",
       |  "visibleTo":"ANONYMOUS"
       |}""".stripMargin)

  "update" should "call the stubbed solrClient.request" in {
    initVault()
    assume(canConnectToEasySchemas)
    val app = new StubbedSolrApp()
    Seq(
      "data/path/to/a/random/video/hubble.mpg",
      "data/reisverslag/centaur-nederlands.srt",
      "data/reisverslag/centaur.mpg",
      "data/reisverslag/centaur.srt",
      "data/reisverslag/deel01.docx",
      "data/reisverslag/deel01.txt",
      "data/reisverslag/deel02.txt",
      "data/reisverslag/deel03.txt",
      "data/ruimtereis01_verklaring.txt"
    ).foreach(f => app.expectsHttpAsString(anonymousAuthItem(uuidCentaur, f)) once())

    val result = app.update(storeName, uuidCentaur)
    inside(result) { case Success(feedback) =>
      feedback.toString shouldBe s"Bag ${ uuidCentaur }: 7 times FileSubmittedWithContent, 2 times FileSubmittedWithOnlyMetadata"
    }
  }

  it should "report problems when easy-auth-info is down" in {
    // this tests the chain of error handling starting at AuthInfo
    // other FileItem problems are tested with "updateFiles"
    initVault()
    val app = new StubbedSolrApp()
    app.expectsHttpAsString(Failure(HttpStatusException("", HttpResponse("", SC_SERVICE_UNAVAILABLE, Map("Status" -> IndexedSeq("")))))) once()

    val result = app.update(storeName, uuidCentaur)
    inside(result) {
      case Failure(MixedResultsException(_, HttpStatusException(_, HttpResponse(_, SC_SERVICE_UNAVAILABLE, _)))) =>
    }
  }

  it should "not stumble on difficult file names" in {
    initVault()
    assume(canConnectToEasySchemas)
    val app = new StubbedSolrApp()
    app.expectsHttpAsString(anonymousAuthItem(uuidAnonymized, "YYY")) anyNumberOfTimes()

    val result = app.update(storeName, uuidAnonymized)
    inside(result) { case Success(feedback) =>
      feedback.toString shouldBe s"Bag ${ uuidAnonymized }: 2 times FileSubmittedWithContent, 1 times FileSubmittedWithOnlyMetadata"
    }
  }

  "updateFiles" should "retrieve checksum, files size nor DDM.solrLiterals because file is not accessible" in {
    val app = new StubbedSolrApp()
    app.expectsHttpAsString(Success(
      s"""{
         |  "itemId":"$uuid/xy.z",
         |  "owner":"someone",
         |  "dateAvailable":"1992-07-30",
         |  "accessibleTo":"NONE",
         |  "visibleTo":"NONE"
         |}""".stripMargin)
    ) once()
    class MockedBag extends Bag("pdbs", uuid, mock[Vault])
    val result = app.updateFiles(mock[MockedBag], mockedDDM, <files><file filepath="xy.z"/></files>)
    result shouldBe Success(BagSubmitted(uuid.toString, Seq.empty))
  }

  it should "report problems when easy-auth-info returns invalid content" in {
    clearVault()
    write(testDir.resolve(s"vault/stores/pdbs/bags/$uuid/metadata/dataset.xml").toFile, "<ddm/>")
    write(testDir.resolve(s"vault/stores/pdbs/bags/$uuid/metadata/files.xml").toFile, "<files><file filepath='xy.z'/></files>")
    val authInfoJson = """{ "visibleTo":"ANONYMOUS" }""".stripMargin
    val app = new StubbedSolrApp()
    app.expectsHttpAsString(Success(authInfoJson))

    val result = app.update(storeName, uuid)
    inside(result) {
      case Failure(MixedResultsException(_, e)) => e.getMessage shouldBe
        s"""parse error [class org.json4s.package$$MappingException: No usable value for itemId
           |Did not find value which can be converted into java.lang.String] for: ${ authInfoJson.toOneLiner }""".stripMargin
    }
  }

  it should "report an invalid bag: file-item without a path" in {
    val app = new StubbedSolrApp()
    class MockedBag extends Bag("pdbs", uuid, mock[Vault])
    val result = app.updateFiles(mock[MockedBag], mockedDDM, <files><file/></files>)
    inside(result) {
      case Failure(MixedResultsException(_, e)) => e.getMessage shouldBe
        s"invalid files.xml for $uuid: filepath attribute is missing in <file/>"
    }
  }

  it should "accept a bag without files" in {
    val app = new StubbedSolrApp()
    class MockedBag extends Bag("pdbs", uuid, mock[Vault])
    val result = app.updateFiles(mock[MockedBag], mockedDDM, <files></files>)
    result shouldBe Success(BagSubmitted(uuid.toString, Seq.empty))
  }

  "delete" should "call the stubbed solrClient.deleteByQuery" in {
    val result = new StubbedSolrApp().delete("*:*")
    inside(result) { case Success(msg) =>
      msg shouldBe s"Deleted documents with query *:*"
    }
  }

  "initSingleStore" should "call the stubbed ApplicationWiring.update method" in {
    clearVault()
    write(testDir.resolve("vault/stores/pdbs/bags").toFile,
      """    9da0541a-d2c8-432e-8129-979a9830b427
        |    24d305fc-060c-4b3b-a5f5-9f212d463cbc
        |    3528bd4c-a87a-4bfa-9741-a25db7ef758a
        |    f70c19a5-0725-4950-aa42-6489a9d73806
        |    6ccadbad-650c-47ec-936d-2ef42e5f3cda""".stripMargin
    )
    val result = new StubbedSolrApp() {
      // vaultBagIds/bags can't be a file and directory so we need a mockedDDM, a failure demonstrates it's called
      override def update(store: String, uuid: UUID): Try[BagSubmitted] = {
        Failure(new Exception("stubbed ApplicationWiring.update"))
      }
    }.initSingleStore(storeName)

    inside(result) { case Failure(e) => e should have message "stubbed ApplicationWiring.update" }
  }

  "initAllStores" should "call the stubbed ApplicationWiring.initSingleStore method" in {
    clearVault()
    write(testDir.resolve("vault/stores").toFile,
      """    <http://localhost:20110/stores/foo>
        |    <http://localhost:20110/stores/bar>
        |    <http://localhost:20110/stores/rabarbera>
        |    <http://localhost:20110/stores/barbapapa>""".stripMargin
    )
    val result = new StubbedSolrApp() {

      // vaultStoreNames/stores can't be a file and directory so we need a mockedDDM, a failure demonstrates it's called
      override def initSingleStore(storeName: String): Try[StoreSubmitted] = {
        Failure(new Exception("stubbed ApplicationWiring.initSingleStore"))
      }
    }.initAllStores()
    inside(result) { case Failure(e) => e should have message "stubbed ApplicationWiring.initSingleStore" }
  }
}
