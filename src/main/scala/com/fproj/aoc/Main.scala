package com.fproj.aoc

import com.fproj.aoc.HttpServer.{aggregateMonthly, aggregateYearly}
import zio._
import zio.blocking._
import zio.clock._
import zio.console._
import com.fproj.aoc.storage._

object Main extends App {

//  val storageDep: ZLayer[Any, Throwable, Storage] = Storage.impl
  val configDep = Config.load("config.cfg")
 // val amazonCrawlerDep = (ZLayer.identity[ZEnv] configDep ++ pageDep ++ Storage.impl ++ ZLayer.identity[ZEnv]) >+> AmazonCrawlerM.impl

  val app =
    for {
      _ <- AmazonCrawlerM.refresh()
      stateRef <- stg()
      st <- stateRef.get
      _ <- putStr(s"Aggregated by months:\n ${aggregateMonthly(st).mkString("\n ")}\nAggregated by years: \n ${aggregateYearly(st).mkString("\n ")} \n")

    //  _ <- HttpServer.startHttpServer()
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val argsDep = Params.impl(args)
    val pageDep = argsDep >+> PlaywrightM.impl >+> BrowserContextM.impl >>> PageM.impl
    app
      .provideLayer(ZLayer.identity[ZEnv] >+> Params.impl(args) >+> configDep >+> pageDep >+> Storage.impl >+> AmazonCrawlerM.impl)
      .exitCode
  }
}