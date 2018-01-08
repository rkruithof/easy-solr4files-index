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

import nl.knaw.dans.easy.solr4files.components._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration

/**
 * Initializes and wires together the components of this application.
 */
trait ApplicationWiring
  extends DebugEnhancedLogging
    with Vault
    with Solr
    with AuthenticationComponent {

  val configuration: Configuration
  private val properties: PropertiesConfiguration = Option(configuration).map(_.properties).getOrElse(new PropertiesConfiguration())
  override val authentication: Authentication = new Authentication {
    override val ldapUsersEntry: String = properties.getString("ldap.users-entry")
    override val ldapProviderUrl: String = properties.getString("ldap.provider.url")
  }

  // don't need resolve for solr, URL gives more early errors TODO perhaps not yet at service startup once implemented
  override val solrUrl: URL = new URL(properties.getString("solr.url", "http://localhost"))
  override val vaultBaseUri: URI = new URI(properties.getString("vault.url", "http://localhost"))
  override val maxFileSizeToExtractContentFrom: Double = properties.getString("max-fileSize-toExtract-content-from", (64*1024*1024).toString).toDouble
}
