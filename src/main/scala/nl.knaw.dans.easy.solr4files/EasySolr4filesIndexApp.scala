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

import nl.knaw.dans.easy.solr4files.components.User
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.solr.client.solrj.SolrQuery
import org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest

import scala.util.{ Success, Try }

class EasySolr4filesIndexApp(wiring: ApplicationWiring) extends AutoCloseable
  with DebugEnhancedLogging {

  def initAllStores(): Try[String] = wiring.initAllStores()

  def initSingleStore(storeName: String): Try[String] = wiring.initSingleStore(storeName).map(_.toString)

  def update(storeName: String, bagId: UUID): Try[String] = wiring.update(storeName, bagId).map(_.toString)

  def delete(query: String): Try[String] = wiring.delete(query)

  def search(query: SolrQuery, skipFetched: Seq[String]): Try[String] = wiring.search(query, skipFetched)

  def authenticate(authRequest: BasicAuthRequest): Try[Option[User]] = wiring.authentication.authenticate(authRequest)

  def init(): Try[Unit] = {
    // Do any initialization of the application here. Typical examples are opening
    // databases or connecting to other services.
    Success(())
  }

  override def close(): Unit = {

  }
}