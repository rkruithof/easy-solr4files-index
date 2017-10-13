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
package nl.knaw.dans.easy.solr4files

import java.util.UUID

import org.rogach.scallop.{ ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter }

class CommandLineOptions(args: Array[String], configuration: Configuration) extends ScallopConf(args) {
  type StoreName = String

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))
  printedName = "easy-update-solr4files-index"
  version(configuration.version)
  private val SUBCOMMAND_SEPARATOR = "---\n"
  val description: String = s"""Update the EASY SOLR for Files Index with file data from a bag-store"""
  val synopsis: String =
    s"""
       |  $printedName update [-s <bag-store>] <uuid>
       |  $printedName init <bag-store>
       |  $printedName run-service
       |  $printedName delete <solr-query>
       |
       |  Some examples of solr queries:
       |
       |    everything:            '*:*'
       |    all bags of one store: 'easy_dataset_store_id:pdbs'
       |    a bag:                 'easy_dataset_id:ef425828-e4ae-4d58-bf6a-c89cd46df61c'
       |    a folder in a bag:     'id:ef425828-e4ae-4d58-bf6a-c89cd46df61c/data/files/Documents/*'
       """.stripMargin

  version(s"$printedName v${ configuration.version }")
  banner(
    s"""
       |  $description
       |
       |Usage:
       |
       |$synopsis
       |
       |Options:
       |""".stripMargin)

  private val defaultBagStore = Some(configuration.properties.getString("default.bag-store", "MISSING_BAG_STORE"))

  private implicit def bagId: ValueConverter[UUID] = {
    singleArgConverter {
      case s => UUID.fromString(s)
    }
  }

  val update = new Subcommand("update") {
    descr("Update accessible files of a bag in the SOLR index")
    val bagStore: ScallopOption[StoreName] = opt[StoreName](
      "bag-store",
      default = defaultBagStore,
      short = 's',
      descr = "Name of the bag store")
    val bagUuid: ScallopOption[UUID] = trailArg[UUID](name = "bag-uuid", required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  val delete = new Subcommand("delete") {
    descr("Delete documents from the SOLR index")
    val query: ScallopOption[String] = trailArg[String](name = "solr-query", required = true)
    footer(SUBCOMMAND_SEPARATOR)
  }
  val init = new Subcommand("init") {
    descr("Rebuild the SOLR index from scratch for active bags in one or all store(s)")
    val bagStore: ScallopOption[StoreName] = trailArg(name = "bag-store", required = false)
    footer(SUBCOMMAND_SEPARATOR)
  }
  val runService = new Subcommand("run-service") {
    descr(
      "Starts EASY Update Solr4files Index as a daemon that services HTTP requests")
    footer(SUBCOMMAND_SEPARATOR)
  }
  addSubcommand(update)
  addSubcommand(delete)
  addSubcommand(init)
  addSubcommand(runService)

  footer("")
  verify()
}
