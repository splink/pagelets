package org.splink.pagelets

import play.api.{Configuration, Environment}
import play.api.inject.Module

class PageletModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Pagelets].to[PageletsAssembly]
  )
}