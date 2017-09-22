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
import org.apache.http.HttpStatus
import org.apache.solr.client.solrj.SolrRequest.METHOD
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.UpdateResponse
import org.apache.solr.client.solrj.{ SolrClient, SolrQuery }
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.util.{ ContentStreamBase, NamedList }

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

trait Solr extends DebugEnhancedLogging {
  val solrUrl: URL
  lazy val solrClient: SolrClient = new HttpSolrClient.Builder(solrUrl.toString).build()

  def createDoc(item: FileItem, size: Long): Try[FileFeedback] = {
    item.bag.fileUrl(item.path).flatMap { fileUrl =>
      val solrDocId = s"${ item.bag.bagId }/${ item.path }"
      val solrFields = (item.bag.solrLiterals ++ item.ddm.solrLiterals ++ item.solrLiterals :+ "file_size" -> size.toString)
        .map { case (k, v) => (k, v.replaceAll("\\s+", " ").trim) }
        .filter { case (_, v) => v.nonEmpty }
      logger.debug(solrFields
        .map { case (key, value) => s"$key = $value" }
        .mkString(s"$solrFields\n\t", "\n\t", "")
      )
      submitWithContent(fileUrl, solrDocId, solrFields)
        .map(_ => FileSubmittedWithContent(solrDocId))
        .recoverWith { case t =>
          logger.warn(s"Submission with content of $solrDocId failed with ${ t.getMessage }", t)
          resubmitMetadata(solrDocId, solrFields).map(_ => FileSubmittedWithJustMetadata(solrDocId))
        }
    }
  }


  def deleteDocuments(query: String): Try[UpdateResponse] = {
    // no status to check since UpdateResponse is a stub
    Try(solrClient.deleteByQuery(new SolrQuery {set("q", query)}.getQuery))
      .recoverWith { case t => Failure(SolrDeleteException(query, t)) }
  }

  def commit(): Try[UpdateResponse] = {
    // no status to check since UpdateResponse is a stub
    Try(solrClient.commit())
      .recoverWith { case t => Failure(SolrCommitException(t)) }
  }

  private def submitWithContent(fileUrl: URL, solrDocId: String, solrFields: SolrLiterals) = {
    Try(solrClient.request(new ContentStreamUpdateRequest("/update/extract") {
      setWaitSearcher(false)
      setMethod(METHOD.POST)
      addContentStream(new ContentStreamBase.URLStream(fileUrl))
      for ((k, v) <- solrFields) {
        setParam(s"literal.easy_$k", v)
      }
      setParam("literal.id", solrDocId)
    })).flatMap(checkSolrStatus)
  }

  private def resubmitMetadata(solrDocId: String, solrFields: SolrLiterals) = {
    Try(solrClient.add(new SolrInputDocument() {
      solrFields
        .withFilter(_._1 == "easy_dataset_store_id")
        .foreach { case (k, v) =>
          addField(s"literal.easy_$k", v)
        }
      addField("id", solrDocId)
    })).map(_.getStatus match {
      case 0 => // no response header
      case HttpStatus.SC_OK =>
      case status => throw new Exception(s"Resubmit $solrDocId returned status $status")
    })
  }.recoverWith {
    case t2: SolrStatusException => Failure(t2)
    case t2 => Failure(SolrUpdateException(solrDocId, t2))
  }

  private def checkSolrStatus(namedList: NamedList[AnyRef]) = {
    Option(namedList.get("status"))
      .withFilter("0" !=)
      .map(_ => Failure(SolrStatusException(namedList)))
      .getOrElse(Success(()))
  }
}
