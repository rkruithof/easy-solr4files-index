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
import org.apache.http.HttpStatus._
import org.apache.solr.client.solrj.SolrRequest.METHOD
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest
import org.apache.solr.client.solrj.response.{ SolrResponseBase, UpdateResponse }
import org.apache.solr.client.solrj.{ SolrClient, SolrQuery }
import org.apache.solr.common.util.{ ContentStreamBase, NamedList }
import org.apache.solr.common.{ SolrDocumentList, SolrInputDocument }
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.{ JField, JValue }

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

trait Solr extends DebugEnhancedLogging {
  val solrUrl: URL
  lazy val solrClient: SolrClient = new HttpSolrClient.Builder(solrUrl.toString).build()

  def createDoc(item: FileItem): Try[FileFeedback] = {
    item.bag.fileUrl(item.path).flatMap { fileUrl =>
      val solrDocId = s"${ item.bag.bagId }/${ item.path }"
      val solrFields = (item.bag.solrLiterals ++ item.ddm.solrLiterals ++ item.solrLiterals :+ "file_size" -> item.size.toString)
        .filter { case (_, v) => v.nonEmpty }
      if (logger.underlying.isDebugEnabled) logger.debug(solrFields
        .map { case (key, value) => s"$key = $value" }
        .mkString("\n\t")
      )
      if (!item.shouldSubmitWithContent) {
        logger.info(s"Submission without content of [$solrDocId]")
        submitWithOnlyMetadata(solrDocId, solrFields).map(_ => FileSubmittedWithOnlyMetadata(solrDocId))
      }
      else {
        logger.info(s"Submission with content of [$solrDocId]")
        submitWithContent(fileUrl, solrDocId, solrFields)
          .map(_ => FileSubmittedWithContent(solrDocId))
          .recoverWith { case t =>
            logger.warn(s"Submission with content of [$solrDocId] failed with ${ t.getMessage }")
            submitWithOnlyMetadata(solrDocId, solrFields).map(_ => FileSubmittedWithOnlyMetadata(solrDocId))
          }
      }
    }
  }

  def deleteDocuments(query: String): Try[UpdateResponse] = {
    val q = new SolrQuery {
      set("q", query)
    }
    Try(solrClient.deleteByQuery(q.getQuery))
      .flatMap(checkResponseStatus)
      .recoverWith {
        case t: HttpSolrClient.RemoteSolrException if isParseException(t) =>
          Failure(SolrBadRequestException(t.getMessage, t))
        case t =>
          Failure(SolrDeleteException(query, t))
      }
  }


  private def toJson(query: SolrQuery, numFound: Long, fileItems: Seq[List[(String, JValue)]]) = {
    val result =
      ("summary" -> (
        ("text" -> query.getQuery) ~
          ("skip" -> query.getStart.toInt) ~
          ("limit" -> query.getRows.toInt) ~
          ("time_allowed" -> query.getTimeAllowed.toInt) ~
          ("found" -> numFound) ~
          ("returned" -> fileItems.size)
        )) ~
        ("fileitems" -> fileItems)
    pretty(render(result))
  }

  /**
   * @param skipFetched fields to skip from fetched sets of fields specified with a wild card
   */
  def search(query: SolrQuery, skipFetched: Seq[String]): Try[String] = {
    (for {
      response <- Try(solrClient.query(query))
      _ <- checkResponseStatus(response)
      results = response.getResults
      fileItems = fileItemsAsJson(results, skipFetched)
    } yield toJson(query, results.getNumFound, fileItems) // .getFacet.Xxx, .getGroupXxx .getSortedXxx .getMoreLikeXxx etc.
      ).recoverWith {
      case t: HttpSolrClient.RemoteSolrException if isParseException(t) =>
        Failure(SolrBadRequestException(t.getMessage, t))
      case t =>
        Failure(SolrSearchException(query.toQueryString, t))
    }
  }

  private def isParseException(t: HttpSolrClient.RemoteSolrException) = {
    t.getRootThrowable.endsWith("ParseException")
  }

  def commit(): Try[UpdateResponse] = {
    Try(solrClient.commit())
      .flatMap(checkResponseStatus)
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

  private def submitWithOnlyMetadata(solrDocId: String, solrFields: SolrLiterals) = {
    Try(solrClient.add(new SolrInputDocument() {
      for ((k, v) <- solrFields) {
        addField(s"easy_$k", v)
      }
      addField("id", solrDocId)
    }))
      .flatMap(checkResponseStatus)
      .recoverWith { case t => Failure(SolrUpdateException(solrDocId, t)) }
  }

  private def checkResponseStatus[T <: SolrResponseBase](response: T): Try[T] = {
    // this method hides the inconsistent design of the solr library from the rest of the code
    Try(response.getStatus) match {
      case Success(0) | Success(SC_OK) => Success(response)
      case Success(_) => Failure(SolrStatusException(response.getResponse))
      case Failure(_: NullPointerException) => Success(response) // no status at all
      case Failure(t) => Failure(t)
    }
  }

  private def checkSolrStatus(namedList: NamedList[AnyRef]) = {
    Option(namedList.get("status"))
      .withFilter("0" !=)
      .map(_ => Failure(SolrStatusException(namedList)))
      .getOrElse(Success(()))
  }

  private def fileItemsAsJson(solrDocumentList: SolrDocumentList, skip: Seq[String]) = {
    (0L until solrDocumentList.size()).map { i =>
      val fieldValueMap = solrDocumentList.get(i.toInt).getFieldValueMap
      fieldValueMap.keySet().asScala // asScala on the map throws NotImplementedException
        .filter(!skip.contains(_))
        .map(key => JField(key.replace("easy_", ""), fieldValueMap.get(key).toString))
        .toList
    }
  }
}