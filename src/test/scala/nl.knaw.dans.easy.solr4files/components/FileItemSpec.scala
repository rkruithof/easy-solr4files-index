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

import nl.knaw.dans.easy.solr4files.TestSupportFixture
import nl.knaw.dans.easy.solr4files.components.RightsFor.RESTRICTED_GROUP
import org.joda.time.DateTime
import scalaj.http.{ BaseHttp, Http }

class FileItemSpec extends TestSupportFixture {

  private implicit val http: BaseHttp = Http

  class MockedBag extends Bag("pdbs", uuid, mock[Vault])
  private val mockedBag = mock[MockedBag]

  private def bagExpects(filePath: String, fileSha: String, fileSize: Int) = {
    (mockedBag.sha(_: String)) expects filePath once() returning fileSha
    (mockedBag.fileSize(_: String)) expects filePath once() returning fileSize
  }

  "solrLiterals" should "return proper values" in {
    val filePath = "data/reisverslag/centaur.mpg"
    val fileSha = "1dd40013ce63dfa98dfe19f5b4bbf811ee2240f7"
    val fileSize = 123
    bagExpects(filePath, fileSha, fileSize)

    val xml = <file filepath={filePath}>
      <dcterms:type>http://schema.org/VideoObject</dcterms:type>
      <dcterms:format>video/mpeg</dcterms:format>
      <dcterms:title>video about the centaur meteorite</dcterms:title>
      <dcterms:relation xml:lang="en">data/reisverslag/centaur.srt</dcterms:relation>
      <dcterms:relation xml:lang="nl">data/reisverslag/centaur-nederlands.srt</dcterms:relation>
    </file>
    val authInfoItem = AuthorisationItem(
      itemId = s"$uuid/$filePath",
      owner = "someone",
      dateAvailable = DateTime.now,
      accessibleTo = RESTRICTED_GROUP,
      visibleTo = RESTRICTED_GROUP,
      licenseKey = "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSLicence.pdf=DANS_Licence_UK.pdf",
      licenseTitle = "DANS_Licence_UK.pdf"
    )
    val solrLiterals = FileItem(mockedBag, (xml \ "title").text, (xml \ "format").text, authInfoItem).solrLiterals.toMap
    solrLiterals("file_path") shouldBe filePath
    solrLiterals("file_size") shouldBe s"$fileSize"
    solrLiterals("file_title") shouldBe "video about the centaur meteorite"
    solrLiterals("file_accessible_to") shouldBe "RESTRICTED_GROUP"
    solrLiterals("file_mime_type") shouldBe "video/mpeg"
    solrLiterals("file_checksum") shouldBe fileSha
    solrLiterals("dataset_id") shouldBe uuid.toString // from auth-info
    solrLiterals("dataset_depositor_id") shouldBe "someone" // from auth-info
    solrLiterals("dataset_store_id") shouldBe "pdbs" // from the bag
  }
}
