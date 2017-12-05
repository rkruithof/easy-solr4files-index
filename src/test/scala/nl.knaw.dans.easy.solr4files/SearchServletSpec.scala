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
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrRequest, SolrResponse }
import org.apache.solr.common.params.SolrParams
import org.apache.solr.common.util.NamedList
import org.apache.solr.common.{ SolrDocument, SolrDocumentList }
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.scalatest.ScalatraSuite

import scalaj.http.Base64

class SearchServletSpec extends TestSupportFixture
  with ServletFixture
  with ScalatraSuite
  with MockFactory {

  private class StubbedWiring extends ApplicationWiring(configWithMockedVault) {

    override lazy val solrClient: SolrClient = new SolrClient() {
      // can't use mock because SolrClient has a final method

      override def query(params: SolrParams): QueryResponse = mockQueryResponse(params)

      override def close(): Unit = ()

      override def request(solrRequest: SolrRequest[_ <: SolrResponse], s: String): NamedList[AnyRef] =
        throw new Exception("not expected call")
    }

    private def mockQueryResponse(params: SolrParams) = {
      new QueryResponse {
        override def getResults: SolrDocumentList = new SolrDocumentList {
          setNumFound(3)
          setStart(0)
          add(new SolrDocument(new java.util.TreeMap[String, AnyRef] {
            put("debug", s"$params")
            // just for these tests: allows to verify what was sent to solr
            // so far any other part of the response is static for all tests
          }))
          add(new SolrDocument(new java.util.HashMap[String, AnyRef] {
            put("easy_file_name", "file.txt")
          }))
          add(new SolrDocument(new java.util.TreeMap[String, AnyRef] {
            // a sorted order makes testing easier
            put("easy_file_name", "some.png")
            put("easy_file_size", "123")
          }))
        }
      }
    }
  }

  private val app = new EasyUpdateSolr4filesIndexApp(new StubbedWiring)
  addServlet(new SearchServlet(app), "/*")

  "get /" should "translate to searching with *:* and the default parser" in {
    get("/?q=foo+bar") { // q is ignored as it is an unknown argument
      body.split("\n").find(_.startsWith("""    "debug""")) shouldBe Option("""    "debug":"q=*:*&fq=easy_file_accessible_to:ANONYMOUS&fq=easy_dataset_date_available:[*+TO+NOW]&fl=easy_dataset_*,easy_file_*&start=0&rows=10&timeAllowed=5000"""")
      status shouldBe SC_OK
    }
  }

  it should "return json" in {
    get(s"/?text=something") {
      header.get("Content-Type") shouldBe Some("application/json;charset=UTF-8")
      body should startWith(
        """{
          |  "summary":{
          |    "text":"something",
          |    "skip":0,
          |    "limit":10,
          |    "time_allowed":5000,
          |    "found":3,
          |    "returned":3
          |  },
          |  "fileitems":[{
          |    "debug":"q=something&defType=dismax&fq=easy_file_accessible_to:ANONYMOUS&fq=easy_dataset_date_available:[*+TO+NOW]&fl=easy_dataset_*,easy_file_*&start=0&rows=10&timeAllowed=5000"
          |  },{
          |""".stripMargin)
      body should include(
        """{
          |    "file_name":"file.txt"
          |  }""".stripMargin)
      body should include(
        """{
          |    "file_name":"some.png",
          |    "file_size":"123"
          |  }""".stripMargin)
      body should endWith(
        """
          |  }]
          |}""".stripMargin)
      status shouldBe SC_OK
    }
  }

  it should "translate limit to rows" in {
    get(s"/?text=foo+bar&limit=1") {
      body should include("&start=0&rows=1&timeAllowed=5000")
      status shouldBe SC_OK
    }
  }

  it should "translate skip to start" in {
    get(s"/?text=foo+bar&skip=1") {
      body should include("&start=1&rows=10&timeAllowed=5000")
      status shouldBe SC_OK
    }
  }

  it should "translate multivalued user specified filter" in {
    get(s"/?text=foo+bar&file_mime_type=application/pdf&file_mime_type=text/plain") {
      body should include("""&fq=easy_file_mime_type:application\\/pdf+OR+easy_file_mime_type:text\\/plain&""")
      status shouldBe SC_OK
    }
  }

  it should "translate encoded filter" in {
    get(s"/?text=foo+bar&file_mime_type=application%2Fpdf") {
      body should include("""&fq=easy_file_mime_type:application\\/pdf&""")
      status shouldBe SC_OK
    }
  }

  it should "escape user specified values to prevent potential injection: {!xmlparser..." in {
    get(s"/?text=nothing&file_mime_type=%7B!xmlparser") {
      body should include("""&fq=easy_file_mime_type:\\{\\!xmlparser&""")
      status shouldBe SC_OK
    }
  }

  it should "reject non-basic authentication" in {
    get(s"/?text=nothing", headers = Map("Authorization" ->
      """Digest realm="testrealm@host.com",
        |qop="auth,auth-int",
        |nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093",
        |opaque="5ccc069c403ebaf9f0171e9517f40e41"""".stripMargin)) {
      body shouldBe "Only anonymous search or basic authentication supported"
      status shouldBe SC_BAD_REQUEST
    }
  }

  it should "report no authentication available" in {
    get(s"/?text=nothing", headers = Map("Authorization" -> ("Basic " + Base64.encodeString("somebody:secret")))) {
      status shouldBe SC_SERVICE_UNAVAILABLE
      body shouldBe "Authentication service not available, try anonymous search"
    }
  }

  it should "apply authenticated filters" ignore { // TODO mock Authentication component alias LDAP
    get(s"/?text=nothing", headers = Map("Authorization" -> ("Basic " + Base64.encodeString("somebody:secret")))) {
      body should include("to:KNOWN+OR+easy_dataset_depositor_id:somebody")
      body should include("TO+NOW]+OR+easy_dataset_depositor_id:somebody")
      status shouldBe SC_OK
    }
  }
}
