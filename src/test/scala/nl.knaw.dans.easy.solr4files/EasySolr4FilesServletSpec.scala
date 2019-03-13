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
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

class EasySolr4FilesServletSpec extends TestSupportFixture
  with EmbeddedJettyContainer
  with ScalatraSuite
  with MockFactory {

  private val app = mock[TestApp]
  addServlet(new EasySolr4FilesServlet(app), "/")
  private val testVersion = "1.0.0"

  "get /" should "return a 200 stating the service is running and the version number" in {
    get("/") {
      body shouldBe s"EASY File Index is running v$testVersion."
      status shouldBe SC_OK
    }
  }
}
