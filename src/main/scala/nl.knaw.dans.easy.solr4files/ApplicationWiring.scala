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

import java.net.{ URI, URL }
import java.util.UUID
import javax.naming.Context
import javax.naming.ldap.{ InitialLdapContext, LdapContext }

import nl.knaw.dans.easy.solr4files.components._
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration

import scala.util.{ Failure, Try }
import scala.xml.Elem

/**
 * Initializes and wires together the components of this application.
 *
 * @param configuration the application configuration
 */
class ApplicationWiring(configuration: Configuration)
  extends DebugEnhancedLogging
    with Vault
    with Solr
    with LdapAuthenticationComponent {

  private val properties: PropertiesConfiguration = configuration.properties
  override val authentication: Authentication = new LdapAuthentication {}
  override val ldapUsersEntry: String = properties.getString("ldap.users-entry")
  override val ldapProviderUrl: String = properties.getString("ldap.provider.url")
  override val ldapContext: Try[LdapContext] = Try { // TODO fail at service startup
    val env = new java.util.Hashtable[String, String] {
      put(Context.SECURITY_AUTHENTICATION, "simple")
      put(Context.SECURITY_PRINCIPAL, properties.getString("ldap.securityPrincipal"))
      put(Context.SECURITY_CREDENTIALS, properties.getString("ldap.securityCredentials"))
      put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
      put(Context.PROVIDER_URL, ldapProviderUrl)
    }
    new InitialLdapContext(env, null)
  }

  // don't need resolve for solr, URL gives more early errors TODO perhaps not yet at service startup once implemented
  override val solrUrl: URL = new URL(properties.getString("solr.url", ""))
  override val vaultBaseUri: URI = new URI(properties.getString("vault.url", ""))

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
  private def updateFiles(bag: Bag, ddm: DDM, filesXML: Elem): Try[BagSubmitted] = {
    (filesXML \ "file")
      .map(FileItem(bag, ddm, _))
      .filter(_.shouldIndex)
      .toStream
      .map(f => createDoc(f, getSize(f.bag.storeName, f.bag.bagId, f.path)))
      .takeUntilFailure
      .doIfFailure { case MixedResultsException(results: Seq[_], _) => results.foreach(fb => logger.info(fb.toString)) }
      .map(results => BagSubmitted(bag.bagId.toString, results))
  }
}
