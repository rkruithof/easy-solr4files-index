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

import nl.knaw.dans.easy.solr4files.{ HttpStatusException, OutputStreamProvider }
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.http.HttpStatus.OK_200

import scala.util.{ Failure, Success, Try }
import scalaj.http.{ Http, HttpResponse }

trait HttpWorkerComponent {

  val http: HttpWorker

  trait HttpWorker {
    def copyHttpStream(uri: URI): OutputStreamProvider => Try[Unit] = { outputStreamProducer =>
      val response = Http(uri.toString).method("GET").exec {
        case (OK_200, _, is) => IOUtils.copyLarge(is, outputStreamProducer())
        case _ => // do nothing
      }
      if (response.code == OK_200) Success(())
      else failed(uri, response)
    }

    def getHttpAsString(uri: URI, connTimeoutMs: Int, readTimeoutMs: Int): Try[String] = {
      val response = Http(uri.toString)
        .method("GET")
        .timeout(connTimeoutMs, readTimeoutMs)
        .asString

      if (response.isSuccess) Success(response.body)
      else failed(uri, response)
    }

    private def failed(uri: URI, response: HttpResponse[_]) = {
      Failure(HttpStatusException(s"Could not download $uri", HttpResponse(response.statusLine, response.code, response.headers)))
    }
  }
}
