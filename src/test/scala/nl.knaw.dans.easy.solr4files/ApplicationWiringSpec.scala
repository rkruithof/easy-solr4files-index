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

import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrRequest, SolrResponse }
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.util.NamedList

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

class ApplicationWiringSpec extends TestSupportFixture {

  // values matching the vault mocked in test/resources
  private val uuid = UUID.fromString("9da0541a-d2c8-432e-8129-979a9830b427")
  private val store = "pdbs"

  private class MockedAndStubbedWiring extends ApplicationWiring(createConfig("vault")) {
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
    assume(canConnectToEasySchemas)
    val result = new MockedAndStubbedWiring().update(store, uuid)
    inside(result) { case Success(feedback) =>
      feedback.toString shouldBe s"Bag $uuid: 7 times FileSubmittedWithContent, 2 times FileSubmittedWithJustMetadata"
    }
  }

  "delete" should "call the stubbed solrClient.deleteByQuery" in {
    val result = new MockedAndStubbedWiring().delete("*:*")
    inside(result) { case Success(msg) =>
      msg shouldBe s"Deleted documents with query *:*"
    }
  }

  "initSingleStore" should "call the stubbed ApplicationWiring.update method" in {
    val result = new ApplicationWiring(createConfig("vaultBagIds")) {
      // vaultBagIds/bags can't be a file and directory so we need a stub, a failure demonstrates it's called
      override def update(store: String, uuid: UUID) =
        Failure(new Exception("stubbed ApplicationWiring.update"))
    }.initSingleStore(store)

    inside(result) { case Failure(e) => e should have message "stubbed ApplicationWiring.update" }
  }

  "initAllStores" should "call the stubbed ApplicationWiring.initSingleStore method" in {
    val result = new ApplicationWiring(createConfig("vaultStoreNames")) {
      // vaultStoreNames/stores can't be a file and directory so we need a stub, a failure demonstrates it's called
      override def initSingleStore(store: String) = Failure(new Exception("stubbed ApplicationWiring.initSingleStore"))
    }.initAllStores()

    inside(result) { case Failure(e) => e should have message "stubbed ApplicationWiring.initSingleStore" }
  }
}
