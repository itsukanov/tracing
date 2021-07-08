package com.itsukanov.exernal.prices

import cats.data.Kleisli
import cats.effect.IO
import com.itsukanov.common.restapi.{RestApiIOApp, RestApiServer, ServerConfig}
import com.itsukanov.exernal.prices.restapi.{ExternalPricesEndpoint, ExternalPricesRoutes}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.model.TraceProcess

object ExternalPricesApp extends RestApiIOApp {

  override def traceProcess = TraceProcess("external-prices-app")

  override def serverStart(implicit ep: EntryPoint[IO]) = RestApiServer.start(
    endpoints = ExternalPricesEndpoint.all,
    title = "External prices app",
    routes = new ExternalPricesRoutes[IO, Kleisli[IO, Span[IO], *]],
    config = ServerConfig("localhost", 8083) // todo move it to the config
  )

}
