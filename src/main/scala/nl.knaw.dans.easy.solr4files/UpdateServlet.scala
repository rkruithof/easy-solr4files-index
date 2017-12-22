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

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.http.HttpStatus._
import org.scalatra._

import scala.util.Try
import scalaj.http.HttpResponse

class UpdateServlet(app: EasySolr4filesIndexApp) extends ScalatraServlet with DebugEnhancedLogging {
  logger.info("File index Servlet running...")

  get("/") {
    contentType = "text/plain"
    Ok("EASY File Index is running.")
  }

  private def respond(result: Try[String]): ActionResult = {
    val msgPrefix = "Log files should show which actions succeeded. Finally failed with: "
    result.map(Ok(_))
      .doIfFailure { case e => logger.error(e.getMessage, e) }
      .getOrRecover {
        case SolrBadRequestException(message, _) => BadRequest(message) // delete or search only
        case HttpStatusException(message, r: HttpResponse[String]) if r.code == SC_NOT_FOUND => NotFound(message)
        case HttpStatusException(message, r: HttpResponse[String]) if r.code == SC_SERVICE_UNAVAILABLE => ServiceUnavailable(message)
        case HttpStatusException(message, r: HttpResponse[String]) if r.code == SC_REQUEST_TIMEOUT => RequestTimeout(message)
        case MixedResultsException(_, HttpStatusException(message, r: HttpResponse[String])) if r.code == SC_NOT_FOUND => NotFound(msgPrefix + message)
        case MixedResultsException(_, HttpStatusException(message, r: HttpResponse[String])) if r.code == SC_SERVICE_UNAVAILABLE => ServiceUnavailable(msgPrefix + message)
        case MixedResultsException(_, HttpStatusException(message, r: HttpResponse[String])) if r.code == SC_REQUEST_TIMEOUT => RequestTimeout(msgPrefix + message)
        case t =>
          logger.error(s"not expected exception", t)
          InternalServerError(t.getMessage) // for an internal servlet we can and should expose the cause
      }
  }

  private def getUUID = {
    Try { UUID.fromString(params("uuid")) }
  }

  private def badUuid(e: Throwable) = {
    BadRequest(e.getMessage)
  }

  post("/update/:store/:uuid") {
    val result = getUUID
      .map(uuid => respond(app.update(params("store"), uuid)))
      .getOrRecover(badUuid)
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }

  post("/init") {
    val result = respond(app.initAllStores())
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }

  post("/init/:store") {
    val result = respond(app.initSingleStore(params("store")))
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }

  delete("/:store/:uuid") {
    val result = getUUID
      .map(uuid => respond(app.delete(s"easy_dataset_id:$uuid")))
      .getOrRecover(badUuid)
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }

  delete("/:store") {
    val result = respond(app.delete(s"easy_dataset_store_id:${ params("store") }"))
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }

  delete("/") {
    val result = params.get("q")
      .map(q => respond(app.delete(q)))
      .getOrElse(BadRequest("delete requires param 'q', got " + params.asString))
    logger.info(s"update returned ${ response.status.line } for $params")
    result
  }
}
