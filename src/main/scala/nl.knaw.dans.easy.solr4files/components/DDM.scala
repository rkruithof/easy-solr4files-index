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

import java.net.URL

import nl.knaw.dans.easy.solr4files._
import nl.knaw.dans.easy.solr4files.components.DDM._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.util.Try
import scala.xml.{ Node, NodeSeq }

class DDM(xml: Node) extends DebugEnhancedLogging {

  private val profile: NodeSeq = xml \ "profile"
  private val dcmiMetadata: NodeSeq = xml \ "dcmiMetadata"

  val accessRights: String = (profile \ "accessRights").text

  // lazy postpones loading vocabularies until a file without accessibleTo=none is found
  // all the xpath handling might be expensive too
  lazy val solrLiterals: SolrLiterals = Seq.empty ++ // empty for code formatting
    (profile \ "title").map(simlpeText(_, "dataset_title")) ++
    (profile \ "creator").map(simlpeText(_, "dataset_creator")) ++
    (profile \ "creatorDetails").map(nestedText(_, "dataset_creator")) ++
    (dcmiMetadata \ "identifier").withFilter(_.hasType("id-type:DOI")).map(simlpeText(_, "dataset_doi")) ++
    (dcmiMetadata \ "identifier").withFilter(!_.hasType("id-type:DOI")).map(typedID(_, "dataset_identifier")) ++
    (profile \ "audience").flatMap(n => Seq(
      ("dataset_audience", n.text),
      ("dataset_subject", audienceMap.getOrElse(n.text, ""))
    )) ++
    (dcmiMetadata \ "subject").flatMap(maybeAbr(_, "dataset_subject")) ++
    (dcmiMetadata \ "temporal").flatMap(maybeAbr(_, "dataset_coverage_temporal")) ++
    (dcmiMetadata \ "coverage").withFilter(_.hasType("dct:Period")).map(simlpeText(_, "dataset_coverage_temporal")) ++
    (dcmiMetadata \ "coverage").withFilter(_.hasNoType).map(simlpeText(_, "dataset_coverage")) ++
    (dcmiMetadata \ "spatial" \\ "description").map(simlpeText(_, "dataset_coverage_spatial")) ++
    (dcmiMetadata \ "spatial" \\ "name").map(simlpeText(_, "dataset_coverage_spatial")) ++
    (dcmiMetadata \ "spatial").withFilter(_.hasType("dcterms:Box")).map(simlpeText(_, "dataset_coverage_spatial")) ++
    (dcmiMetadata \ "relation").withFilter(r => !r.isUrl && !r.isStreamingSurrogate).map(simlpeText(_, "dataset_relation")) ++
    qualifiedRelation("conformsTo") ++
    qualifiedRelation("isVersionOf") ++
    qualifiedRelation("hasVersion") ++
    qualifiedRelation("isReplacedBy") ++
    qualifiedRelation("replaces") ++
    qualifiedRelation("isRequiredBy") ++
    qualifiedRelation("requires") ++
    qualifiedRelation("isPartOf") ++
    qualifiedRelation("hasPart") ++
    qualifiedRelation("isReferencedBy") ++
    qualifiedRelation("references") ++
    qualifiedRelation("isFormatOf") ++
    qualifiedRelation("hasFormat")

  // TODO complex spatial https://github.com/DANS-KNAW/easy-schema/blob/acb6506/src/main/assembly/dist/docs/examples/ddm/example2.xml#L280-L320

  private def qualifiedRelation(qualifier: String): SolrLiterals = {
    (dcmiMetadata \ qualifier).withFilter(!_.isUrl).map(simlpeText(_, "dataset_relation"))
  }
}

object DDM {

  private val abrPrefix = "abr:ABR"

  private lazy val abrMaps = loadVocabularies("https://easy.dans.knaw.nl/schemas/vocab/2012/10/abr-type.xsd")
    .map { case (k, v) => // attributes in xsd are complex/periode
      (s"$abrPrefix$k", v) // we want to search with DDM attributes which are abr:ABRcomplex/abr:ABRperiode
    }

  private lazy val audienceMap = loadVocabularies(
    "https://easy.dans.knaw.nl/schemas/vocab/2015/narcis-type.xsd"
  )("Discipline")

  private def getAbrMap(n: Node): Option[VocabularyMap] = {
    n.attribute(xsiURI, "type")
      .map(_.text)
      .filter(_.startsWith(abrPrefix))
      .flatMap(abrMaps.get)
  }

  private def maybeAbr(n: Node, solrField: String): SolrLiterals = {
    getAbrMap(n) match {
      case None => Seq((solrField, n.text))
      case Some(map) if map.isEmpty => Seq((solrField, n.text))
      case Some(map) => Seq(
        (solrField + "_abr", n.text),
        (solrField, map.getOrElse(n.text, ""))
      )
    }
  }

  private def typedID(n: Node, solrField: String): (String, String) = {
    (solrField, n.attribute(xsiURI, "type").map(_.text).mkString.replace("id-type:", "") + " " + n.text)
  }

  private def simlpeText(n: Node, solrField: String): (String, String) = {
    (solrField, n.text)
  }


  private def nestedText(ns: Seq[Node], solrField: String): (String, String) = {
    val result: ListBuffer[String] = ListBuffer.empty

    @tailrec
    def internal(todo: Seq[Node] = ns): ListBuffer[String] = {
      todo match {
        case Seq() => result
        case Seq(h, t @ _*) if h.child.isEmpty =>
          result += h.text
          internal(t)
        case Seq(h, t @ _*) => internal(h.child ++ t)
      }
    }

    (solrField, internal().mkString(" "))
  }

  private def loadVocabularies(xsdURL: String): Map[String, VocabularyMap] = {
    for {
      url <- Try(new URL(xsdURL))
      xml <- url.loadXml
    } yield (xml \ "simpleType")
      .map(n => (mapName(n), findKeyValuePairs(n)))
      .toMap
  }.getOrElse(Map.empty)

  private def mapName(n: Node) = {
    n.attribute("name").map(_.text).getOrElse("")
  }

  private def findKeyValuePairs(table: Node): VocabularyMap = {
    (table \\ "enumeration")
      .map { node =>
        val key = node.attribute("value").map(_.text).getOrElse("")
        val value = (node \ "annotation" \ "documentation").text
        key -> value.replaceAll("\\s+", " ").trim
      }.toMap
  }
}
