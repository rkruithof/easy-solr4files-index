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

import java.util
import javax.naming.directory.SearchControls.SUBTREE_SCOPE
import javax.naming.directory.{ Attribute, SearchControls, SearchResult }
import javax.naming.ldap.InitialLdapContext
import javax.naming.{ AuthenticationException, Context }

import nl.knaw.dans.easy.solr4files.{ AuthorisationNotAvailableException, AuthorisationTypeNotSupportedException, InvalidUserPasswordException }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest
import resource.managed

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

trait AuthenticationComponent extends DebugEnhancedLogging {

  val authentication: Authentication

  trait Authentication {
    val ldapUsersEntry: String
    val ldapProviderUrl: String

    def authenticate(authRequest: BasicAuthRequest): Try[Option[User]] = {

      (authRequest.providesAuth, authRequest.isBasicAuth) match {
        case (true, true) => getUser(authRequest.username, authRequest.password).map(Some(_))
        case (true, _) => Failure(AuthorisationTypeNotSupportedException(new Exception("Supporting only basic authentication")))
        case (_, _) => Success(None)
      }
    }

    private def getUser(userName: String, password: String): Try[User] = {
      // inner functions reuse the arguments

      logger.info(s"looking for user [$userName]")

      val query = s"(&(objectClass=easyUser)(uid=$userName))"
      val connectionProperties = new util.Hashtable[String, String]() {
        put(Context.PROVIDER_URL, ldapProviderUrl)
        put(Context.SECURITY_AUTHENTICATION, "simple")
        put(Context.SECURITY_PRINCIPAL, s"uid=$userName, $ldapUsersEntry")
        put(Context.SECURITY_CREDENTIALS, password)
        put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
      }

      def search = {
        val searchControls = new SearchControls() {
          setSearchScope(SUBTREE_SCOPE)
        }
        managed(new InitialLdapContext(connectionProperties, null))
          .map(_.search(ldapUsersEntry, query, searchControls))
          .tried
      }.recoverWith {
        case t: AuthenticationException => Failure(InvalidUserPasswordException(userName, new Exception("invalid password", t)))
        case t => Failure(AuthorisationNotAvailableException(t))
      }

      def getFirst(list: List[SearchResult]): Try[SearchResult] = {
        list
          .headOption
          .map(Success(_))
          .getOrElse(Failure(InvalidUserPasswordException(userName, new Exception(s"User [$userName] not found"))))
      }

      def toTuples(a: Attribute): (String, Seq[String]) = {
        (a.getID, a.getAll.asScala.map(_.toString).toSeq)
      }

      def userIsActive(map: Map[String, Seq[String]]): Try[Unit] = {
        val values = map.getOrElse("dansState", Seq.empty)
        logger.info(s"state of $userName: $values")
        if (values.contains("ACTIVE")) Success(())
        else Failure(InvalidUserPasswordException(userName, new Exception(s"User [$userName] found but not active")))
      }

      for {
        searchResults <- search // returns zero or one
        searchResult <- getFirst(searchResults.asScala.toList)
        userAttributes = searchResult.getAttributes.getAll.asScala.map(toTuples).toMap
        _ <- userIsActive(userAttributes)
        roles = userAttributes.getOrElse("easyRoles", Seq.empty)
      } yield User(userName, userAttributes.getOrElse("easyGroups", Seq.empty))
    }
  }
}

