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

import java.io.File
import java.net.{ HttpURLConnection, URI, URL, URLEncoder }
import java.nio.file.{ Files, Path, Paths }
import java.util.UUID

import nl.knaw.dans.easy.solr4files.components.Vault
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.io.FileUtils
import org.scalamock.handlers.CallHandler3
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterEach, FlatSpec, Inside, Matchers }

import scala.util.Try

trait TestSupportFixture extends FlatSpec with Matchers with Inside with BeforeAndAfterEach with MockFactory {

  val uuid: UUID = UUID.randomUUID()
  lazy val testDir: Path = {
    val path = Paths.get(s"target/test/${ getClass.getSimpleName }").toAbsolutePath
    FileUtils.deleteQuietly(path.toFile)
    Files.createDirectories(path)
    path
  }

  abstract class TestApp() extends EasySolr4filesIndexApp {

    override lazy val configuration: Configuration = new Configuration("1.0.0", new PropertiesConfiguration() {
      addProperty("auth-info.url", "http://hostThatDoesNotExist:20170/")
      addProperty("auth-info.connection-timeout-ms", 2000)
      addProperty("auth-info.read-timeout-ms", 2000)
      addProperty("list-bags.connection-timeout-ms", 10000)
      addProperty("list-bags.read-timeout-ms", 10000)
      addProperty("fetch-metadata.connection-timeout-ms", 10000)
      addProperty("fetch-metadata.read-timeout-ms", 10000)
    })

    override val maxFileSizeToExtractContentFrom: Double = 64 * 1024 * 1024
    override val mimeTypesToExtractContentFrom: Seq[String] = Seq("text/plain", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/xml")
    override val vaultBaseUri: URI = new URI(s"file:///${ testDir.resolve("vault").toAbsolutePath }/")
    override val authentication: Authentication = new Authentication {
      override val ldapUsersEntry: String = "ou=users,ou=easy,dc=dans,dc=knaw,dc=nl"
      override val ldapProviderUrl: String = "ldap://ldapHostDoesNotExist:389"
    }
    override val authorisation: Authorisation = new Authorisation {
      override val baseUri: URI = new URI("http://authInfoHostDoesNotExist:20170/")
      override val connectionTimeOutMs: Int = 2000
      override val readTimeOutMs: Int = 2000
    }
    override val http: HttpWorker = mock[HttpWorker]

    def expectsHttpAsString(result: Try[String]): CallHandler3[URI, Int, Int, Try[String]] = {
      (http.getHttpAsString(_: URI, _: Int, _: Int)) expects(*, *, *) returning result
    }
  }

  val uuidCentaur: UUID = UUID.fromString("9da0541a-d2c8-432e-8129-979a9830b427")
  val uuidAnonymized: UUID = UUID.fromString("1afcc4e9-2130-46cc-8faf-2663e199b218")

  val mockedVault: Vault = new Vault {
    // vault/stores is sometimes a folder, sometimes a dir
    private val vaultBaseDir = URLEncoder.encode(testDir.resolve("vault").toAbsolutePath.toString, "UTF8")
    override val vaultBaseUri = new URI(s"file:///$vaultBaseDir/")
    override val listBagsConnTimeoutMs: Int = 2000
    override val listBagsReadTimeoutMs: Int = 2000
  }

  def clearVault(): Unit = {
    FileUtils.deleteDirectory(testDir.resolve("vault").toFile)
  }

  def initVault(): Unit = {
    clearVault()
    FileUtils.copyDirectory(new File("src/test/resources/vault"), testDir.resolve("vault").toFile)
  }

  /** assume(canConnectToEasySchemas) allows to build when offline */
  def canConnectToEasySchemas: Boolean = Try {
    // allows to build when offline
    new URL("https://easy.dans.knaw.nl/schemas").openConnection match {
      case connection: HttpURLConnection =>
        connection.setConnectTimeout(1000)
        connection.setReadTimeout(1000)
        connection.connect()
        connection.disconnect()
        true
      case _ => throw new Exception
    }
  }.isSuccess
}
