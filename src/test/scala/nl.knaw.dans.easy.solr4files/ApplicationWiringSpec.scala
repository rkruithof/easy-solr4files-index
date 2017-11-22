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

import org.apache.commons.io.FileUtils.write
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrRequest, SolrResponse }
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.util.NamedList

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

class ApplicationWiringSpec extends TestSupportFixture {

  private val store = "pdbs"

  private class MockedAndStubbedWiring extends ApplicationWiring(configWithMockedVault) {
    override lazy val solrClient: SolrClient = new SolrClient() {
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

  "update" should "call the stubbed solrClient.request" in {
    initVault()
    assume(canConnectToEasySchemas)
    val result = new MockedAndStubbedWiring().update(store, uuidCentaur)
    inside(result) { case Success(feedback) =>
      feedback.toString shouldBe s"Bag ${ uuidCentaur }: 7 times FileSubmittedWithContent, 2 times FileSubmittedWithJustMetadata"
    }
  }

  it should "not stumble on difficult file names" in {
    initVault()
    assume(canConnectToEasySchemas)
    val result = new MockedAndStubbedWiring().update(store, uuidAnonymized)
    inside(result) { case Success(feedback) =>
      feedback.toString shouldBe s"Bag ${ uuidAnonymized }: 3 times FileSubmittedWithContent"
    }
  }

  "delete" should "call the stubbed solrClient.deleteByQuery" in {
    val result = new MockedAndStubbedWiring().delete("*:*")
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
    val result = new ApplicationWiring(configWithMockedVault) {
      // vaultBagIds/bags can't be a file and directory so we need a stub, a failure demonstrates it's called
      override def update(store: String, uuid: UUID) =
        Failure(new Exception("stubbed ApplicationWiring.update"))
    }.initSingleStore(store)

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
    val result = new ApplicationWiring(configWithMockedVault) {
      // vaultStoreNames/stores can't be a file and directory so we need a stub, a failure demonstrates it's called
      override def initSingleStore(store: String) = Failure(new Exception("stubbed ApplicationWiring.initSingleStore"))
    }.initAllStores()

    inside(result) { case Failure(e) => e should have message "stubbed ApplicationWiring.initSingleStore" }
  }
}
