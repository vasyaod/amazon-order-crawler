package com.fproj.aoc

import zio.{Has, ZIO, ZLayer}
import scopt.OParser

object Params {

  case class Args(login: String = "", password: String = "", headless: Boolean = false)

  type ArgsT = Has[Args]

  def impl(args: List[String]): ZLayer[Any, Throwable, ArgsT] = ZLayer.fromEffect {
    ZIO.fromEither {
      val builder = OParser.builder[Args]
      val parser1 = {
        import builder._
        OParser.sequence(
          programName("amazon-report"),
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
            .text("headless mode")
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
