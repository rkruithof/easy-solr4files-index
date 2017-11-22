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
import javax.naming.directory.{ SearchControls, SearchResult }
import javax.naming.ldap.{ InitialLdapContext, LdapContext }
import javax.naming.{ AuthenticationException, Context, NamingEnumeration }

import nl.knaw.dans.easy.solr4files.{ AuthorisationNotAvailableException, InvalidUserPasswordException }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

trait LdapAuthenticationComponent extends AuthenticationComponent {
  val ldapContext: Try[LdapContext]
  val ldapUsersEntry: String
  val ldapProviderUrl: String

  trait LdapAuthentication extends Authentication {

    def getUser(userName: String, password: String): Try[User] = {

      logger.info(s"looking for user [$userName]")

      def toUser(searchResult: SearchResult) = {
        def getAttrs(key: String) = {
          Option(searchResult.getAttributes.get(key)).map(
            _.getAll.asScala.toList.map(_.toString)
          ).getOrElse(Seq.empty)
        }

        val roles = getAttrs("easyRoles")
        User(userName,
          isArchivist = roles.contains("ARCHIVIST"),
          isAdmin = roles.contains("ADMIN"),
          groups = getAttrs("easyGroups")
        )
      }

      def validPassword: Try[InitialLdapContext] = Try {
        val env = new util.Hashtable[String, String]() {
          put(Context.PROVIDER_URL, ldapProviderUrl)
          put(Context.SECURITY_AUTHENTICATION, "simple")
          put(Context.SECURITY_PRINCIPAL, s"uid=$userName, $ldapUsersEntry")
          put(Context.SECURITY_CREDENTIALS, password)
          put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        }
        new InitialLdapContext(env, null)
        // TODO can we get attributes from this context?
      }.recoverWith {
        case t: AuthenticationException => Failure(InvalidUserPasswordException(userName, new Exception("invalid password", t)))
        case t => Failure(t)
      }

      def findUser(userAttributes: NamingEnumeration[SearchResult]): Try[User] = {
        userAttributes.asScala.toList.headOption match {
          case Some(sr) => Success(toUser(sr))
          case None => Failure(InvalidUserPasswordException(userName, new Exception("not found")))
        }
      }

      val searchFilter = s"(&(objectClass=easyUser)(uid=$userName))"
      val searchControls = new SearchControls() {
        setSearchScope(SearchControls.SUBTREE_SCOPE)
      }
      (for {
        context <- ldapContext
        _ <- validPassword
        userAttributes <- Try(context.search(ldapUsersEntry, searchFilter, searchControls))
        user <- findUser(userAttributes)
      } yield user).recoverWith {
        case t: InvalidUserPasswordException => Failure(t)
        case t => Failure(AuthorisationNotAvailableException(t))
      }
    }
  }
}
