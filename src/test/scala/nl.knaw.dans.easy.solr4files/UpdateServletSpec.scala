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

import org.apache.http.HttpStatus._
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.{ Failure, Success }
import scalaj.http.HttpResponse

class UpdateServletSpec extends TestSupportFixture
  with EmbeddedJettyContainer
  with ScalatraSuite
  with MockFactory {

  private val app = mock[TestApp]
  addServlet(new UpdateServlet(app), "/*")

  "post /init[/:store]" should "return a feedback message for all stores" in {
    app.initAllStores _ expects() once() returning
      Success("xxx")
    post("/init") {
      body shouldBe "xxx"
      status shouldBe SC_OK
    }
  }

  it should "return a feedback message for a single store" in {
    (app.initSingleStore(_: String)) expects "pdbs" once() returning
      Success(StoreSubmitted("xxx"))
    post("/init/pdbs") {
      body shouldBe "xxx"
      status shouldBe SC_OK
    }
  }

  it should "return NOT FOUND for an empty path" in {
    post("/init/") {
      body should startWith("""Requesting "POST /init/" on servlet "" but only have:""")
      status shouldBe SC_NOT_FOUND
    }
  }

  "post /update/:store/:uuid" should "return a feedback message" in {
    (app.update(_: String, _: UUID)) expects("pdbs", uuid) once() returning
      Success(BagSubmitted("12 files submitted", Seq.empty))
    post(s"/update/pdbs/$uuid") {
      body shouldBe "12 files submitted"
      status shouldBe SC_OK
    }
  }

  it should "return NOT FOUND if uuid is missing" in {
    post("/update/pdbs/") {
      body should startWith("""Requesting "POST /update/pdbs/" on servlet "" but only have:""")
      status shouldBe SC_NOT_FOUND
    }
  }

  it should "return BAD REQUEST with an invalid uuid" in {
    post("/update/pdbs/rabarbera") {
      body shouldBe "String 'rabarbera' is not a UUID"
      status shouldBe SC_BAD_REQUEST
    }
  }

  it should "return NOT FOUND if something is not found for the first file, bag or store" in {
    (app.update(_: String, _: UUID)) expects("pdbs", uuid) once() returning
      Failure(createHttpException(SC_NOT_FOUND))
    post(s"/update/pdbs/$uuid") {
      body shouldBe "getContent(url)"
      status shouldBe SC_NOT_FOUND
    }
  }

  it should "return NOT FOUND if something is not found for the n-th file, bag or store" in {
    // TODO check if exceptions from getContent indeed bubble up: refactor RichUrl into heavy cake trait
    (app.update(_: String, _: UUID)) expects("pdbs", uuid) once() returning
      Failure(MixedResultsException(Seq.empty, createHttpException(SC_NOT_FOUND)))
    post(s"/update/pdbs/$uuid") {
      body shouldBe "Log files should show which actions succeeded. Finally failed with: getContent(url)"
      status shouldBe SC_NOT_FOUND
    }
  }

  it should "return INTERNAL SERVER ERROR in case of unexpected errors" in {
    (app.update(_: String, _: UUID)) expects("pdbs", uuid) once() returning
      Failure(new Exception())
    post(s"/update/pdbs/$uuid") {
      body shouldBe ""
      status shouldBe SC_INTERNAL_SERVER_ERROR
    }
  }

  "delete /?q=XXX" should "return a feedback message" in {
    (app.delete(_: String)) expects "*:*" once() returning
      Success("xxx")
    delete("/?q=*:*") {
      body shouldBe "xxx"
      status shouldBe SC_OK
    }
  }

  it should "return BAD REQUEST with an invalid query" in {
    (app.delete(_: String)) expects ":" once() returning
      Failure(SolrBadRequestException("Cannot parse ':'", new Exception))
    delete("/?q=:") {
      body should startWith("Cannot parse ':'")
      status shouldBe SC_BAD_REQUEST
    }
  }

  it should "complain about the required query" in {
    delete("/") {
      body shouldBe "delete requires param 'q', got no params at all"
      status shouldBe SC_BAD_REQUEST
    }
  }

  it should "show params received" in {
    delete("/?skip") {
      body shouldBe "delete requires param 'q', got params [skip -> ]"
      status shouldBe SC_BAD_REQUEST
    }
  }

  "delete /:store[/:uuid]" should "return a feedback message with just a store" in {
    (app.delete(_: String)) expects "easy_dataset_store_id:pdbs" once() returning
      Success("xxx")
    delete("/pdbs") {
      body shouldBe "xxx"
      status shouldBe SC_OK
    }
  }

  it should "return a feedback message with store and UUID" in {
    (app.delete(_: String)) expects s"easy_dataset_id:$uuid" once() returning
      Success("xxx")
    delete("/pdbs/" + uuid) {
      body shouldBe "xxx"
      status shouldBe SC_OK
    }
  }

  it should "return BAD REQUEST with an invalid UUID" in {
    delete("/pdbs/rabarbera") {
      body shouldBe "String 'rabarbera' is not a UUID"
      status shouldBe SC_BAD_REQUEST
    }
  }

  private def createHttpException(code: Int) = {
    val headers = Map("Status" -> IndexedSeq(""))
    val r = new HttpResponse[String]("", code, headers)
    // URL could be a vocabulary for the DDM class or addressing the bag store service
    HttpStatusException("getContent(url)", r)
  }
}
