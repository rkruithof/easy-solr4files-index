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

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import resource._

import scala.language.reflectiveCalls
import scala.util.control.NonFatal
import scala.util.{ Failure, Try }

object Command extends App with DebugEnhancedLogging {

  val configuration = Configuration()
  val commandLine: CommandLineOptions = new CommandLineOptions(args, configuration)
  val app = new EasyUpdateSolr4filesIndexApp(new ApplicationWiring(configuration))

  managed(app)
    .acquireAndGet(app => {
      for {
        _ <- app.init()
        msg <- runCommand(app)
      } yield msg
    })
    .doIfSuccess(msg => println(s"OK: $msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${ e.getMessage }") }

  private def runCommand(app: EasyUpdateSolr4filesIndexApp): Try[FeedBackMessage] = {
    commandLine.subcommand
      .collect {
        case update @ commandLine.update => app.update(update.bagStore(), update.bagUuid())
        case delete @ commandLine.delete => app.delete(delete.query())
        case init @ commandLine.init => init.bagStore.toOption
          .map(app.initSingleStore)
          .getOrElse(app.initAllStores())
        case commandLine.runService => Failure(new NotImplementedError())
      }
      .getOrElse(Failure(new IllegalArgumentException(s"Unknown command: ${ commandLine.subcommand }")))
  }
}
