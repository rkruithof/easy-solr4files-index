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
package nl.knaw.dans.easy.solr4files.components

import java.net.URI
import java.nio.file.Path
import java.util.UUID

import nl.knaw.dans.easy.solr4files._
import nl.knaw.dans.lib.encode.PathEncoding
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.{ DefaultFormats, _ }

import scala.util.Try

trait AuthorisationComponent extends DebugEnhancedLogging {
  this: HttpWorkerComponent =>

  val authorisation: Authorisation

  trait Authorisation {
    val baseUri: URI
    val connectionTimeOutMs: Int
    val readTimeOutMs: Int

    private implicit val jsonFormats: Formats = DefaultFormats

    def getAuthInfoItem(bagId: UUID, path: Path): Try[AuthorisationItem] = {
      val uri = baseUri.resolve(s"$bagId/${ path.escapePath }")
      logger.info(s"loading authorisation info from $uri")

      for {
        jsonString <- http.getHttpAsString(uri, connectionTimeOutMs, readTimeOutMs)
        jsonOneLiner = jsonString.toOneLiner
        _ = logger.debug(s"auth-info: ${ jsonOneLiner }")
        authInfoItem <- AuthorisationItem.fromJson(jsonOneLiner)
      } yield authInfoItem
    }
  }
}
