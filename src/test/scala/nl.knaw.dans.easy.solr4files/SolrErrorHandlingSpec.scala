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

import org.apache.http.HttpStatus._
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.response.{ QueryResponse, UpdateResponse }
import org.apache.solr.client.solrj.{ SolrClient, SolrRequest, SolrResponse }
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.SolrParams
import org.apache.solr.common.util.NamedList
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.Success

class SolrErrorHandlingSpec extends TestSupportFixture
  with ServletFixture
  with ScalatraSuite {

  private val app = new TestApp() {
    override val solrClient: SolrClient = new SolrClient() {
      // can't use mock because SolrClient has a final method

      override def deleteByQuery(q: String): UpdateResponse = q match {
        case ":" => throw mockParseException
        case _ => new UpdateResponse
      }

      override def commit(): UpdateResponse =
        new UpdateResponse

      override def query(params: SolrParams): QueryResponse =
        throw mockParseException

      override def add(doc: SolrInputDocument): UpdateResponse =
        throw new Exception("mocked add")

      override def close(): Unit = ()

      override def request(solrRequest: SolrRequest[_ <: SolrResponse], s: String): NamedList[AnyRef] =
        throw new Exception("mocked request")

      private def mockParseException = {
        new HttpSolrClient.RemoteSolrException("mockedHost", 0, "mocked parser", new Exception()) {
          // try the query manually at deasy.dans.knaw.nl:8983/solr/#/fileitems/query
          // and see error.metadata.root-error-class in the response
          override def getRootThrowable: String = "org.apache.solr.parser.ParseException"
        }
      }
    }
  }
  addServlet(new UpdateServlet(app), "/fileindex/*")
  addServlet(new SearchServlet(app), "/filesearch/*")

  "delete" should "return the exception bubbling up from solrClient.deleteByQuery" in {
    delete("/fileindex/?q=:") {
      body shouldBe "Error from server at mockedHost: mocked parser"
      status shouldBe SC_BAD_REQUEST
    }
  }

  "submit" should "return the exception bubbling up from solrClient.request" in {
    assume(canConnectToEasySchemas)
    val path = "data/path/to/a/random/video/hubble.mpg"
    app.expectsHttpAsString(Success(
      s"""{
         |  "itemId":"$uuidCentaur/$path",
         |  "owner":"someone",
         |  "dateAvailable":"1992-07-30",
         |  "accessibleTo":"ANONYMOUS",
         |  "visibleTo":"ANONYMOUS"
         |}""".stripMargin))
    initVault()
    post(s"/fileindex/update/pdbs/${ uuidCentaur }") {
      body shouldBe s"solr update of file ${ uuidCentaur }/$path failed with mocked add"
      status shouldBe SC_INTERNAL_SERVER_ERROR
    }
  }

  "search" should "return the exception bubbling up from solrClient.query" in {
    get(s"/filesearch?text=:") {
      body shouldBe "Error from server at mockedHost: mocked parser"
      status shouldBe SC_BAD_REQUEST
    }
  }
}
