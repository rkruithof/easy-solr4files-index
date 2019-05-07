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

import nl.knaw.dans.easy.solr4files.{ TestSupportFixture, _ }
import scalaj.http.{ BaseHttp, Http }

class DDMSpec extends TestSupportFixture {

  private implicit val http: BaseHttp = Http

  "solrLiteral" should "return proper values" in {
    initVault()
    assume(canConnectToEasySchemas)
    val xml = mockedVault.fileURL("pdbs", uuidCentaur, "metadata/dataset.xml").flatMap(_.loadXml).getOrElse(<ddm/>)

    val ddm = new DDM(xml)
    val literals: Seq[(String, String)] = ddm.solrLiterals
      .map { case (k, v) => (k, v.replaceAll("\\s+", " ").trim) }
      .filter { case (_, v) => !v.isEmpty }
    val expected = Seq(
      ("dataset_doi", "10.5072/dans-x6g-x2hb"),
      ("dataset_identifier", "URN urn:nbn:nl:ui:13-7vca-i7"),
      ("dataset_identifier", "ARCHIS-ZAAK-IDENTIFICATIE 6663"),
      ("dataset_identifier", "easy-dataset:14"),
      ("dataset_identifier", "ds1"),
      ("dataset_audience", "D30000"),
      ("dataset_date_available", "1992-07-30T00:00:00Z"),
      ("dataset_subject", "Humanities"),
      ("dataset_relation", "dummy"),
      ("dataset_relation", "blabla"),
      ("dataset_coverage_temporal", "random text"),
      ("dataset_creator", "Captain J.T. Kirk United Federation of Planets"),
      ("dataset_subject", "astronomie"),
      ("dataset_subject", "ruimtevaart"),
      ("dataset_subject_abr", "IX"),
      ("dataset_subject", "Infrastructuur, onbepaald"),
      ("dataset_coverage_temporal_abr", "PALEOLB"),
      ("dataset_coverage_temporal", "Paleolithicum laat B: 18000 C14 -8800 vC"),
      ("dataset_title", s"Reis naar Centaur-planeto\u00efde"),
      ("dataset_title", "Trip to Centaur asteroid")
    )
    // in case of problems "should contain theSameElementsAs" gives two very long lists that do not equal
    // the following checks signal a problem in a short way at the end of the exception message
    expected.foreach(literals should contain(_))
    literals.foreach(expected should contain(_))
  }

  it should "have white space in a one-liner" in {
    assume(canConnectToEasySchemas)
    val ddmLiterals = new DDM(<ddm:DDM
        xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/md/ddm/ https://easy.dans.knaw.nl/schemas/md/ddm/ddm.xsd"
        xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:dcx-dai="http://easy.dans.knaw.nl/schemas/dcx/dai/">
      <ddm:profile>
        <dcx-dai:creatorDetails><dcx-dai:author><dcx-dai:titles>Captain</dcx-dai:titles><dcx-dai:initials>J.T.</dcx-dai:initials><dcx-dai:surname>Kirk</dcx-dai:surname><dcx-dai:organization><dcx-dai:name xml:lang="en">United Federation of Planets</dcx-dai:name></dcx-dai:organization></dcx-dai:author></dcx-dai:creatorDetails>
      </ddm:profile>
    </ddm:DDM>
    ).solrLiterals.toMap
    ddmLiterals("dataset_creator") shouldBe "Captain J.T. Kirk United Federation of Planets"
  }

  it should "create coverage_temporal from <dc:coverage xsi:type='dct:Period'>" in {
    assume(canConnectToEasySchemas)
    val ddmLiterals = new DDM(<ddm:DDM
        xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/md/ddm/ https://easy.dans.knaw.nl/schemas/md/ddm/ddm.xsd"
        xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:id-type="http://easy.dans.knaw.nl/schemas/vocab/identifier-type/">
      <ddm:dcmiMetadata>
        <dc:coverage xsi:type="dct:Period">name=The Great Depression; start=1929; end=1939;</dc:coverage>
      </ddm:dcmiMetadata>
    </ddm:DDM>
    ).solrLiterals.toMap
    ddmLiterals("dataset_coverage_temporal") shouldBe "name=The Great Depression; start=1929; end=1939;"
  }

  it should "create coverage_spatial from descriptions and names>" in {
    assume(canConnectToEasySchemas)
    val ddmLiterals = new DDM(<ddm:DDM
        xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/md/ddm/ https://easy.dans.knaw.nl/schemas/md/ddm/ddm.xsd"
        xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <ddm:dcmiMetadata>
        <dcx-gml:spatial srsName="http://www.opengis.net/def/crs/EPSG/0/28992">
          <Point xmlns="http://www.opengis.net/gml">
            <description>Entrance of DANS Building</description>
            <name>Data Archiving and Networked Services (DANS)</name>
            <pos>83575.4 455271.2 1.12</pos>
          </Point>
        </dcx-gml:spatial>
        <dcterms:spatial xsi:type="dcterms:Box">name=Western Australia; northlimit=-13.5; southlimit=-35.5; westlimit=112.5; eastlimit=129</dcterms:spatial>
      </ddm:dcmiMetadata>
    </ddm:DDM>
    ).solrLiterals
    ddmLiterals.toMap.keys shouldBe Set("dataset_coverage_spatial")
    ddmLiterals.map { case (_, v) => v } should contain theSameElementsAs Seq(
      "Entrance of DANS Building",
      "Data Archiving and Networked Services (DANS)",
      "name=Western Australia; northlimit=-13.5; southlimit=-35.5; westlimit=112.5; eastlimit=129"
    )
  }

  it should "create simple coverage" in {
    assume(canConnectToEasySchemas)
    val ddmLiterals = new DDM(<ddm:DDM
        xsi:schemaLocation="http://easy.dans.knaw.nl/schemas/md/ddm/ https://easy.dans.knaw.nl/schemas/md/ddm/ddm.xsd"
        xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <ddm:dcmiMetadata>
        <dc:coverage>Dekking</dc:coverage>
        <dcterms:coverage>meer dekking</dcterms:coverage>
      </ddm:dcmiMetadata>
    </ddm:DDM>
    ).solrLiterals
    ddmLiterals.toMap.keys shouldBe Set("dataset_coverage")
    ddmLiterals.map { case (_, v) => v } should contain theSameElementsAs Seq(
      "Dekking",
      "meer dekking"
    )
  }
}
