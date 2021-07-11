package com.fproj.aoc

import zio.{Has, ZIO, ZLayer}
import scopt.OParser

object Params {

  case class Args(login: String = "",
                  password: String = "",
                  headless: Boolean = false,
                  amazonUrl: String = "http://amazon.com/",
                  years: Seq[String] = Seq("2021"))

  type ArgsT = Has[Args]

  def impl(args: List[String]): ZLayer[Any, Throwable, ArgsT] = ZLayer.fromEffect {
    ZIO.fromEither {
      val builder = OParser.builder[Args]
      val parser1 = {
        import builder._
        OParser.sequence(
          programName("amazon-order-crawler"),
          // option -f, --foo
          opt[String]('l', "login")
            .action((x, c) => c.copy(login = x))
            .text("login/username for amazon site")
            .required(),
          opt[String]('p', "password")
            .action((x, c) => c.copy(password = x))
            .text("password for amazon site")
            .required(),
          opt[Unit]('h', "headless")
            .action((x, c) => c.copy(headless = true))
            .text("headless mode"),
          opt[String]("url")
            .action((x, c) => c.copy(amazonUrl = x))
            .text("Amazon url, default http://amazon.com/. It could depend on a country")
          opt[Seq[String]]("years")
            .valueName("<year1>,<year2>...")
            .action((x, c) => c.copy(years = x))
            .text("Kind of filter for what years orders should be downloaded"),
        )
      }

      // OParser.parse returns Option[Config]
      OParser.parse(parser1, args, Args()) match {
        case Some(config) =>
          Right(config)
        case _ =>
          Left(new Exception("bad argument"))
        // arguments are bad, error message will have been displayed
      }
    }
  }
}
