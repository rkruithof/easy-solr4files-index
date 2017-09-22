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

import java.net.{ URL, URLEncoder }

import nl.knaw.dans.easy.solr4files.{ FileToShaMap, SolrLiterals, _ }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.util.Try
import scala.util.matching.Regex
import scala.xml.Elem

case class Bag(storeName: String,
               bagId: String,
               private val vault: Vault
              ) extends DebugEnhancedLogging {

  private def getDepositor: String = {
    val key = "EASY-User-Account"
    for {
      url <- vault.fileURL(storeName, bagId, "bag-info.txt")
      lines <- url.readLines
    } yield lines
      .filter(_.trim.startsWith(key))
      .map(_.replace(key, "").replace(":", "").trim)
      .mkString
  }.getOrElse("")

  def fileUrl(path: String): Try[URL] = {
    vault.fileURL(storeName, bagId,  URLEncoder.encode(path,"UTF8"))
  }


  // splits a string on the first sequence of white space after the sha
  // the rest is a path that might contain white space
  private lazy val regex: Regex = """(\w+)\s+(.*)""".r()

  private lazy val fileShas: FileToShaMap = {
    // gov.loc.repository.bagit.reader.ManifestReader reads files, we need URL or stream
    for {
      url <- vault.fileURL(storeName, bagId, "manifest-sha1.txt")
      lines <- url.readLines
    } yield lines.map { line: String =>
      val regex(sha, path) = line.trim
      (path, sha)
    }.toMap
  }.getOrElse(Map.empty)

  def sha(path: String): String = {
    fileShas.getOrElse(path, "")
  }

  def loadDDM: Try[Elem] = vault
    .fileURL(storeName, bagId, "metadata/dataset.xml")
    .flatMap(_.loadXml)

  def loadFilesXML: Try[Elem] = vault
    .fileURL(storeName, bagId, "metadata/files.xml")
    .flatMap(_.loadXml)

  val solrLiterals: SolrLiterals = Seq(
    ("dataset_store_id", storeName),
    ("dataset_depositor_id", getDepositor),
    ("dataset_id", bagId)
  )
}
