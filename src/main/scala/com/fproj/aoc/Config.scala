package com.fproj.aoc

import zio.Has
import zio.config.typesafe._
import zio.config.magnolia.DeriveConfigDescriptor._

object Config {
  case class Credential(amazonUrl: String, years: List[String])

  type CredentialT = Has[Credential]

  def load(configPath: String) = {
    val automaticDescription = descriptor[Credential]
    TypesafeConfig.fromHoconFile(new java.io.File(configPath), automaticDescription)
  }
}
