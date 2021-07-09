package com.fproj.aoc

import java.net.InetSocketAddress
import java.util.{Calendar, GregorianCalendar}

import uzhttp.server.Server
import uzhttp.{RefineOps, Response}
import com.fproj.aoc.storage._
import zio._
import java.util.Date
import java.util.GregorianCalendar


object HttpServer {

  def aggregateMonthly(orders: List[Amazon.Order]) =  {
    orders
      .distinct
      .groupBy { order =>
        val date = new GregorianCalendar(order.date.getYear, order.date.getMonth, 1).getTime
        date
      }
      .map { case (date, orders) =>
        (date, orders.map(_.amount).sum, orders.size)
      }
      .toList
      .sortBy(_._1)
  }

  def aggregateYearly(orders: List[Amazon.Order]) =  {
    orders
      .distinct
      .groupBy { order =>
        val date = new GregorianCalendar(order.date.getYear, 0, 1).getTime
        date
      }
      .map { case (date, orders) =>
        (date, orders.map(_.amount).sum, orders.size)
      }
      .toList
      .sortBy(_._1)
  }

  def startHttpServer() = {
    Server.builder(new InetSocketAddress("127.0.0.1", 8085))
      .handleSome {
        case req if req.uri.getPath startsWith "/refresh" =>
          for {
            _ <-  AmazonCrawlerM.refresh().fork
          } yield Response.plain(s"refresh")
        case req if req.uri.getPath startsWith "/static" =>
          // deliver a static file from an application resource
          Response.fromResource(s"staticFiles${req.uri}", req).refineHTTP(req)
        case req if req.uri.getPath == "/" =>
          // deliver a constant HTML response
          for {
            stateRef <- stg()
            st <- stateRef.get
          } yield Response.plain(s"Size: ${st.size}\nOrders: ${st.mkString(",")}\nAggregated monthly: ${aggregateMonthly(st)}\nAggregated yearly: ${aggregateYearly(st)}")

      }.serve.useForever.orDie
  }

}
