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

class FileItemSpec extends TestSupportFixture {

  private val centaurBag = Bag("pdbs", uuidCentaur, mockedVault)

  "solrLiteral" should "return proper values" in {
    initVault()
    val xml = <file filepath="data/reisverslag/centaur.mpg">
      <dcterms:type>http://schema.org/VideoObject</dcterms:type>
      <dcterms:format>video/mpeg</dcterms:format>
      <dcterms:title>video about the centaur meteorite</dcterms:title>
      <dcterms:accessRights>RESTRICTED_GROUP</dcterms:accessRights>
      <dcterms:relation xml:lang="en">data/reisverslag/centaur.srt</dcterms:relation>
      <dcterms:relation xml:lang="nl">data/reisverslag/centaur-nederlands.srt</dcterms:relation>
    </file>

    val fi = FileItem(centaurBag, ddm("OPEN_ACCESS"), xml)
    fi.mimeType shouldBe "video/mpeg"
    fi.path shouldBe "data/reisverslag/centaur.mpg"

    val solrLiterals = fi.solrLiterals.toMap
    solrLiterals("file_path") shouldBe fi.path
    solrLiterals("file_accessible_to") shouldBe "RESTRICTED_GROUP"
    solrLiterals("file_mime_type") shouldBe "video/mpeg"
    solrLiterals("file_checksum") shouldBe "1dd40013ce63dfa98dfe19f5b4bbf811ee2240f7"
  }

  it should "use the dataset rights as default" in {
    val solrLiterals = FileItem(centaurBag, ddm("OPEN_ACCESS"), <file filepath="p"/>)
      .solrLiterals.toMap
    solrLiterals("file_accessible_to") shouldBe "ANONYMOUS"
  }

  it should "also use the dataset rights as default" in {
    val item = FileItem(centaurBag, ddm("OPEN_ACCESS_FOR_REGISTERED_USERS"), <file filepath="p"/>)
    val solrLiterals = item.solrLiterals.toMap
    solrLiterals("file_accessible_to") shouldBe "KNOWN"
  }

  it should "not have read the lazy files in case of accessible to none" ignore { // TODO
    val item = FileItem(centaurBag, ddm("NO_ACCESS"), <file filepath="p"/>)

    // The bag.sha's and ddm.vocabularies are private
    // so we need side effects, not something like https://stackoverflow.com/questions/1651927/how-to-unit-test-for-laziness
    // we could mock the vault.fileURL for this test
    // throwing an error when called for the sha's
    // remains checking for the vocabularies in DDM
  }

  private def ddm(datasetAccessRights: String) = new DDM(
    <ddm:DDM xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/md/ddm/ https://easy.dans.knaw.nl/schemas/md/ddm/ddm.xsd"
             xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/">
      <ddm:profile>
        <ddm:accessRights>{datasetAccessRights}</ddm:accessRights>
        <ddm:creatorDetails></ddm:creatorDetails>
      </ddm:profile>
    </ddm:DDM>
  )
}
