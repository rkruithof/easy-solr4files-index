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

import java.nio.file.Paths
import java.util.UUID

import nl.knaw.dans.easy.solr4files.components._
import nl.knaw.dans.lib.error.{ CompositeException, _ }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest

import scala.util.{ Failure, Success, Try }
import scala.xml.{ Elem, Node }

trait EasySolr4filesIndexApp extends ApplicationWiring with AutoCloseable
  with DebugEnhancedLogging {

  def initAllStores(): Try[FeedBackMessage] = {
    getStoreNames
      .flatMap(updateStores)
  }

  def initSingleStore(storeName: String): Try[StoreSubmitted] = {
    getBagIds(storeName)
      .flatMap(updateBags(storeName, _))
  }

  def update(storeName: String, bagId: UUID): Try[BagSubmitted] = {
    val bag = Bag(storeName, bagId, this)
    for {
      ddmXML <- bag.loadDDM
      ddm = new DDM(ddmXML)
      filesXML <- bag.loadFilesXML
      _ = logger.info(s"deleted documents of $bagId")
      _ <- deleteDocuments(s"id:${ bag.bagId }*")
      feedbackMessage <- updateFiles(bag, ddm, filesXML)
      _ <- commit()
      _ = logger.info(s"committed $feedbackMessage")
    } yield feedbackMessage
  }.recoverWith {
    case t: SolrStatusException => commitAnyway(t) // just the delete
    case t: SolrUpdateException => commitAnyway(t) // just the delete
    case t: MixedResultsException[_] => commitAnyway(t) // delete and some files
    case t => Failure(t)
  }

  def delete(query: String): Try[FeedBackMessage] = {
    for {
      _ <- deleteDocuments(query)
      _ <- commit()
    } yield s"Deleted documents with query $query"
  }.recoverWith {
    case t: SolrCommitException => Failure(t)
    case t =>
      commit()
      Failure(t)
  }

  private def commitAnyway(t: Throwable): Try[Nothing] = {
    commit()
      .map(_ => Failure(t))
      .getOrRecover(t2 => Failure(new CompositeException(t2, t)))
  }

  /**
   * The number of bags submitted per store are logged as info
   * if and when another store in the vault failed.
   */
  private def updateStores(storeNames: Seq[String]): Try[FeedBackMessage] = {
    storeNames
      .toStream
      .map(initSingleStore)
      .takeUntilFailure
      .doIfFailure { case MixedResultsException(results: Seq[_], _) => results.foreach(fb => logger.info(fb.toString)) }
      .map(storeSubmittedSeq => s"Updated ${ storeNames.size } stores: ${ storeSubmittedSeq.map(_.msg).mkString(", ") }.")
  }

  /**
   * The number of files submitted with or without content per bag are logged as info
   * if and when another bag in the same store failed.
   */
  private def updateBags(storeName: String, bagIds: Seq[UUID]): Try[StoreSubmitted] = {
    bagIds
      .toStream
      .map(update(storeName, _))
      .takeUntilFailure
      .doIfFailure { case MixedResultsException(results: Seq[_], _) => results.foreach(fb => logger.info(fb.toString)) }
      .map(_ => StoreSubmitted(s"${ bagIds.size } bags for $storeName"))
  }

  /**
   * Files submitted without content are logged immediately as warning.
   * Files submitted with content are logged as info
   * if and when another file in the same bag failed.
   */
  def updateFiles(bag: Bag, ddm: DDM, filesXML: Elem): Try[BagSubmitted] = {
    (filesXML \ "file")
      .toStream // prevent execution beyond the first failure
      .flatMap(getFileItem(bag, _).toSeq) // skip the None's and unwrap the Some's
      .map(_.flatMap(createDoc(_, ddm))) // createDoc if getFileItem did not fail
      .takeUntilFailure
      .doIfFailure { case MixedResultsException(results: Seq[_], e: Throwable) =>
        logger.error(e.getMessage)
        logger.info("Indexing has stopped, because one of the files could not be indexed. Only the following files are submitted:")
        results.foreach(fileFeedBack => logger.info(fileFeedBack.toString))
      }
      .map(results => BagSubmitted(bag.bagId.toString, results))
  }

  private def getFileItem(bag: Bag, fileNode: Node): Option[Try[FileItem]] = {
    val title = (fileNode \ "title").text
    val mimeType = (fileNode \ "format").text
    getAccessibleAuthInfo(bag.bagId, fileNode) match {
      case None => Some(Failure(new Exception(s"invalid files.xml for ${ bag.bagId }: filepath attribute is missing in ${ fileNode.toString().toOneLiner }")))
      case Some(Failure(t)) => Some(Failure(t))
      case Some(Success(authInfoItem)) if !authInfoItem.isAccessible =>
        logger.info(s"file ${authInfoItem.itemId} is not accessible, not indexing")
        None
      case Some(Success(authInfoItem)) =>
        logger.info(s"file ${authInfoItem.itemId} is accessible, indexing")
        Some(Success(FileItem(bag, title, mimeType , authInfoItem)))
    }
  }

  private def getAccessibleAuthInfo(bagID: UUID, fileNode: Node): Option[Try[AuthorisationItem]] = {
    fileNode
      .attribute("filepath")
      .map(attribute => authorisation.getAuthInfoItem(bagID, Paths.get(attribute.text)))
  }

  def authenticate(authRequest: BasicAuthRequest): Try[Option[User]] = {
    authentication.authenticate(authRequest)
  }

  def init(): Try[Unit] = {
    // Do any initialization of the application here. Typical examples are opening
    // databases or connecting to other services.
    Success(())
  }

  override def close(): Unit = {

  }
}

object EasySolr4filesIndexApp {

  def apply(conf: Configuration): EasySolr4filesIndexApp = new EasySolr4filesIndexApp {
    override lazy val configuration: Configuration = conf
  }
}
