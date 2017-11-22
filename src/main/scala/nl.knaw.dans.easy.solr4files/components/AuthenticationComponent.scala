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

import nl.knaw.dans.easy.solr4files.AuthorisationTypeNotSupportedException
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest

import scala.util.{ Failure, Success, Try }

trait AuthenticationComponent extends DebugEnhancedLogging {

  val authentication: Authentication

  trait Authentication {
    def authenticate(authRequest: BasicAuthRequest): Try[Option[User]] = {

      (authRequest.providesAuth, authRequest.isBasicAuth) match {
        case (true, true) => getUser(authRequest.username, authRequest.password).map(Some(_))
        case (true, _) => Failure(AuthorisationTypeNotSupportedException(new Exception("Supporting only basic authentication")))
        case (_, _) => Success(None)
      }
    }

    def getUser(name: String, password: String): Try[User]
  }
}
