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

import scalaj.http.BaseHttp

trait HttpContext {

  val applicationVersion: String

  lazy val userAgent: String = {
    val agent = s"easy-solr4files-index/$applicationVersion"
    
    // Solr's library does a call to easy-bag-store in ContentStreamBase.URLStream,
    // here we cannot set the user-agent
    // therefore we need to set it here, such that it can be used in
    // sun.net.www.protocol.http.HttpURLConnection
    System.setProperty("http.agent", agent)

    agent
  }

  implicit object Http extends BaseHttp(userAgent = userAgent)
}
