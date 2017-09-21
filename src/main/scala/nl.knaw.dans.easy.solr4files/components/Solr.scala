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

import java.net.URL

import nl.knaw.dans.easy.solr4files._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.solr.client.solrj.SolrRequest.METHOD
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrQuery }
import org.apache.solr.common.util.{ ContentStreamBase, NamedList }

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

trait Solr extends DebugEnhancedLogging {
  val solrUrl: URL
  lazy val solrClient: SolrClient = new HttpSolrClient.Builder(solrUrl.toString).build()

  def createDoc(item: FileItem, size: Long): Try[FileFeedback] = {
    item.bag.fileUrl(item.path).flatMap { fileUrl =>
      val solrDocId = s"${ item.bag.bagId }/${ item.path }"
      val request = new ContentStreamUpdateRequest("/update/extract") {
        setWaitSearcher(false)
        setMethod(METHOD.POST)
        addContentStream(new ContentStreamBase.URLStream(fileUrl))
        setParam("literal.id", solrDocId)
        setParam("literal.easy_file_size", size.toString)
        for ((key, value) <- item.bag.solrLiterals ++ item.ddm.solrLiterals ++ item.solrLiterals) {
          if (value.trim.nonEmpty)
            setParam(s"literal.easy_$key", value.replaceAll("\\s+", " ").trim)
        }
      }
      submitRequest(solrDocId, request)
    }
  }

  def deleteBag(bagId: String): Try[UpdateResponse] = {
    // no status to check since UpdateResponse is a stub
    Try(solrClient.deleteByQuery(createDeleteQuery(bagId)))
      .recoverWith { case t => Failure(SolrDeleteException(bagId, t)) }
  }

  def commit(): Try[UpdateResponse] = {
    // no status to check since UpdateResponse is a stub
    Try(solrClient.commit())
      .recoverWith { case t => Failure(SolrCommitException(t)) }
  }

  private def submitRequest(solrDocId: String, req: ContentStreamUpdateRequest): Try[FileFeedback] = {
    logger.debug(req.getParams.getParameterNames.toArray()
      .map(key => s"$key = ${ req.getParams.getParams(key.toString).toSeq.mkString(", ") }")
      .mkString("\n\t")
    )
    executeUpdate(req)
      .map(_ => FileSubmittedWithContent(solrDocId))
      .recoverWith { case t =>
        logger.warn(s"Submission with content of $solrDocId failed with ${ t.getMessage }", t)
        req.getContentStreams.clear() // retry with just metadata
        executeUpdate(req)
          .map(_ => FileSubmittedWithJustMetadata(solrDocId))
          .recoverWith {
            case t2: SolrStatusException => Failure(t2)
            case t2 => Failure(SolrUpdateException(solrDocId, t2))
          }
      }
  }

  private def executeUpdate(req: ContentStreamUpdateRequest): Try[Unit] = {
    Try(solrClient.request(req))
      .flatMap(checkSolrStatus)
  }

  private def createDeleteQuery(bagId: String) = {
    new SolrQuery {
      set("q", s"id:$bagId/*")
    }.getQuery
  }

  private def checkSolrStatus(namedList: NamedList[AnyRef]) = {
    Option(namedList.get("status"))
      .withFilter("0" !=)
      .map(_ => Failure(SolrStatusException(namedList)))
      .getOrElse(Success(()))
  }
}
