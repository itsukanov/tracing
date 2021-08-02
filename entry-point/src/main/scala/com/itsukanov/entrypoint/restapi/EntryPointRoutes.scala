package com.itsukanov.entrypoint.restapi

import cats.NonEmptyParallel
import cats.effect.{BracketThrow, Concurrent, ContextShift, Sync, Timer}
import cats.implicits._
import com.itsukanov.common.restapi._
import com.itsukanov.common.{CompanyFullInfo, CompanyShortInfo}
import com.itsukanov.entrypoint.Retries
import io.circe.generic.auto._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.{HttpRoutes, Request, _}
import org.typelevel.log4cats.Logger
import retry.Sleep
import sttp.tapir.server.http4s.Http4sServerOptions

class EntryPointRoutes[
     F[_]: Concurrent: ContextShift: Timer,
     G[_]: BracketThrow: Trace: Sync: NonEmptyParallel: Sleep: Logger
](ep: EntryPoint[F], client: Client[G])(
  implicit serverOptions: Http4sServerOptions[F, F],
  P: Provide[F, G, Span[F]],
  authToken: BearerToken)
    extends BaseRoutes[F, G]
    with Retries
    with Endpoint2Rout {

  implicit val iep: EntryPoint[F] = ep

  private val notFount = ApiError.CompanyNotFound.asLeft[CompanyFullInfo]

  private val authHeaders = Headers.of(
    Header("Authorization", s"Bearer ${authToken.token}")
  )

  private val getAll: HttpRoutes[F] = toRoutes1(EntryPointEndpoint.getAll) {
    case Paging(from, limit) =>
      val getAllRequest = Request[G](
        method = Method.GET,
        uri = uri"http://localhost:8081/api/v1.0/company"
          .withQueryParam("from", from)
          .withQueryParam("limit", limit),
        headers = authHeaders
      )

      logErrorWithTrace(client.expect[List[CompanyShortInfo]](getAllRequest).toEither)
  }

  private val getSingle: HttpRoutes[F] = toRoutes1(EntryPointEndpoint.getSingle) { ticker =>
    val pricesG = withRetry("getting.CompanyPrices")(
      client.expect[CompanyPrices](
        Request[G](
          method = Method.GET,
          uri = uri"http://localhost:8082/api/v1.0/prices" / ticker,
          headers = authHeaders
        )
      )
    ).handle404

    val commonInfoG = withRetry("getting.CompanyShortInfo")(
      client.expect[CompanyShortInfo](
        Request[G](
          method = Method.GET,
          uri = uri"http://localhost:8081/api/v1.0/company" / ticker,
          headers = authHeaders
        )
      )
    ).handle404

    logErrorWithTrace((pricesG, commonInfoG).parMapN { case (pricesOpt, infoOpt) =>
      (for {
        CompanyPrices(prices)          <- pricesOpt
        CompanyShortInfo(name, ticker) <- infoOpt
      } yield CompanyFullInfo(name, ticker, prices)) match {
        case Some(fullInfo) => fullInfo.asRight[ApiError]
        case None           => notFount
      }
    })
  }

  val routes = getAll <+> getSingle

}
