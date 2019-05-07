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

import nl.knaw.dans.easy.solr4files.SolrLiterals
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

case class FileItem(bag: Bag, title: String, mimeType: String, authInfoItem: AuthorisationItem) extends DebugEnhancedLogging {

  //strip the UUID from the itemId including the first slash
  val path: String = authInfoItem.itemId.replaceAll("^[^/]+/", "")

  lazy val size: Long = bag.fileSize(path)

  // lazy postpones loading Bag.sha's
  lazy val solrLiterals: SolrLiterals = Seq(
    ("file_path", path),
    ("file_title", title),
    ("file_checksum", bag.sha(path)),
    ("file_mime_type", mimeType),
    ("file_size", size.toString),
    ("file_accessible_to", authInfoItem.accessibleTo.toString),
    ("dataset_depositor_id", authInfoItem.owner),
    ("dataset_id", bag.bagId.toString),
    ("dataset_store_id", bag.storeName)
  )
}
