package com.fproj.aoc

import java.nio.file.Paths
import java.util.Date

import com.fproj.aoc.PageM.PageT
import com.fproj.aoc.storage._
import com.microsoft.playwright.Page.{LoadState, WaitForLoadStateOptions}
import com.microsoft.playwright._
import zio.Has._
import zio._
import zio.clock._
import zio.console._
import zio.duration.Duration
import zio.blocking._

import scala.util.matching.Regex

object PlaywrightM {

  type PlaywrightT = Has[Playwright]

  val impl: ZLayer[Any, Throwable, PlaywrightT] = ZLayer.fromAcquireRelease(
    IO
      .effect {
        Playwright.create
      }
  )(playwright => IO.effectTotal{ println("Close Playwright"); playwright.close()})

  val playwright: ZIO[PlaywrightT, Throwable, Playwright] =
    ZIO.access(x => x.get)
}

object BrowserContextM {

  import PlaywrightM._

  type BrowserContextT = Has[BrowserContext]

  val impl: ZLayer[PlaywrightT, Throwable, BrowserContextT] = ZLayer.fromAcquireRelease {
    for {
      playwright <- playwright
      context <- UIO.effectTotal {
        val browser = playwright.chromium.launch(new BrowserType.LaunchOptions().withHeadless(false))
        browser.newContext(new Browser.NewContextOptions()
          .withViewport(1920, 1200)
        )
      }
    } yield context
  } { context => IO.effectTotal{ println("Close BrowserContext"); context.close()} }

  val browserContext: ZIO[BrowserContextT, Throwable, BrowserContext] =
    ZIO.access(x => x.get)
}

object PageM {

  import BrowserContextM._

  type PageT = Has[Page]

  val impl: ZLayer[BrowserContextT, Throwable, PageT] = ZLayer.fromAcquireRelease {
    for {
      context <- browserContext
      page <- ZIO.effect { context.newPage }
    } yield page
  } { page => IO.effectTotal{ println("Close Page"); page.close()} }

  val page: ZIO[PageT, Throwable, Page] =
    ZIO.access(x => x.get)
}

// [ZIO[Console with Clock with PageT with Has[Config.Credential], Throwable, Unit]]
object Amazon {
  import PageM._

  val orderPattern: Regex = "orderId=([0-9\\-]+)".r
  val amountPattern: Regex = "grand total:.*?([0-9,\\.]+)".r
  val datePattern: Regex = "ordered on ([A-Za-z0-9]+ [0-9]+, [0-9]+)".r

  case class Order(id: String, amount: Double, date: Date)

  val state = Ref.make[List[Order]](List())

  def parseOrders(content: String): List[String] = {
    orderPattern.findAllMatchIn(content).map(_.group(1)).toList.distinct
  }

  def parseOrder(orderId: String, content: String): Order = {
    val modifiedText = content.toLowerCase.replaceAll("\n","/")

    Order(
      id = orderId,
      amount = amountPattern.findAllMatchIn(modifiedText)
        .map(_.group(1))
        .toList
        .headOption
        .get
        .toDouble,

      date = datePattern.findAllMatchIn(modifiedText)
        .map(_.group(1))
        .toList
        .headOption
        .map { str =>
          val format = new java.text.SimpleDateFormat("MMMM dd, yyyy")
          format.parse(str)
        }
        .get
    )

  }

  def downloadOrderIds(url: String, filter: String, index: Int): ZIO[Clock with PageT with Blocking, Throwable, List[String]] =
    for {
      page <- page
      _ <- effectBlocking { page.navigate(s"${url}/gp/your-account/order-history?orderFilter=year-${filter}&startIndex=${index}") }
      _ <- effectBlocking { page.waitForLoadState(LoadState.LOAD, new WaitForLoadStateOptions().withTimeout(5.0)) }
      _ <- ZIO.sleep { Duration.fromMillis(200) }
      //      _ <- IO.effect { page.screenshot(new Page.ScreenshotOptions().withPath(Paths.get("example1.png"))) }
      html <- IO.effect {page.content() }
    } yield { parseOrders(html) }

  def downloadOrderAllIdsForFilter(url: String, filter: String, index: Int, orderIds: List[String]): ZIO[Clock with PageT with Blocking, Throwable, List[String]] =
    for {
      ids <- downloadOrderIds(url, filter, index)
      allIds <- {
        if (ids.nonEmpty)
          downloadOrderAllIdsForFilter(url, filter, index + ids.size, orderIds ++ ids)
        else
          ZIO.succeed(orderIds.distinct)
      }
    } yield allIds

  def downloadOrderAllIds(url: String, yearFilter: List[String]): ZIO[Clock with PageT with Blocking, Throwable, List[String]] =
    for {
      filters <- ZIO.succeed(yearFilter)
      setOfIds <- ZIO.foreach(filters) { filter =>
        downloadOrderAllIdsForFilter(url, filter, 1, List())
      }

    } yield setOfIds.flatten.distinct

  def downloadOrder(url: String, orderId: String) =
    for {
      page <- page
      _ <- effectBlocking { page.navigate(s"${url}/gp/your-account/order-details?orderID=${orderId}") }
      _ <- effectBlocking { page.waitForLoadState(LoadState.DOMCONTENTLOADED) }.catchAll(x => IO.succeed(println("Error", x)))
      _ <- ZIO.sleep { Duration.fromMillis(100) }
      //      _ <- IO.effect { page.screenshot(new Page.ScreenshotOptions().withPath(Paths.get("example1.png"))) }
      //      _ <- IO.effect { println("-----------------------------------"); println(page.content()); println("======================================="); }
      //      _ <- IO.effect { new PrintWriter(s"filename-${orderId}") { write(page.innerText("body")); close } }
      html <- IO.effect { page.innerText("body") }
    } yield parseOrder(orderId, html)

  def downloadAllOrders(url: String, orderIds: List[String]) =
    for {
      orders <- ZIO.foreach(orderIds) { id =>
        downloadOrder(url, id)
      }
    } yield orders

  def authorize(url: String) =
    for {
      page <- page
      _ <- ZIO.sleep { Duration.fromMillis(500) }
      _ <- IO.effect { page.navigate(s"${url}") }
      _ <- ZIO.sleep { Duration.fromMillis(500) }
      config <- ZIO.access[Has[Config.Credential]](x => x.get)
      args <- ZIO.access[Has[Params.Args]](x => x.get)
      _ <- IO.effect {
        page.click("#nav-orders")
        page.fill("#ap_email", args.login)
        page.click("#continue")
        page.fill("#ap_password", args.password)
        page.click("#signInSubmit")
      }
      _ <- ZIO.sleep(Duration.fromMillis(2000))
      _ <- IO.effect {
        page.screenshot(new Page.ScreenshotOptions().withPath(Paths.get("example.png")))
      }
    } yield ()

  def refresh() = {
    for {
      config <- ZIO.access[Has[Config.Credential]](x => x.get)
      _ <- authorize(config.amazonUrl)
      ids <- downloadOrderAllIds(config.amazonUrl, config.years)
      orders <- downloadAllOrders(config.amazonUrl, ids)
      stateRef <- stg()
      _ <- stateRef.set(orders)
//      _ <- putStr(s"${ids.mkString("\n")}, size ${ids.size}, ${orders}")
    } yield ()
  }
}

object AmazonCrawlerM {

  type AmazonServiceT = Has[AmazonService]

  trait AmazonService {
    def refresh(): ZIO[Any, Throwable, Unit]
  }

  val impl: ZLayer[Console with Blocking with Clock with Storage with PageT with Config.CredentialT with Params.ArgsT, Throwable, AmazonServiceT] = ZLayer.fromFunction {
    x => new AmazonService {
      override def refresh(): ZIO[Any, Throwable, Unit] = {
        Amazon.refresh().provide(x)
      }
    }
  }

  def refresh(): ZIO[AmazonServiceT, Throwable, ()] =
    ZIO.accessM(x => x.get.refresh())
}