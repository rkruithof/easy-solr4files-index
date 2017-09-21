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

import java.net.{ HttpURLConnection, URI, URL, URLEncoder }
import java.nio.file.{ Files, Path, Paths }

import nl.knaw.dans.easy.solr4files.components.Vault
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.FileUtils
import org.scalatest.{ BeforeAndAfterEach, FlatSpec, Inside, Matchers }

import scala.util.Try

trait TestSupportFixture extends FlatSpec with Matchers with Inside with BeforeAndAfterEach {

  lazy val testDir: Path = {
    val path = Paths.get(s"target/test/${ getClass.getSimpleName }").toAbsolutePath
    FileUtils.deleteQuietly(path.toFile)
    Files.createDirectories(path)
    path
  }

  def mockVault(testDir: String) = new Vault with DebugEnhancedLogging {
    // TODO use testDir https://github.com/DANS-KNAW/easy-split-multi-deposit/blob/69957d9f2244092f88d49eb903c6cb0bc781ee32/src/test/scala/nl.knaw.dans.easy.multideposit/BlackBoxSpec.scala#L62-L63
    private val absolutePath = URLEncoder.encode(Paths.get(s"src/test/resources/$testDir").toAbsolutePath.toString, "UTF8")
    override val vaultBaseUri = new URI(s"file:///$absolutePath/")
  }

  /** assume(canConnectToEasySchemas) allows to build when offline */
  def canConnectToEasySchemas: Boolean = Try {
    // allows to build when offline
    new URL("http://easy.dans.knaw.nl/schemas").openConnection match {
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
