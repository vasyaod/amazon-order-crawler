package com.fproj.aoc

import zio._

package object storage {
  type Storage = Has[Ref[List[Amazon.Order]]]

  object Storage extends Serializable {
    val any: ZLayer[Storage, Nothing, Storage] = ZLayer.requires[Storage]
    val impl: Layer[Nothing, Storage] = ZLayer.fromEffect(Ref.make[List[Amazon.Order]](List()))
  }

  def stg(): ZIO[Storage, Nothing, Ref[List[Amazon.Order]]] =
    ZIO.access(x => x.get)
}


