package com.itsukanov.entrypoint.restapi

import com.itsukanov.common.restapi._
import io.circe.generic.auto._
import sttp.model.{Headers, StatusCode}
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{Endpoint, _}

case class CompanyShortInfo(name: String, ticker: String)

case class CompanyFullInfo(name: String, ticker: String, prices: Seq[Double])

object EntryPointEndpoint extends BaseEndpoint with PagingParams {

  val getAll: Endpoint[(Headers, BearerToken, Paging), ApiError, List[CompanyShortInfo], Any] =
    baseEndpoint
      .get
      .in(basePath / "company")
      .in(pagingIn)
      .out(jsonBody[List[CompanyShortInfo]])

  val getSingle: Endpoint[(Headers, BearerToken, String), ApiError, CompanyFullInfo, Any] =
    baseEndpoint
      .get
      .in(basePath / "company" / path[String]("ticker"))
      .out(jsonBody[CompanyFullInfo])

  val clearCache: Endpoint[(Headers, BearerToken), ApiError, Unit, Any] =
    baseEndpoint
      .post
      .in(basePath / "clear-cache")
      .out(statusCode(StatusCode.NoContent))

  val all = List(getAll, getSingle, clearCache)

}